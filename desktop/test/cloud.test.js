'use strict';
// v7.217 cloud.js tests. LWW fixture vectors EXACTLY wahi jo JarvisCloudRulesTest.kt use karta hai —
// dono taraf same winner nikalna zaroori hai (phone <-> laptop conflict resolution).
const test = require('node:test');
const assert = require('node:assert');
const C = require('../cloud.js');

function code(addr, tok) {
  return 'JARVIS-CLOUD:' + Buffer.from(JSON.stringify({ a: addr, p: tok })).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

test('parseCode accepts https with valid token', () => {
  const p = C.parseCode(code('https://brain.example.com', 'ABCdef123_-456789012345678'));
  assert.ok(p);
  assert.strictEqual(p[0], 'https://brain.example.com');
  assert.strictEqual(p[1], 'ABCdef123_-456789012345678');
});

test('parseCode rejects http, garbage, wrong prefix, bad host', () => {
  assert.strictEqual(C.parseCode(code('http://brain.example.com', 'ABCdef123_-456789012345678')), null);
  assert.strictEqual(C.parseCode(null), null);
  assert.strictEqual(C.parseCode('JARVIS-CLOUD:!!!'), null);
  assert.strictEqual(C.parseCode('JARVIS-LINK:abcdef'), null);
  assert.strictEqual(C.parseCode(code('', 'ABCdef123_-456789012345678')), null);
});

test('parseCode accepts localhost http (local testing)', () => {
  assert.ok(C.parseCode(code('http://127.0.0.1:8787', 'ABCdef123_-456789012345678')));
});

test('cleanBase basics match the phone rules', () => {
  assert.strictEqual(C.cleanBase('https://b.example.com/'), 'https://b.example.com');
  assert.strictEqual(C.cleanBase('ftp://b.example.com'), '');
  assert.strictEqual(C.cleanBase('https://user:pass@b.example.com'), '');
  assert.strictEqual(C.cleanBase('http://127.0.0.1:8787'), 'http://127.0.0.1:8787');
});

// ---- LWW fixtures (Kotlin mirror) ----
test('lww: higher version wins', () => {
  assert.strictEqual(C.lwwWinner('mine', 2, 1000, 'd1', 'theirs', 1, 2000, 'd2'), 'mine');
});
test('lww: same version newer ts wins', () => {
  assert.strictEqual(C.lwwWinner('mine', 1, 1000, 'd1', 'theirs', 1, 2000, 'd2'), 'theirs');
});
test('lww: tie breaks by device id', () => {
  assert.strictEqual(C.lwwWinner('mine', 1, 1000, 'd1', 'theirs', 1, 1000, 'd2'), 'theirs');
  assert.strictEqual(C.lwwWinner('mine', 1, 1000, 'd2', 'theirs', 1, 1000, 'd1'), 'mine');
});
test('lww: numeric compare, not string (10 vs 9)', () => {
  assert.strictEqual(C.lwwWinner('mine', 10, 1, 'd1', 'theirs', 9, 999, 'd2'), 'mine');
  assert.strictEqual(C.lwwWinner('mine', 9, 999, 'd1', 'theirs', 10, 1, 'd2'), 'theirs');
});
test('lww: lower version loses even with newer ts', () => {
  assert.strictEqual(C.lwwWinner('mine', 1, 999999, 'd9', 'theirs', 2, 1, 'd1'), 'theirs');
});

test('retryDelayMs backoff schedule', () => {
  assert.strictEqual(C.retryDelayMs(0), 0);
  assert.strictEqual(C.retryDelayMs(1), 60000);
  assert.strictEqual(C.retryDelayMs(2), 300000);
  assert.strictEqual(C.retryDelayMs(3), 900000);
  assert.strictEqual(C.retryDelayMs(99), 900000);
});

// ---- E2E seal round trip (phone ke family key se seal, laptop khol sake) ----
test('sealText/openText round trip + tamper reject', () => {
  const fam = require('crypto').randomBytes(32).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const env = C.sealText(fam, 'task:t1', 'laptop pe chrome kholo');
  assert.notStrictEqual(env, 'laptop pe chrome kholo');
  assert.strictEqual(C.openText(fam, 'task:t1', env), 'laptop pe chrome kholo');
  assert.strictEqual(C.openText(fam, 'task:OTHER', env), env);   // galat topic -> envelope wapas, content leak nahi
  const bad = env.slice(0, -4) + (env.slice(-4) === 'AAAA' ? 'BBBB' : 'AAAA');
  assert.strictEqual(C.openText(fam, 'task:t1', bad), bad);      // tampered -> envelope wapas
  const otherFam = require('crypto').randomBytes(32).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  assert.strictEqual(C.openText(otherFam, 'task:t1', env), env); // galat key -> envelope wapas
});

// ---- Cloud store round trip (no API key ever stored) ----
test('Cloud stores only addr/token/family in its own store', () => {
  let saved = null;
  const store = {
    read: () => ({}),
    write: (s) => { saved = s; },
    clear: () => { saved = null; }
  };
  const cl = new C.Cloud(store);
  assert.strictEqual(cl.linked, false);
  cl.save('https://b.example.com', 'tok-1234567890abcdef', 'famkey');
  assert.strictEqual(cl.linked, true);
  assert.deepStrictEqual(saved, { addr: 'https://b.example.com', token: 'tok-1234567890abcdef', family: 'famkey' });
  cl.clear();
  assert.strictEqual(cl.linked, false);
  assert.strictEqual(saved, null);
});

// ---- main.js/desktop code me API key ka zikr nahi (purana invariant) ----
test('cloud.js never mentions provider API keys', async () => {
  const fs = require('node:fs');
  const src = fs.readFileSync(__dirname + '/../cloud.js', 'utf-8') + fs.readFileSync(__dirname + '/../main.js', 'utf-8');
  assert.ok(!/GEMINI|GROQ|api[_-]?key/i.test(src.replace(/API keys kabhi cloud par nahi jaate/g, '')));
});
