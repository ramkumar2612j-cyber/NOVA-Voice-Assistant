'use strict';
// v7.217 CENTRAL CLOUD BRAIN, desktop side (Part 1). Phone (JarvisCloud.kt) aur backend (backend/brain_server.py)
// ke saath ek hi contract. LWW + pairing parse + E2E seal EXACTLY phone jaise (tools/test_cloud_brain_contract.py
// fixture vectors se teeno taraf same result aana check hota hai). API keys kabhi cloud par nahi jaate.
const crypto = require('crypto');
const MAX_BODY = 8192, CONNECT_MS = 6000, READ_MS = 10000;

function b64u(b) { return Buffer.from(b).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''); }
function unb64u(s) { return Buffer.from(String(s).replace(/-/g, '+').replace(/_/g, '/'), 'base64'); }

// ---------- pure rules (mirror of JarvisCloud.Rules) ----------

function cleanBase(raw) {
  let s = String(raw || '').trim();
  if (!s || s.length > 300 || /\s/.test(s)) return '';
  const lower = s.toLowerCase();
  let scheme;
  if (lower.startsWith('https://')) scheme = 'https'; else if (lower.startsWith('http://')) scheme = 'http'; else return '';
  let rest = s.slice(scheme.length + 3);
  if (/[?#@\\]/.test(rest)) return '';
  while (rest.endsWith('/')) rest = rest.slice(0, -1);
  if (!rest) return '';
  const host = rest.split('/')[0].split(':')[0].toLowerCase();
  if (!host || !/^[a-z0-9.-]+$/.test(host)) return '';
  const port = (rest.split('/')[0].split(':')[1] || '');
  if (port && (port.length > 5 || !/^\d+$/.test(port))) return '';
  if (scheme === 'http' && host !== '127.0.0.1' && host !== 'localhost') return '';
  if (scheme === 'https' && !host.includes('.')) return '';
  return scheme + '://' + rest;
}

function parseCode(raw) {
  const t = String(raw || '').trim();
  if (t.length > 400) return null;
  const i = t.indexOf(':');
  if (i < 0 || t.slice(0, i).trim().toLowerCase() !== 'jarvis-cloud') return null;
  let j;
  try { j = JSON.parse(unb64u(t.slice(i + 1).trim()).toString('utf-8')); } catch (e) { return null; }
  const addr = cleanBase(j.a);
  const tok = String(j.p || '');
  if (!addr || !/^[A-Za-z0-9_=-]{20,300}$/.test(tok)) return null;
  return [addr, tok];
}

// LWW: (ver, ts, deviceId) tuple compare — Kotlin/Python wala hi order (ver/ts NUMERIC compare, device string compare)
function lwwWinner(mine, myVer, myTs, myDevice, theirs, theirVer, theirTs, theirDevice) {
  if (myVer !== theirVer) return myVer > theirVer ? mine : theirs;
  if (myTs !== theirTs) return myTs > theirTs ? mine : theirs;
  return myDevice > theirDevice ? mine : theirs;   // string tie-break; poora tie -> theirs (server ka value already pada hai)
}

function retryDelayMs(attempts) {
  if (attempts <= 0) return 0;
  if (attempts === 1) return 60000;
  if (attempts === 2) return 5 * 60000;
  return 15 * 60000;
}

// ---------- E2E payload seal (LaptopLink wala scheme, family key; phone ke saath interop) ----------

function sealText(familyKey, topic, text) {
  try {
    const k = unb64u(familyKey); if (k.length !== 32) return String(text);
    const iv = crypto.randomBytes(12);
    const c = crypto.createCipheriv('aes-256-gcm', k, iv);
    c.setAAD(Buffer.from(topic));
    const body = JSON.stringify({ t: String(text).slice(0, 800), ts: Date.now() });
    const ct = Buffer.concat([c.update(body, 'utf-8'), c.final()]);
    return b64u(Buffer.concat([iv, ct, c.getAuthTag()]));
  } catch (e) { return String(text); }
}

function openText(familyKey, topic, env) {
  try {
    const k = unb64u(familyKey); if (k.length !== 32) return env;
    const raw = unb64u(env); if (raw.length < 29) return env;
    const d = crypto.createDecipheriv('aes-256-gcm', k, raw.subarray(0, 12));
    d.setAAD(Buffer.from(topic)); d.setAuthTag(raw.subarray(raw.length - 16));
    const m = JSON.parse(Buffer.concat([d.update(raw.subarray(12, raw.length - 16)), d.final()]).toString('utf-8'));
    return (m && m.t) ? m.t : env;
  } catch (e) { return env; }
}

// ---------- HTTP ----------

async function post(url, body, token) {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), CONNECT_MS + READ_MS);
  try {
    const r = await fetch(url, {
      method: 'POST', signal: ctl.signal,
      headers: Object.assign({ 'Content-Type': 'application/json' }, token ? { Authorization: 'Bearer ' + token } : {}),
      body: String(body).slice(0, MAX_BODY)
    });
    let j = {};
    try { j = await r.json(); } catch (e) { }
    return [r.status, j];
  } catch (e) { return [0, {}]; }
  finally { clearTimeout(t); }
}

async function get(url) {
  try {
    const r = await fetch(url);
    let j = {};
    try { j = await r.json(); } catch (e) { }
    return [r.status, j];
  } catch (e) { return [0, {}]; }
}

// ---------- client ----------

class Cloud {
  constructor(store) {
    // store = { read(): {addr,token,family}, write(state), clear() }  (userData/cloud.json — koi API key nahi)
    this.store = store;
    const s = store.read();
    this.addr = s.addr || ''; this.token = s.token || ''; this.family = s.family || '';
  }
  get linked() { return Boolean(this.addr && this.token); }
  save(addr, token, family) {
    this.addr = addr; this.token = token; this.family = family || '';
    this.store.write({ addr: this.addr, token: this.token, family: this.family });
  }
  clear() { this.addr = ''; this.token = ''; this.family = ''; this.store.clear(); }
  async register(code, name, platform) {
    const p = parseCode(code); if (!p) return 'invalid';
    const body = JSON.stringify({ code, name: String(name || 'laptop').slice(0, 40), platform: platform || 'windows' });
    const [c, j] = await post(p[0] + '/api/register', body, null);
    if (c !== 200 || !j.device_token) return 'fail';
    this.save(p[0], j.device_token, j.family_key || '');
    return 'ok';
  }
  async status() { return this.linked ? post(this.addr + '/api/status', JSON.stringify({ run: true, net: 'WI-FI' }), this.token) : [0, {}]; }
  async taskPush(id, title, target) {
    if (!this.linked) return [0, {}];
    const sealed = this.family ? sealText(this.family, 'task:' + id, title) : title;
    return post(this.addr + '/api/tasks/push', JSON.stringify({ id: String(id).slice(0, 64), title: String(sealed).slice(0, 800), target: String(target || '').slice(0, 20), ver: 1, ts: Math.floor(Date.now() / 1000) }), this.token);
  }
  async taskPull(since) {
    if (!this.linked) return [];
    const [c, j] = await post(this.addr + '/api/tasks/pull', JSON.stringify({ since: since || 0 }), this.token);
    if (c !== 200 || !Array.isArray(j.tasks)) return [];
    return j.tasks.map(t => ({ id: t.id, title: this.family ? openText(this.family, 'task:' + t.id, t.title) : t.title, target: t.target, state: t.state, claimed_by: t.claimed_by }));
  }
  async taskClaim(id) { return this.linked ? post(this.addr + '/api/tasks/claim', JSON.stringify({ id }), this.token) : [0, {}]; }
  async taskDone(id, result) {
    if (!this.linked) return [0, {}];
    const sealed = this.family ? sealText(this.family, 'task:' + id + ':r', result) : result;
    return post(this.addr + '/api/tasks/done', JSON.stringify({ id, result: String(sealed).slice(0, 800) }), this.token);
  }
  async memoryPush(id, text, scope) {
    if (!this.linked) return [0, {}];
    const sealed = this.family ? sealText(this.family, 'mem:' + id, text) : text;
    return post(this.addr + '/api/memory/push', JSON.stringify({ id: String(id).slice(0, 64), text: String(sealed).slice(0, 800), scope: scope === 'private' ? 'private' : 'all', ver: 1, ts: Math.floor(Date.now() / 1000) }), this.token);
  }
  async memoryPull(since) {
    if (!this.linked) return [];
    const [c, j] = await post(this.addr + '/api/memory/pull', JSON.stringify({ since: since || 0 }), this.token);
    if (c !== 200 || !Array.isArray(j.facts)) return [];
    return j.facts.map(f => ({ id: f.id, text: this.family ? openText(this.family, 'mem:' + f.id, f.text) : f.text, scope: f.scope }));
  }
  async sync(items) {
    if (!this.linked) return [0, {}];
    return post(this.addr + '/api/sync', JSON.stringify({ items: items.slice(0, 50).map(i => ({ key: String(i.key).slice(0, 64), val: String(i.val).slice(0, 4000), ver: 1, ts: Math.floor(Date.now() / 1000) })) }), this.token);
  }
}

async function publicStatus(addr) {
  const a = cleanBase(addr); if (!a) return null;
  const [c, j] = await get(a + '/api/status');
  return (c === 200 && j.ok) ? j : null;
}

module.exports = { cleanBase, parseCode, lwwWinner, retryDelayMs, sealText, openText, Cloud, publicStatus, MAX_BODY };
