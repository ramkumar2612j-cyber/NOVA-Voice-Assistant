#!/usr/bin/env python3
"""v7.217 CLOUD BRAIN contract (Part 1):
1) static: Kotlin <-> desktop/cloud.js <-> backend constants match; pairing prefix; https-only; caps; SecureStore slots;
   bridge + BRIDGE list + ui.html box; service wiring; no API key strings in desktop cloud code.
2) interop (node): desktop cloud.js ka LWW + pairing parse fixture vectors (Kotlin JarvisCloudRulesTest wale hi).
3) backend: import brain_server (syntax) + uske endpoints ke paths constants se match.
Kotlin compile yahan NAHI hota (local_compile_check.sh / GitHub CI uska kaam)."""
import json, os, re, shutil, subprocess, sys
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
bad = []
K = rd('app/src/main/java/com/nova/assistant/JarvisCloud.kt')
S = rd('app/src/main/java/com/nova/assistant/JarvisService.kt')
M = rd('app/src/main/java/com/nova/assistant/MainActivity.kt')
U = rd('app/src/main/assets/ui.html')
J = rd('desktop/cloud.js')
JM = rd('desktop/main.js')
B = rd('backend/brain_server.py')

# --- 1) pairing code contract: sab jagah JARVIS-CLOUD: prefix + same field names (a = addr, p = token)
for need in ['JARVIS-CLOUD', '"p"', '"a"']:
    if need not in K: bad.append('JarvisCloud.kt missing ' + need)
if 'JARVIS-CLOUD' not in B: bad.append('brain_server.py missing JARVIS-CLOUD prefix')
if "jarvis-cloud" not in J: bad.append('cloud.js missing jarvis-cloud prefix')

# --- 2) SecureStore slots (cloudbrain + cloudfamily) sirf wahi jagah — koi API key slot nahi
if 'SLOT = "cloudbrain"' not in K or 'SLOT_FAMILY = "cloudfamily"' not in K: bad.append('JarvisCloud slots wrong')
if re.search(r'slot.*(api_key|groq|gemini)', K, re.I): bad.append('JarvisCloud touches an API key slot')

# --- 3) https-only + localhost exception dono taraf
for need in ['https://', '127.0.0.1', 'localhost']:
    if need not in K: bad.append('JarvisCloud cleanBase missing ' + need)
    if need not in J: bad.append('cloud.js cleanBase missing ' + need)

# --- 4) LWW semantics comment (numeric ver/ts, string device tie-break) + retry backoff same
if 'NUMERIC compare' not in K or 'NUMERIC compare' not in J: bad.append('LWW numeric-compare comment missing (Kotlin/JS)')
if '60_000L' not in K or '60000' not in J: bad.append('retry backoff 1-min step missing (Kotlin/JS)')
if '15 * 60_000L' not in K or '15 * 60000' not in J: bad.append('retry backoff 15-min cap missing (Kotlin/JS)')

# --- 5) body caps
if 'MAX_BODY = 8_192' not in K or 'MAX_BODY = 8192' not in J: bad.append('client body caps differ')
if 'MAX_BODY = 64_000' not in B: bad.append('server body cap missing')

# --- 6) service wiring (add-only pattern)
for need in ['fun restartCloudSync()', 'cloudEpoch', 'restartCloudSync() }', 'cloudRoute(text) ?: laptopRoute(text) ?: handle(pcm, text)', '"jarvis-cloud-sync"']:
    if need not in S: bad.append('JarvisService missing ' + need)
# purana laptop-link chain batcha rehna chahiye
for need in ['fun restartLaptopLink', '"cmd" ->', '"ask" ->', '"reply" ->', 'laptopRoute(text) ?: handle(pcm, text)']:
    if need not in S: bad.append('JarvisService PURANA laptop-link hissa toota: ' + need)

# --- 7) bridge: capped + BRIDGE list me naam + ui box
if 'fun saveCloudLink(raw0: String): String = try {' not in M or 'raw0.length > 400' not in M: bad.append('saveCloudLink cap/shape wrong')
for fn in ['saveCloudLink', 'hasCloudLink', 'clearCloudLink', 'cloudStatus']:
    if 'fun ' + fn not in M: bad.append('bridge missing ' + fn)
    if '"%s"' % fn not in U: bad.append('BRIDGE list missing ' + fn)
for need in ['id="cb_key"', 'id="cb_save"', 'id="cb_del"', 'id="cb_st"', 'Android.saveCloudLink', 'Android.clearCloudLink', 'Android.cloudStatus']:
    if need not in U: bad.append('ui.html missing ' + need)

# --- 8) desktop: cloud commands + store me sirf addr/token/family; koi API key ka zikr nahi
for need in ["require('./cloud')", 'cloud.json', 'cloud (?:jodo|jodna|pair|connect)']:
    if need not in JM: bad.append('desktop/main.js missing ' + need)
if re.search(r'GEMINI|GROQ|api[_-]?key', J + JM.replace('API keys kabhi cloud par nahi jaate', ''), re.I): bad.append('desktop cloud code mentions an API key')

# --- 9) backend endpoints ka exact path set (client + server match)
paths_kt = set(re.findall(r'/api/[a-z/]+', K))
paths_js = set(re.findall(r'/api/[a-z/]+', J))
paths_py = set(re.findall(r'"(/(?:api|admin)/[a-z/]+)"', B))
need_paths = {'/api/register', '/api/status', '/api/tasks/push', '/api/tasks/pull', '/api/tasks/claim', '/api/tasks/done', '/api/memory/push', '/api/memory/pull', '/api/sync'}
if not need_paths <= paths_py: bad.append('backend missing paths: %s' % (need_paths - paths_py))
if not need_paths <= (paths_kt | paths_js): bad.append('clients missing paths: %s' % (need_paths - (paths_kt | paths_js)))

# --- 10) JUnit test file exists
if not os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/JarvisCloudRulesTest.kt')): bad.append('JUnit test missing')
if not os.path.exists(os.path.join(R, 'desktop/test/cloud.test.js')): bad.append('desktop cloud test missing')
if not os.path.exists(os.path.join(R, 'backend/test_brain_server.py')): bad.append('backend test missing')

# --- interop: node se cloud.js ke fixture vectors (Kotlin wale hi)
node = shutil.which('node')
if node:
    code = """
const C = require(%s);
const out = {
  v1: C.lwwWinner('mine', 2, 1000, 'd1', 'theirs', 1, 2000, 'd2'),
  v2: C.lwwWinner('mine', 1, 1000, 'd1', 'theirs', 1, 2000, 'd2'),
  v3: C.lwwWinner('mine', 1, 1000, 'd1', 'theirs', 1, 1000, 'd2'),
  v4: C.lwwWinner('mine', 1, 999999, 'd9', 'theirs', 2, 1, 'd1'),
  v5: C.lwwWinner('mine', 10, 1, 'd1', 'theirs', 9, 999, 'd2'),
  num: C.lwwWinner('mine', 9, 999, 'd1', 'theirs', 10, 1, 'd2')
};
console.log(JSON.stringify(out));
""" % json.dumps(os.path.join(R, 'desktop/cloud.js'))
    r = subprocess.run([node, '-e', code], capture_output=True, text=True, timeout=60)
    try:
        o = json.loads(r.stdout)
        expect = {'v1': 'mine', 'v2': 'theirs', 'v3': 'theirs', 'v4': 'theirs', 'v5': 'mine', 'num': 'theirs'}
        for k, v in expect.items():
            if o.get(k) != v: bad.append('cloud.js LWW fixture %s: %s != %s' % (k, o.get(k), v))
    except Exception:
        bad.append('cloud.js interop run failed: ' + r.stderr[:120])
else:
    print('  (node missing: interop SKIPPED)')

if bad:
    print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS cloud brain contract (static%s)' % (' + node interop' if node else ''))
