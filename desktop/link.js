'use strict';
// Phone <-> laptop link. End-to-end encrypted (AES-256-GCM) with a secret made on the laptop and typed/pasted ONCE into the phone app.
// The relay (default ntfy.sh, free, no account) only ever sees random topic names + ciphertext. API keys never travel: the laptop sends an 'ask', the PHONE answers with its own keys.
const crypto = require('crypto');
const MAX_SKEW_MS = 120000, MAX_TEXT = 1500;
const b64u = b => Buffer.from(b).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const unb64u = s => Buffer.from(String(s).replace(/-/g, '+').replace(/_/g, '/'), 'base64');

function genKey() { return b64u(crypto.randomBytes(32)); }
function topics(key) {
  const h = d => 'jarvis-' + crypto.createHash('sha256').update(d + ':' + key).digest('hex').slice(0, 40);
  return { l2p: h('l2p'), p2l: h('p2l') };   // laptop->phone, phone->laptop
}
function seal(key, topic, msg) {
  const k = unb64u(key); if (k.length !== 32) throw new Error('bad key');
  const iv = crypto.randomBytes(12), c = crypto.createCipheriv('aes-256-gcm', k, iv);
  c.setAAD(Buffer.from(topic));
  const body = JSON.stringify({ id: msg.id || b64u(crypto.randomBytes(9)), ts: msg.ts || Date.now(), type: msg.type, text: String(msg.text || '').slice(0, MAX_TEXT), re: msg.re || '' });
  const ct = Buffer.concat([c.update(body, 'utf8'), c.final()]);
  return b64u(Buffer.concat([iv, ct, c.getAuthTag()]));
}
// returns the message object, or null if it is not for us / tampered / stale.
function open(key, topic, env, now) {
  try {
    const k = unb64u(key), raw = unb64u(env); if (k.length !== 32 || raw.length < 29) return null;
    const iv = raw.subarray(0, 12), tag = raw.subarray(raw.length - 16), ct = raw.subarray(12, raw.length - 16);
    const d = crypto.createDecipheriv('aes-256-gcm', k, iv); d.setAAD(Buffer.from(topic)); d.setAuthTag(tag);
    const m = JSON.parse(Buffer.concat([d.update(ct), d.final()]).toString('utf8'));
    if (!m || typeof m.id !== 'string' || typeof m.type !== 'string') return null;
    if (Math.abs((now || Date.now()) - m.ts) > MAX_SKEW_MS) return null;
    return m;
  } catch (e) { return null; }
}

// transport = { publish(topic, text): Promise, subscribe(topic, onText): () => stop }
function ntfyTransport(base) {
  base = (base || 'https://ntfy.sh').replace(/\/+$/, '');
  return {
    publish: async (topic, text) => { const r = await fetch(base + '/' + topic, { method: 'POST', body: text }); if (!r.ok) throw new Error('relay ' + r.status); },
    subscribe(topic, onText) {
      let stop = false, ctl = null;
      (async function loop() {
        while (!stop) {
          try {
            ctl = new AbortController();
            const r = await fetch(base + '/' + topic + '/json?since=' + Math.floor(Date.now() / 1000 - 90), { signal: ctl.signal });
            const rd = r.body.getReader(), dec = new TextDecoder(); let buf = '';
            while (!stop) {
              const { done, value } = await rd.read(); if (done) break;
              buf += dec.decode(value, { stream: true }); let i;
              while ((i = buf.indexOf('\n')) >= 0) { const ln = buf.slice(0, i); buf = buf.slice(i + 1); try { const j = JSON.parse(ln); if (j.event === 'message' && j.message) onText(j.message); } catch (e) {} }
            }
          } catch (e) {}
          if (!stop) await new Promise(r => setTimeout(r, 4000));
        }
      })();
      return () => { stop = true; try { ctl && ctl.abort(); } catch (e) {} };
    }
  };
}

class Link {
  constructor(key, transport, onMessage) {
    this.key = key; this.t = transport; this.tp = topics(key); this.seen = new Map(); this.onMessage = onMessage; this.stop = null;
  }
  start() { this.stop = this.t.subscribe(this.tp.p2l, env => this._in(env)); }
  close() { if (this.stop) this.stop(); this.stop = null; }
  _in(env) {
    const m = open(this.key, this.tp.p2l, env); if (!m) return;
    const now = Date.now(); for (const [id, ts] of this.seen) if (now - ts > 300000) this.seen.delete(id);
    if (this.seen.has(m.id)) return; this.seen.set(m.id, now);
    try { this.onMessage(m); } catch (e) {}
  }
  send(type, text, re) { const id = b64u(crypto.randomBytes(9)); return this.t.publish(this.tp.l2p, seal(this.key, this.tp.l2p, { id, type, text, re })).then(() => id); }
}
module.exports = { genKey, topics, seal, open, ntfyTransport, Link, MAX_SKEW_MS };
