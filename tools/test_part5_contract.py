#!/usr/bin/env python3
"""v7.34 PART 5 guard (add-only): AUTO log chain is wired end to end. Static, no compile."""
import os, re, sys, hashlib
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
M = os.path.join(R, 'app/src/main'); J = os.path.join(M, 'java/com/nova/assistant')
def rd(p): return open(p, encoding='utf8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)
at = rd(J + '/AutoTrace.kt') if os.path.exists(J + '/AutoTrace.kt') else ''
need(at, 'AutoTrace.kt missing')
need(os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/AutoTraceTest.kt')), 'AutoTraceTest.kt missing')
for f in ['fun add(', 'fun addUi(', 'fun summary(', 'fun isAsk(', 'fun cleanSubject(', 'ErrLog.clean', 'RATE_MS = 10_000L', 'MAX = 30']:
    need(f in at, 'AutoTrace missing: ' + f)
need('android.' not in at, 'AutoTrace must stay pure Kotlin (no android imports)')
need(at.count('{') == at.count('}') and at.count('(') == at.count(')'), 'AutoTrace braces/parens unbalanced')
ns = rd(J + '/NovaService.kt'); hc = rd(J + '/HealthCheck.kt'); ma = rd(J + '/MainActivity.kt'); ui = rd(M + '/assets/ui.html')
need('AutoTrace.add(tk, autoReq.subject' in ns, 'NovaService: AutoTrace.add call missing')
need('"NOVA AUTO"' in ns and 'ActionLog.add(this, "NOVA AUTO"' in ns, 'NovaService: ActionLog NOVA AUTO missing')
need('ErrLog.add("AutoIntent"' in ns, 'NovaService: ErrLog on exception missing')
need('AutoTrace.isAsk(text)' in ns and 'AutoTrace.summary()' in ns, 'NovaService: auto kya samjha route missing')
a = ns.index('val autoReq = if (text.isBlank())'); b = ns.index('AutoTrace.add(tk'); c = ns.index('return siteBegin(SiteIntent.Parsed(autoReq.subject')
need(a < b < c, 'AutoTrace record must sit after autoReq line and before the old routes return')
need('AutoTrace.summary()' in hc, 'HealthCheck: AUTO TRACE section missing')
need(re.search(r'@JavascriptInterface\s+fun uiLog\(tag: String, msg: String\)', ma) is not None, 'MainActivity: uiLog bridge missing')
need('Android.uiLog' in ui and 'id="p5_log"' in ui, 'ui.html: p5_log wrapper missing')
need("typeof Android.uiLog==='function'" in ui, 'ui.html: typeof guard missing')
need(ui.rstrip().endswith('</body></html>'), 'ui.html end changed')
need(ui.index('id="p5_log"') > ui.index('id="p4_fx"'), 'p5_log must be appended after p4_fx')
p5 = ui[ui.index('<script id="p5_log">'):].split('<script id="p6_verify">')[0]
need('localStorage' not in p5 and 'sessionStorage' not in p5, 'p5_log must not store user text')
need('c_in' not in p5, 'p5_log must not read the typed text')
# privacy: no raw user text variable passed to the trace
for m in re.finditer(r'AutoTrace\.add\(([^)]*)\)', ns):
    need(re.search(r'\btext\b|rawText|text0', m.group(1)) is None, 'AutoTrace.add must not get the full sentence: ' + m.group(1))
# every Android.* the page calls has a bridge (spot check of the new one + audit tool)
need('fun detectIntent' in ma and 'fun editPhoto' in ma, 'detectIntent/editPhoto bridge missing')
need(hashlib.sha256(open(os.path.join(R, '.github/workflows/build.yml'), 'rb').read()).hexdigest() == 'e8a0351533826bd47582c70bd560539263af889d6020d106d4cfad49fea5e2ba', 'build.yml changed!')
if bad:
    print('FAIL part5 contract'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS part5 contract')
