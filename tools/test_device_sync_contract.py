#!/usr/bin/env python3
"""v7.218 DEVICE SYNC contract (Part 2): phone <-> laptop integration.
1) static: SyncRules + service wiring + laptop-link naye types (stat/task/mem) dono taraf + brain export/import.
2) interop (node): desktop merge fixtures EXACTLY Kotlin SyncRulesTest wale (phone aur laptop same winner chunein)."""
import json, os, re, shutil, subprocess, sys
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
bad = []
K = rd('app/src/main/java/com/nova/assistant/SyncRules.kt')
KT = rd('app/src/test/java/com/nova/assistant/SyncRulesTest.kt')
S = rd('app/src/main/java/com/nova/assistant/JarvisService.kt')
J = rd('desktop/link.js')
JM = rd('desktop/main.js')
B = rd('desktop/brain.js')

# --- 1) SyncRules constants dono taraf same
for need in ['MAX_TASKS = 100', 'MAX_FACTS = 200', 'MAX_TEXT = 400']:
    if need not in K: bad.append('SyncRules missing ' + need)
# --- 2) LWW + private rule
if 'lwwWinner' not in K: bad.append('SyncRules LWW not using JarvisCloud.Rules.lwwWinner')
if 'private' not in K: bad.append('SyncRules private scope rule missing')
# --- 3) naye link types phone + desktop dono taraf (purane cmd/ask/reply untouched)
for need in ['"stat" ->', '"mem" ->']:
    if need not in S: bad.append('JarvisService link type missing ' + need)
for need in ["m.type === 'stat'", "m.type === 'mem'"]:
    if need not in JM: bad.append('desktop main.js link type missing ' + need)
# purane types batche hain
for need in ['"cmd" ->', '"ask" ->', '"reply" ->']:
    if need not in S: bad.append('PURANA laptop-link type toota: ' + need)
# --- 4) memory sync cloud mode + link-only mode (phone)
for need in ['syncMemoryCloud', 'PersonalMemoryStore.save', 'factsToMemory']:
    if need not in S: bad.append('phone memory sync missing ' + need)
# --- 5) desktop: task worker + memory sync loops
for need in ['taskPull', 'taskClaim', 'taskDone', 'exportSync', 'importSync']:
    if need not in JM: bad.append('desktop loops missing ' + need)
for need in ['exportSync(device)', 'importSync(items)']:
    if need not in B: bad.append('brain.js missing ' + need)
# --- 6) "laptop ko bolo" handoff + "laptop ka status" phone me
for need in ['laptopHandoffRoute', 'laptopStatRoute']:
    if need not in S: bad.append('JarvisService missing ' + need)
# network background thread me (main thread crash nahi hona chahiye)
if 'Thread {   // network kabhi main thread par nahi' not in S: bad.append('handoff route network thread me nahi hai')
# --- 7) id rule same (sha256 first 20 hex) dono taraf
if 'take(20)' not in K: bad.append('Kotlin idOf rule wrong')
if "slice(0, 20)" not in B and "digest('hex').slice(0, 20)" not in B: bad.append('brain.js idOf rule wrong')

# --- interop: node fixtures = Kotlin SyncRulesTest
node = shutil.which('node')
if node:
    code = """
const fs = require('node:fs');
const C = require(%s);
function mergeTasks(mine, theirs) {
  const MAX_TASKS = 100;
  const byId = new Map();
  for (const t of theirs) byId.set(t.id, t);
  for (const t of mine) { const old = byId.get(t.id); byId.set(t.id, old ? C.lwwWinner(t, t.ver, t.ts, t.device, old, old.ver, old.ts, old.device) : t); }
  return [...byId.values()].sort((a, b) => a.ts - b.ts).slice(-MAX_TASKS);
}
const t = (id, title, o = {}) => Object.assign({ id, title, target: '', state: 'open', claimedBy: '', ver: 1, ts: 0, device: '' }, o);
// Kotlin SyncRulesTest.taskMergeTieBreakByDevice fixtures
const r1 = mergeTasks([t('x', 'mine', { ts: 100, device: 'd1' })], [t('x', 'theirs', { ts: 100, device: 'd2' })])[0].title;
const r2 = mergeTasks([t('x', 'mine', { ts: 100, device: 'd2' })], [t('x', 'theirs', { ts: 100, device: 'd1' })])[0].title;
// Kotlin taskMergeLwwSameVerNewerTs fixture
const r3 = mergeTasks([t('x', 'purana', { ts: 100 })], [t('x', 'naya', { ts: 200 })])[0].title;
// Kotlin taskMergeSortsByTsAndCaps fixture (150 -> last = kaam 150)
const many = []; for (let i = 1; i <= 150; i++) many.push(t('t' + i, 'kaam ' + i, { ts: i }));
const m = mergeTasks(many, []);
console.log(JSON.stringify({ r1, r2, r3, cap: m.length, last: m[m.length - 1].title }));
""" % json.dumps(os.path.join(R, 'desktop/cloud.js'))
    r = subprocess.run([node, '-e', code], capture_output=True, text=True, timeout=60)
    try:
        o = json.loads(r.stdout)
        expect = {'r1': 'theirs', 'r2': 'mine', 'r3': 'naya', 'cap': 100, 'last': 'kaam 150'}
        for k, v in expect.items():
            if o.get(k) != v: bad.append('JS merge fixture %s: %r != %r' % (k, o.get(k), v))
    except Exception:
        bad.append('node interop failed: ' + r.stderr[:150])
else:
    print('  (node missing: interop SKIPPED)')

if bad:
    print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS device sync contract (static%s)' % (' + node interop fixtures == Kotlin' if node else ''))
