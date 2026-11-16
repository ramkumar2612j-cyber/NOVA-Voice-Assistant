const test = require('node:test'), assert = require('node:assert');
const L = require('../link');
const key = L.genKey(), tp = L.topics(key);
test('topics are unguessable and different', () => { assert.notStrictEqual(tp.l2p, tp.p2l); assert.match(tp.l2p, /^jarvis-[0-9a-f]{40}$/); assert.notStrictEqual(L.topics(L.genKey()).l2p, tp.l2p); });
test('roundtrip', () => { const m = L.open(key, tp.p2l, L.seal(key, tp.p2l, { type: 'cmd', text: 'chrome kholo' })); assert.strictEqual(m.text, 'chrome kholo'); assert.strictEqual(m.type, 'cmd'); });
test('wrong key rejected', () => assert.strictEqual(L.open(L.genKey(), tp.p2l, L.seal(key, tp.p2l, { type: 'cmd', text: 'x' })), null));
test('wrong topic rejected (AAD)', () => assert.strictEqual(L.open(key, tp.l2p, L.seal(key, tp.p2l, { type: 'cmd', text: 'x' })), null));
test('tampered rejected', () => { const e = L.seal(key, tp.p2l, { type: 'cmd', text: 'x' }); const b = Buffer.from(e.replace(/-/g, '+').replace(/_/g, '/'), 'base64'); b[15] ^= 1; assert.strictEqual(L.open(key, tp.p2l, b.toString('base64url')), null); });
test('stale rejected', () => assert.strictEqual(L.open(key, tp.p2l, L.seal(key, tp.p2l, { type: 'cmd', text: 'x', ts: Date.now() - 600000 })), null));
test('garbage rejected', () => { assert.strictEqual(L.open(key, tp.p2l, 'abc'), null); assert.strictEqual(L.open('short', tp.p2l, 'abc'), null); });
test('replay ignored + end to end over fake relay', async () => {
  const subs = {}; const T = { publish: async (t, x) => { (subs[t] || []).forEach(f => f(x)); }, subscribe: (t, f) => { (subs[t] = subs[t] || []).push(f); return () => {}; } };
  const got = []; const lk = new L.Link(key, T, m => got.push(m)); lk.start();
  const env = L.seal(key, tp.p2l, { type: 'ask', text: 'kaun hai PM' }); await T.publish(tp.p2l, env); await T.publish(tp.p2l, env);
  assert.strictEqual(got.length, 1); assert.strictEqual(got[0].text, 'kaun hai PM');
  let seen = null; T.subscribe(tp.l2p, x => { seen = L.open(key, tp.l2p, x); }); await lk.send('cmd', 'call mummy'); assert.strictEqual(seen.text, 'call mummy');
});
test('no api key ever in a message type list', () => { const s = require('fs').readFileSync(__dirname + '/../main.js', 'utf8'); assert.ok(!/api[_-]?key|GEMINI|GROQ/i.test(s), 'main.js must not hold keys'); });
