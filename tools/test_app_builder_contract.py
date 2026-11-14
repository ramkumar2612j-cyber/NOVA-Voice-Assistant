"""v7.176 (c) APP BUILDER contract: voice grammar + deep research + source-pinned facts + build + install are wired UI <-> voice <-> code.
Evidence level: static wiring checks. The pure rules are compiled and unit-run separately (AppBuilderRulesTest.kt)."""
import os, re, sys, json, subprocess, tempfile
HERE = os.path.dirname(os.path.abspath(__file__))
J = os.path.join(HERE, '..', 'app/src/main/java/com/nova/assistant/')
def rd(p): return open(p, encoding='utf-8').read()
R = rd(J + 'AppBuilderRules.kt'); A = rd(J + 'AppBuilder.kt'); N = rd(J + 'NovaService.kt'); M = rd(J + 'MainActivity.kt')
SG = rd(J + 'SiteGen.kt'); U = rd(os.path.join(HERE, '..', 'app/src/main/assets/ui.html')); X = rd(os.path.join(HERE, '..', 'app/src/main/res/xml/nova_file_paths.xml'))
bad = []
def need(c, m):
    if not c: bad.append(m)
# voice + screen wiring
need('AppBuilderRules.parse(text)' in N and 'appBuilderRoute(appCmd)' in N, 'NovaService does not route AppBuilderRules.parse')
for k in ['K_APP_BUILD', 'K_APP_STOP', 'K_APP_SCREEN', 'K_APP_INSTALL']:
    need('AppBuilderRules.' + k in N, 'NovaService does not handle ' + k)
need('AppBuilder.ui = { js -> runJs(js) }' in M, 'MainActivity does not set AppBuilder.ui')
for fn in ['appBuilderStatus', 'appBuilderStart', 'appBuilderStop', 'appBuilderInstall']:
    need('fun ' + fn + '(' in M and fn in U, fn + ' missing in bridge or ui.html')
for i in ['apbScr', 'apbBack', 'apbLive', 'apbStages', 'apbTerm', 'apbIdea', 'apbGo', 'apbStop', 'apbInstall', 'apbCopyRepo', 'apbMsg']:
    need('id="%s"' % i in U, 'ui.html missing id ' + i)
need('window.__apbOpen' in U and 'window.onAppBuilderEvent' in U, 'ui.html screen open/event hooks missing')
need("window.__apbOpen()" in U and "'APP BUILDER'" in U, 'APP BUILDER card / Skills entry does not open the screen')
need('cache-path name="nova_built_apps" path="built_apps/"' in X, 'FileProvider path for built_apps missing')
need('internal fun requestHtml' in SG, 'SiteGen.requestHtml must be internal for AppBuilder')
# honesty / research rules
need('AnalysisSearchRules.runSearch' in A and 'fetchPage' in A and 'MAX_PAGES' in A, 'deep research (search + pages) not wired')
need('numbersSupported' in R and 'parseSheet' in R and 'notEnough' in R, 'fact verification rules missing')
need('NEVER fill a gap from your own memory' in R and 'Never invent or' in R, 'anti-invention prompt rules missing')
need('checkHtml' in A and 'repairPrompt' in A, 'invented-detail check + repair not wired')
need('why != null' in A and 'notEnoughText' in A, 'a topic with too few verified facts must stop the build')
# secrets / safety
need('"Authorization", "Bearer " + token' in A, 'token must be sent in a header')
need(not re.search(r'log\(g, [^\n]*token', A), 'token must never be logged')
need('?access_token' not in A, 'token must never be in a URL')
need('validUrl' in A and 'hostAllowed' in A, 'page fetch / download must validate hosts')
need('info.packageName != pkg' in A, 'downloaded APK must be verified against the built package')
need('canRequestPackageInstalls' in A and 'ACTION_MANAGE_UNKNOWN_APP_SOURCES' in A, 'install permission flow missing')
need('workflow_dispatch:' in R and 'push:' not in R.split('WORKFLOW_TPL')[1].split('""".replace')[0], 'workflow must be dispatch-only (one run, not one per pushed file)')
need('stop(en: Boolean)' in A and 'gen.incrementAndGet()' in A, 'stop must end only the build (generation counter)')
need('mlkit' not in A.lower(), 'sanity')
# the generated project templates must be well formed
import xml.dom.minidom
def tpl(name):
    m = re.search(r'private val %s = """(.*?)"""' % name, R, re.S); return m.group(1) if m else ''
for n in ['MANIFEST_TPL', 'ICON_TPL']:
    try: xml.dom.minidom.parseString(tpl(n).replace('__NAME__', 'X').replace('__COLOR__', '#000000').encode())
    except Exception as e: bad.append(n + ' is not valid XML: ' + str(e))
# the ui script must parse
m = re.search(r'<script id="v176-apb">(.*?)</script>', U, re.S)
if not m: bad.append('ui script missing')
else:
    try:
        with tempfile.NamedTemporaryFile('w', suffix='.js', delete=False, encoding='utf-8') as f: f.write(m.group(1)); fn = f.name
        r = subprocess.run(['node', '--check', fn], capture_output=True, text=True)
        if r.returncode != 0: bad.append('ui script syntax: ' + r.stderr[:200])
    except FileNotFoundError: pass
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS app builder contract')
