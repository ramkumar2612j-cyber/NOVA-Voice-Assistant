#!/usr/bin/env python3
"""v7.35 AUTO VERIFY guard (add-only): the self-verify chain is wired and its lists are complete. Static, no compile."""
import os, re, sys, glob
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
M = os.path.join(R, 'app/src/main'); J = os.path.join(M, 'java/com/nova/assistant')
rd = lambda p: open(p, encoding='utf8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)
fv = rd(J + '/FeatureVerify.kt'); ma = rd(J + '/MainActivity.kt'); ns = rd(J + '/NovaService.kt'); hc = rd(J + '/HealthCheck.kt'); ui = rd(M + '/assets/ui.html')
need(os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/FeatureVerifyTest.kt')), 'FeatureVerifyTest.kt missing')
for f in ['fun probes(', 'fun stateChecks(', 'fun pageChecks(', 'fun start(', 'fun healthLines(', 'fun isAsk(', 'FeatureCheck.understanding(rec)', 'ErrLog.add("Verify"', 'ActionLog.add(act, "NOVA VERIFY"']:
    need(f in fv, 'FeatureVerify missing: ' + f)
need(fv.count('{') == fv.count('}') and fv.count('(') == fv.count(')'), 'FeatureVerify braces/parens unbalanced')
need(fv.count('/*') == fv.count('*/'), 'FeatureVerify comment open/close (Kotlin nests comments)')
need('FeatureVerify.start(this, 12_000L' in ma, 'MainActivity: automatic start missing')
need(ma.index('FeatureVerify.start(this, 12_000L') > ma.index('AutoTest.kick(this)'), 'verify start must come after the old AutoTest.kick line')
for fn in ['verifyPage', 'verifyNow']:
    need(re.search(r'@JavascriptInterface\s+fun %s\(' % fn, ma) is not None, 'bridge missing: ' + fn)
need('FeatureVerify.healthLines()' in hc, 'HealthCheck: AUTO VERIFY line missing')
need('FeatureVerify.isAsk(text)' in ns and ns.index('FeatureVerify.isAsk(text)') > ns.index('AutoTrace.isAsk(text)'), 'NovaService: auto verify route missing')
need('id="p6_verify"' in ui and ui.index('id="p6_verify"') > ui.index('id="p5_log"'), 'ui.html: p6_verify must be appended after p5_log')
need(ui.rstrip().endswith('</body></html>'), 'ui.html end changed')
p6 = ui[ui.index('<script id="p6_verify">'):]
m = re.search(r'var BRIDGE=(\[.*?\]);', p6, re.S); BR = set(re.findall(r'"(\w+)"', m.group(1))) if m else set()
m = re.search(r'var CB=(\[.*?\]);', p6, re.S); CB = set(re.findall(r'"(\w+)"', m.group(1))) if m else set()
used = set(re.findall(r"\bA\(\s*['\"](\w+)['\"]", ui)) | set(re.findall(r"\bAndroid\.(\w+)\s*\(", ui)) | set(re.findall(r"Android\[\s*['\"](\w+)['\"]", ui))
need(used <= BR, 'p6 BRIDGE list is missing Android.* calls of the page: %s' % sorted(used - BR))
bm = set(re.findall(r'@JavascriptInterface\s+fun\s+(\w+)', ma))
need(BR <= bm, 'p6 BRIDGE names with no Kotlin bridge: %s' % sorted(BR - bm))
ks = '\n'.join(rd(f) for f in glob.glob(J + '/*.kt'))
kcb = set(re.findall(r'(?:runJs|evaluateJavascript)\(\s*"(?:if\(window\.\w+\))?([A-Za-z_]\w*)\(', ks))
kcb = {c for c in kcb if re.search(r'window\.%s\s*=|function\s+%s\b' % (c, c), ui)}
need(kcb <= CB, 'p6 CB list is missing Kotlin->JS callbacks: %s' % sorted(kcb - CB))
for c in CB: need(re.search(r'window\.%s\s*=|function\s+%s\b' % (c, c), ui) is not None, 'p6 CB name not defined in ui.html: ' + c)
need('localStorage' not in p6 and 'c_in' not in p6.split('var IDS')[0], 'p6 must not store or read typed text')
if bad:
    print('FAIL part6 contract'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS part6 contract (bridge list %d, callback list %d)' % (len(BR), len(CB)))
