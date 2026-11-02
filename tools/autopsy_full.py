#!/usr/bin/env python3
"""NOVA full-file AUTOPSY (v7.36 Part 1, add-only new tool, read-only: changes nothing).
Checks every Kotlin file / the page / the manifest for wiring gaps:
  A orphan Kotlin files (declared names never used anywhere else)
  B Android.* calls in ui.html that have no @JavascriptInterface method, and wrong argument counts
  C bridge methods nothing in the page calls (informational)
  D JS callbacks Kotlin calls that the page does not define
  E getElementById / $ / g literals that exist nowhere (HTML or runtime-created)
  F duplicate static ids
  G inline on*= handlers that call a function the page does not define
  H manifest components vs Kotlin classes (both directions)
  I R.xxx.yyy resources that do not exist
Exit 1 when a REAL gap (B, D, H-missing, I) is found. Usage: python3 tools/autopsy_full.py [--json]"""
import os, re, sys, json

R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
MAIN = os.path.join(R, 'app/src/main')
KT = os.path.join(MAIN, 'java/com/nova/assistant')
TEST = os.path.join(R, 'app/src/test/java/com/nova/assistant')
UI = open(os.path.join(MAIN, 'assets/ui.html'), encoding='utf-8').read()
MANIFEST = open(os.path.join(MAIN, 'AndroidManifest.xml'), encoding='utf-8').read()
kt = {f: open(os.path.join(KT, f), encoding='utf-8').read() for f in sorted(os.listdir(KT)) if f.endswith('.kt')}
tests = {f: open(os.path.join(TEST, f), encoding='utf-8').read() for f in sorted(os.listdir(TEST)) if f.endswith('.kt')} if os.path.isdir(TEST) else {}
allkt = '\n'.join(kt.values())
report = {}
real = []


def add(key, items, is_real=False):
    report[key] = items
    if is_real and items:
        real.extend('%s: %s' % (key, i) for i in items)


# ---- A orphan files
orph = []
for f, s in kt.items():
    names = re.findall(r'^(?:internal |private |data |sealed |abstract |open )*(?:object|class|interface)\s+(\w+)', s, re.M)
    if not names:
        names = re.findall(r'^fun\s+(?:\w+\.)?(\w+)', s, re.M)
    if not names:
        continue
    used = False
    for n in names:
        pat = re.compile(r'\b' + re.escape(n) + r'\b')
        for g, t in kt.items():
            if g != f and pat.search(t):
                used = True; break
        if not used and pat.search(MANIFEST):
            used = True
        if used:
            break
    if not used:
        in_tests = any(re.search(r'\b' + re.escape(n) + r'\b', t) for n in names for t in tests.values())
        orph.append('%s (names %s; used by tests only: %s)' % (f, ','.join(names[:3]), in_tests))
add('A_orphan_kotlin_files', orph)

# ---- B/C bridge
bridge = {}
for m in re.finditer(r'@JavascriptInterface\s+(?:@\w+(?:\([^)]*\))?\s+)*fun\s+(\w+)\s*\(([^)]*)\)', kt['MainActivity.kt']):
    params = [p for p in m.group(2).split(',') if p.strip()]
    bridge[m.group(1)] = len(params)
calls = {}
for m in re.finditer(r'\bAndroid\.(\w+)\s*\(', UI):
    name = m.group(1)
    # count args roughly: balanced parens scan
    i = m.end(); depth = 1; args = 0; seen = False; q = None
    while i < len(UI) and depth > 0:
        c = UI[i]
        if q:
            if c == '\\': i += 1
            elif c == q: q = None
        elif c in '"\'`': q = c; seen = True
        elif c in '([{': depth += 1; seen = True
        elif c in ')]}': depth -= 1
        elif c == ',' and depth == 1: args += 1
        elif not c.isspace(): seen = True
        i += 1
    n = (args + 1) if seen else 0
    calls.setdefault(name, set()).add(n)
miss = sorted(n for n in calls if n not in bridge)
add('B_ui_calls_without_bridge_method', miss, True)
badarity = []
for n, counts in sorted(calls.items()):
    if n in bridge and all(c != bridge[n] for c in counts):
        badarity.append('%s: page passes %s, Kotlin takes %d' % (n, sorted(counts), bridge[n]))
add('B2_bridge_argument_count_mismatch', badarity, True)
add('C_bridge_methods_never_named_in_page', sorted(n for n in bridge if n not in calls and n not in UI))

# ---- D callbacks
cb = set()
for s in kt.values():
    for m in re.finditer(r'"((?:if\(window\.\w+\))?\(?(?:window\.)?)(on[A-Z]\w+)\(', s):
        cb.add(m.group(2))
    for m in re.finditer(r'runJs\("(?:if\(window\.\w+\))?(on[A-Z]\w+)\(', s):
        cb.add(m.group(1))
    for m in re.finditer(r'"(?:window\.)?(nova[A-Z]\w+|p\d_\w+|verify\w+)\(', s):
        cb.add(m.group(1))
dmiss = []
for n in sorted(cb):
    if not re.search(r'function\s+' + n + r'\b|window\.' + n + r'\s*=|\b' + n + r'\s*=\s*function|\b' + n + r'\s*=\s*\(', UI):
        dmiss.append(n)
add('D_kotlin_callbacks_missing_in_page', dmiss, True)

# ---- E ids
static_ids = set(re.findall(r'\bid=["\']([\w\-:]+)["\']', UI))
runtime_ids = set(re.findall(r'\.id\s*=\s*["\']([\w\-:]+)["\']', UI)) | set(re.findall(r'setAttribute\(["\']id["\'],\s*["\']([\w\-:]+)["\']', UI))
runtime_ids |= set(re.findall(r'\bid=\\?["\']([\w\-:]+)\\?["\']', UI))
lit = set()
for m in re.finditer(r'(?:getElementById|(?<![\w.])\$|(?<![\w.])g|(?<![\w.])byId|(?<![\w.])el)\(\s*["\']([\w\-:]+)["\']\s*\)', UI):
    lit.add(m.group(1))
emiss = sorted(i for i in lit if i not in static_ids and i not in runtime_ids)
add('E_id_literals_found_nowhere', emiss)

# ---- F duplicates (static markup only, scripts/styles removed)
markup = re.sub(r'<script\b.*?</script>|<style\b.*?</style>', '', UI, flags=re.S)
ids = re.findall(r'\bid=["\']([\w\-:]+)["\']', markup)
dups = sorted({i for i in ids if ids.count(i) > 1})
add('F_duplicate_static_ids', dups)

# ---- G inline handlers
defs = set(re.findall(r'function\s+([A-Za-z_$][\w$]*)', UI)) | set(re.findall(r'(?:window\.|var |let |const )([A-Za-z_$][\w$]*)\s*=\s*(?:function|\()', UI))
defs |= set(re.findall(r'window\.([A-Za-z_$][\w$]*)\s*=', UI))
BUILTIN = {'if', 'return', 'this', 'event', 'alert', 'confirm', 'document', 'window', 'Android', 'event', 'setTimeout', 'void', 'location', 'history', 'navigator', 'JSON', 'Math', 'parseInt', 'parseFloat', 'String', 'Number', 'console'}
hmiss = set()
for m in re.finditer(r'\bon(?:click|change|input|touchstart|touchend|submit|pointerdown)\s*=\s*"([^"]*)"', markup):
    for f in re.findall(r'(?<![\w.$])([A-Za-z_$][\w$]*)\s*\(', m.group(1)):
        if f not in defs and f not in BUILTIN:
            hmiss.add(f)
add('G_inline_handlers_undefined_function', sorted(hmiss))

# ---- H manifest
mf = set()
for m in re.finditer(r'android:name="(\.?[\w.]+)"', MANIFEST):
    n = m.group(1)
    if n.startswith('.'):
        mf.add(n[1:])
    elif n.startswith('com.nova.assistant.'):
        mf.add(n.split('.')[-1])
mmiss = sorted(n for n in mf if ('class ' + n) not in allkt and ('object ' + n) not in allkt)
add('H_manifest_class_missing_in_kotlin', mmiss, True)
comp = set(re.findall(r'class\s+(\w+)[^{\n]*:\s*[^{\n]*\b(?:Service|BroadcastReceiver|Activity|AppCompatActivity|NotificationListenerService|AccessibilityService|VoiceInteractionService|VoiceInteractionSessionService|RecognitionService|FileProvider|Worker|CoroutineWorker)\b', allkt))
add('H2_component_classes_not_in_manifest', sorted(c for c in comp if c not in mf and not c.endswith('Worker')))

# ---- I resources
res = set()
for root, _d, files in os.walk(os.path.join(MAIN, 'res')):
    d = os.path.basename(root)
    kind = d.split('-')[0]
    for f in files:
        res.add((kind, f.rsplit('.', 1)[0]))
        if f.endswith('.xml') and kind == 'values':
            x = open(os.path.join(root, f), encoding='utf-8').read()
            for tag, nm in re.findall(r'<(string|color|style|dimen|bool|integer|array|string-array)\s+name="([\w.]+)"', x):
                res.add(({'string-array': 'array'}.get(tag, tag), nm.replace('.', '_')))
rmiss = set()
for f, s in kt.items():
    for k, n in re.findall(r'(?<![\w.])R\.(\w+)\.(\w+)', s):
        if k in ('id', 'layout', 'attr', 'styleable'):
            continue
        if (k, n) not in res and (k, n.replace('.', '_')) not in res:
            rmiss.add('%s: R.%s.%s' % (f, k, n))
mfres = set(re.findall(r'@(?:mipmap|drawable|string|xml|style|color)/([\w.]+)', MANIFEST))
for n in sorted(mfres):
    if not any(n.replace('.', '_') == r[1] for r in res):
        rmiss.add('AndroidManifest: @%s' % n)
add('I_resources_missing', sorted(rmiss), True)

if '--json' in sys.argv:
    print(json.dumps(report, indent=1))
else:
    for k, v in report.items():
        print('%-42s %s' % (k, 'OK' if not v else '%d' % len(v)))
        for i in v[:60]:
            print('    -', i)
    print('REAL GAPS:', len(real))
sys.exit(1 if real else 0)
