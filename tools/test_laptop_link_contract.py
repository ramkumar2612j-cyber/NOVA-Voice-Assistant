#!/usr/bin/env python3
"""v7.177 LAPTOP LINK phone side contract.
1) static: LaptopLink.kt constants == desktop/link.js, bridge + UI + service wiring present, no API key in desktop code.
2) interop (needs node + java): desktop/link.js <-> Java mirror of LaptopLink.kt crypto (topic names, AES-GCM layout, AAD, tamper/wrong-key/stale rejection).
Kotlin compile is NOT done here (no kotlinc offline); GitHub build covers that."""
import json, os, re, shutil, subprocess, sys, tempfile
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
bad = []
K = rd('app/src/main/java/com/nova/assistant/LaptopLink.kt'); J = rd('desktop/link.js'); S = rd('app/src/main/java/com/nova/assistant/JarvisService.kt')
M = rd('app/src/main/java/com/nova/assistant/MainActivity.kt'); U = rd('app/src/main/assets/ui.html')
for need in ['"AES/GCM/NoPadding"', 'GCMParameterSpec(128', 'updateAAD(topic.toByteArray())', '"jarvis-"', '.take(40)', 'SKEW_MS = 120_000L', 'MAX_TEXT = 1500', 'copyOfRange(0, 12)', 'raw.size < 29', 'ntfy.sh']:
    if need not in K: bad.append('LaptopLink.kt missing ' + need)
for need in ["'aes-256-gcm'", 'MAX_SKEW_MS = 120000', 'MAX_TEXT = 1500', "'jarvis-'", '.slice(0, 40)', 'raw.length < 29']:
    if need not in J: bad.append('link.js missing ' + need)
for fn in ['saveLaptopLink', 'hasLaptopLink', 'clearLaptopLink']:
    if 'fun ' + fn in M and ('"%s"' % fn) in U: pass
    else: bad.append('bridge ' + fn + ' missing in MainActivity or BRIDGE list')
for need in ['id="ll_key"', 'id="ll_save"', 'id="ll_del"', 'id="ll_st"', 'Android.saveLaptopLink', 'Android.clearLaptopLink']:
    if need not in U: bad.append('ui.html missing ' + need)
for need in ['fun restartLaptopLink', 'restartLaptopLink() }', 'laptopRoute(text) ?: handle(pcm, text)', 'linkEpoch++', '"cmd" ->', '"ask" ->', '"reply" ->', 'runFreeChain(text, en, withFacts = false)', 'LaptopLinkRules.Dedupe()']:
    if need not in S: bad.append('JarvisService missing ' + need)
if re.search(r'GEMINI|GROQ|api[_-]?key', rd('desktop/main.js') + J, re.I): bad.append('desktop code mentions an API key')
# keys / codes must never be logged
for m in re.finditer(r'ErrLog\.add\([^)]*\)', S[S.index('fun restartLaptopLink'):S.index('fun typed(')]):
    if 'key' in m.group(0).lower(): bad.append('laptop link logs something key-like: ' + m.group(0))

# ---- interop ----
node, java = shutil.which('node'), shutil.which('java')
ran = False
if node and java:
    ran = True
    W = os.path.join(R, 'tools/laptop_link_interop')
    jr = lambda *a: subprocess.run([java, os.path.join(W, 'Interop.java')] + list(a), capture_output=True, text=True, timeout=120).stdout
    nd = lambda code: subprocess.run([node, '-e', "const L=require(%s);" % json.dumps(os.path.join(R, 'desktop/link.js')) + code], capture_output=True, text=True, timeout=60).stdout.strip()
    key = nd("console.log(L.genKey())"); t = json.loads(nd("console.log(JSON.stringify(L.topics(%s)))" % json.dumps(key)))
    if jr('topic', key, 'l2p') != t['l2p'] or jr('topic', key, 'p2l') != t['p2l']: bad.append('topic names differ node vs java')
    env = nd("console.log(L.seal(%s,%s,{type:'cmd',text:'chrome kholo \u0939\u093f\u0902\u0926\u0940 \U0001F600'}))" % (json.dumps(key), json.dumps(t['l2p'])))
    pj = jr('open', key, t['l2p'], env)
    try:
        o = json.loads(pj); assert o['type'] == 'cmd' and o['text'].startswith('chrome kholo') and abs(o['ts'] / 1000 - __import__('time').time()) < 60
    except Exception: bad.append('java could not open a node envelope: ' + pj[:80])
    if jr('open', key, t['p2l'], env) != 'NULL': bad.append('java opened with the WRONG topic (AAD not enforced)')
    other = nd("console.log(L.genKey())")
    if jr('open', other, t['l2p'], env) != 'NULL': bad.append('java opened with the WRONG key')
    bad_env = env[:-4] + ('AAAA' if env[-4:] != 'AAAA' else 'BBBB')
    if jr('open', key, t['l2p'], bad_env) != 'NULL': bad.append('java accepted a tampered envelope')
    body = json.dumps({'id': 'abc123', 'ts': int(__import__('time').time() * 1000), 'type': 'reply', 'text': 'jawab \u0939\u093f\u0902\u0926\u0940', 're': 'x1'}, ensure_ascii=False)
    je = jr('seal', key, t['p2l'], body)
    out = nd("const m=L.open(%s,%s,%s);console.log(JSON.stringify(m))" % (json.dumps(key), json.dumps(t['p2l']), json.dumps(je)))
    try:
        o = json.loads(out); assert o['type'] == 'reply' and o['re'] == 'x1' and o['text'].startswith('jawab')
    except Exception: bad.append('node could not open a java envelope: ' + out[:80])
    old = json.dumps({'id': 'old1', 'ts': int(__import__('time').time() * 1000) - 600000, 'type': 'cmd', 'text': 'x', 're': ''})
    if nd("console.log(JSON.stringify(L.open(%s,%s,%s)))" % (json.dumps(key), json.dumps(t['p2l']), json.dumps(jr('seal', key, t['p2l'], old)))) != 'null': bad.append('node accepted a stale (10 min old) message')
if bad:
    print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS laptop link contract (static%s)' % (' + node<->java crypto interop' if ran else '; interop SKIPPED: node or java missing'))
