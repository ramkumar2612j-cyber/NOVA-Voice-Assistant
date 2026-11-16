const test = require('node:test'), assert = require('node:assert');
const V = require('../voice');
const KEY = 'gsk_' + 'a'.repeat(30), AUDIO = Buffer.alloc(4000, 1);
const okFetch = (j, status = 200, spy) => async (url, o) => { if (spy) spy.push({ url, o }); return { ok: status < 300, status, json: async () => j }; };
test('validKey', () => { assert.ok(V.validKey(KEY)); for (const k of ['', 'short', 'has space 12345678901234567890', null, 'x'.repeat(300)]) assert.ok(!V.validKey(k), String(k)); });
test('transcribe ok: url, bearer header, multipart has model + file bytes', async () => {
  const spy = [], r = await V.transcribe(okFetch({ text: ' chrome kholo ', segments: [{ no_speech_prob: 0.1 }] }, 200, spy), KEY, AUDIO, 'audio/webm;codecs=opus');
  assert.deepStrictEqual(r, { ok: true, text: 'chrome kholo' });
  assert.strictEqual(spy[0].url, V.ENDPOINT); assert.strictEqual(spy[0].o.headers.Authorization, 'Bearer ' + KEY);
  const body = spy[0].o.body.toString('latin1'); assert.ok(body.includes('name="model"') && body.includes(V.MODEL) && body.includes('filename="cmd.webm"'));
  assert.ok(spy[0].o.body.includes(AUDIO)); assert.ok(spy[0].o.headers['Content-Type'].startsWith('multipart/form-data; boundary='));
});
test('errors are explained, key never echoed', async () => {
  for (const [st, re] of [[401, /key galat/], [429, /limit/], [500, /error 500/]]) { const r = await V.transcribe(okFetch({}, st), KEY, AUDIO, 'audio/webm'); assert.ok(!r.ok); assert.match(r.err, re); assert.ok(!r.err.includes(KEY)); }
  const net = await V.transcribe(async () => { throw new Error('ENOTFOUND ' + KEY); }, KEY, AUDIO, 'audio/webm'); assert.match(net.err, /internet/); assert.ok(!net.err.includes(KEY));
});
test('no key / tiny / huge audio refused before any network', async () => {
  let called = 0; const f = async () => { called++; return {}; };
  assert.match((await V.transcribe(f, '', AUDIO, 'x')).err, /key nahi/); assert.match((await V.transcribe(f, KEY, Buffer.alloc(10), 'x')).err, /chhoti/); assert.match((await V.transcribe(f, KEY, Buffer.alloc(9 * 1024 * 1024), 'x')).err, /lambi/); assert.strictEqual(called, 0);
});
test('noise guards: no_speech, prompt echo, classic hallucinations', async () => {
  assert.strictEqual(V.cleanText('chrome kholo', [{ no_speech_prob: 0.95 }, { no_speech_prob: 0.9 }]), '');
  assert.strictEqual(V.cleanText('chrome kholo', [{ no_speech_prob: 0.95 }, { no_speech_prob: 0.2 }]), 'chrome kholo');
  assert.strictEqual(V.cleanText(V.PROMPT, []), ''); assert.strictEqual(V.cleanText('Hinglish voice command for a laptop assistant', []), '');
  assert.strictEqual(V.cleanText('Thank you.', []), '');
  assert.strictEqual(V.cleanText('jarvis', []), 'jarvis');
  const r = await V.transcribe(okFetch({ text: 'You', segments: [] }), KEY, AUDIO, 'audio/webm'); assert.match(r.err, /Kuch sunai/);
});
test('guard: link code and main.js never touch the voice key', () => {
  const fs = require('fs'), rd = f => fs.readFileSync(__dirname + '/../' + f, 'utf8');
  assert.ok(!/voice|stt/i.test(rd('link.js')), 'link.js must not know about voice');
  assert.ok(!/gsk_|sttKey|GROQ|api[_-]?key/i.test(rd('main.js')), 'main.js must not hold keys');
  assert.ok(!/require\('\.\/link'\)/.test(rd('voice-main.js')), 'voice-main.js must not use the phone link');
});
