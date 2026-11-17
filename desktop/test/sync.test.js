'use strict';
// v7.218 sync tests — fixtures EXACTLY wahi jo SyncRulesTest.kt me hain (phone <-> laptop same winner).
// merge logic desktop/cloud.js ke lwwWinner par bana hai; SyncRules ka mirror yahan chhota interpreter hai.
const test = require('node:test');
const assert = require('node:assert');
const crypto = require('crypto');
const C = require('../cloud.js');

// TaskItem/FactItem merge — Kotlin SyncRules.mergeTasks/mergeFacts ka JS mirror (values se, bhiurdari nahi)
function mergeTasks(mine, theirs) {
  const MAX_TASKS = 100;
  const byId = new Map();
  for (const t of theirs) byId.set(t.id, t);
  for (const t of mine) {
    const old = byId.get(t.id);
    byId.set(t.id, old ? C.lwwWinner(t, t.ver, t.ts, t.device, old, old.ver, old.ts, old.device) : t);
  }
  return [...byId.values()].sort((a, b) => a.ts - b.ts).slice(-MAX_TASKS);   // NAYE ts wale bache (Kotlin: sortedByDescending.take.sortedBy)
}

function mergeFacts(mine, theirs) {
  const MAX_FACTS = 200;
  const byId = new Map();
  for (const f of theirs) if (f.scope !== 'private') byId.set(f.id, f);
  for (const f of mine) {
    const old = byId.get(f.id);
    byId.set(f.id, old ? C.lwwWinner(f, f.ver, f.ts, f.device, old, old.ver, old.ts, old.device) : f);
  }
  return [...byId.values()].sort((a, b) => a.ts - b.ts).slice(-MAX_FACTS);   // NAYE ts wale bache (Kotlin jaisa)
}

const t = (id, title, o = {}) => Object.assign({ id, title, target: '', state: 'open', claimedBy: '', ver: 1, ts: 0, device: '' }, o);
const f = (id, text, o = {}) => Object.assign({ id, text, scope: 'all', ver: 1, ts: 0, device: '' }, o);

test('task merge unions by id (Kotlin fixture)', () => {
  const m = mergeTasks([t('a', 'mera kaam'), t('b', 'b mine', { ver: 2 })], [t('b', 'b theirs'), t('c', 'un kaam')]);
  assert.deepStrictEqual(m.map(x => x.id).sort(), ['a', 'b', 'c']);
  assert.strictEqual(m.find(x => x.id === 'b').title, 'b mine');
});

test('task merge LWW same ver newer ts (Kotlin fixture)', () => {
  const m = mergeTasks([t('x', 'purana', { ts: 100 })], [t('x', 'naya', { ts: 200 })]);
  assert.strictEqual(m[0].title, 'naya');
});

test('task merge tie breaks by device (Kotlin fixture)', () => {
  assert.strictEqual(mergeTasks([t('x', 'mine', { ts: 100, device: 'd1' })], [t('x', 'theirs', { ts: 100, device: 'd2' })])[0].title, 'theirs');
  assert.strictEqual(mergeTasks([t('x', 'mine', { ts: 100, device: 'd2' })], [t('x', 'theirs', { ts: 100, device: 'd1' })])[0].title, 'mine');
});

test('task merge sorts by ts and caps at 100 (Kotlin fixture)', () => {
  const many = [];
  for (let i = 1; i <= 150; i++) many.push(t('t' + i, 'kaam ' + i, { ts: i }));
  const m = mergeTasks(many, []);
  assert.strictEqual(m.length, 100);
  assert.strictEqual(m[m.length - 1].title, 'kaam 150');
});

test('private facts never cross devices (Kotlin fixture)', () => {
  const m = mergeFacts([f('p1', 'mera private', { scope: 'private' })], [f('p2', 'unka private', { scope: 'private' }), f('s1', 'shared fact')]);
  assert.ok(!m.some(x => x.id === 'p2'));
  assert.ok(m.some(x => x.id === 'p1'));
  assert.ok(m.some(x => x.id === 's1'));
});

test('fact merge LWW (Kotlin fixture)', () => {
  const m = mergeFacts([f('k', 'mera', { ver: 2 })], [f('k', 'un ka', { ver: 1 })]);
  assert.strictEqual(m[0].text, 'mera');
});

test('memory -> cloud: stable ids like Kotlin idOf (sha256 first 20 hex)', () => {
  const idOf = (s) => crypto.createHash('sha256').update(String(s).trim()).digest('hex').slice(0, 20);
  const facts = ['mera naam Boss hai', 'delhi me rehta hu'].map(x => ({ id: idOf(x), text: x }));
  assert.strictEqual(facts.length, 2);
  assert.strictEqual(facts[0].id, idOf('mera naam Boss hai'));
  assert.notStrictEqual(facts[0].id, facts[1].id);
});

// brain.js export/import sync (add-only API for the cloud loop)
test('brain exportSync/importSync round trip + no duplicate on import', async () => {
  const { Brain } = require('../brain.js');
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'brain-'));
  const b = new Brain(dir + '/brain.json');
  b.run({ act: 'note_add', text: 'note ek' }, Date.now());
  b.run({ act: 'remember', text: 'yaad wali baat' }, Date.now());
  const exported = b.exportSync('laptop-1');            // [{id, text, scope, ver, ts, device}]
  assert.ok(Array.isArray(exported) && exported.length >= 2);
  assert.ok(exported.every(x => x.id && x.text && x.device === 'laptop-1'));
  // doosri "device" ki brain me import -> items aa jaate hain, duplicate nahi
  const b2 = new Brain(dir + '/brain2.json');
  const added = b2.importSync(exported);
  assert.ok(added >= 2);
  const addedAgain = b2.importSync(exported);           // dobara import = 0 naya
  assert.strictEqual(addedAgain, 0);
  assert.ok(b2.d.memory.some(m => m.t === 'yaad wali baat'));
  fs.rmSync(dir, { recursive: true, force: true });
});
