#!/usr/bin/env python3
"""AUTOPSY PART 1 wiring audit (static, no compile). Fails when a feature is written but not wired:
 1. every A('x') / Android.x() call in assets/ui.html has a @JavascriptInterface fun x in MainActivity.kt
 2. every @JavascriptInterface fun in MainActivity.kt is referenced by name in ui.html (no invisible feature)
 3. every command kind Logic.kt can produce has an executor branch in NovaService.kt or runCommand
 4. every manifest component class exists as Kotlin source
 5. every element hidden by default in ui.html is referenced by JS (can be shown)"""
import re, sys, glob, os
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
M = os.path.join(R, 'app/src/main')
J = os.path.join(M, 'java/com/nova/assistant')
h = open(os.path.join(M, 'assets/ui.html'), encoding='utf8').read()
k = open(os.path.join(J, 'MainActivity.kt'), encoding='utf8').read()
svc = open(os.path.join(J, 'NovaService.kt'), encoding='utf8').read()
lg = open(os.path.join(J, 'Logic.kt'), encoding='utf8').read()
man = open(os.path.join(M, 'AndroidManifest.xml'), encoding='utf8').read()
bad = []
bm = set(re.findall(r'@JavascriptInterface\s+fun\s+(\w+)', k))
called = set(re.findall(r"\bA\(\s*['\"](\w+)['\"]", h)) | set(re.findall(r"\bAndroid\.(\w+)\s*\(", h)) | set(re.findall(r"Android\[\s*['\"](\w+)['\"]", h))
for n in sorted(called - bm): bad.append('dead button, no Kotlin bridge: ' + n)
for n in sorted(x for x in bm if x not in h): bad.append('Kotlin feature with no UI reference: ' + n)
kinds = set(re.findall(r'Cmd\(\s*"([a-z_]+)"', lg)) | set(re.findall(r'kind\s*=\s*"([a-z_]+)"', lg))
for n in sorted(kinds):
    if '"%s" ->' % n not in svc and ('"%s"' % n) not in svc: bad.append('command kind with no executor: ' + n)
srcs = '\n'.join(open(f, encoding='utf8').read() for f in glob.glob(J + '/*.kt'))
for nm in re.findall(r'<(?:activity|service|receiver)\b[^>]*?android:name="([^"]+)"', man):
    short = nm.split('.')[-1]
    if nm.startswith('.') or nm.startswith('com.nova.assistant'):
        if not re.search(r'\b(class|object)\s+' + short + r'\b', srcs): bad.append('manifest class missing: ' + nm)
for tag, i in re.findall(r'<(\w+)[^>]*\bid="([^"]+)"[^>]*style="[^"]*display:\s*none', h):
    if not re.search(r'[\'"#]' + re.escape(i) + r'[\'"]', h): bad.append('hidden forever: ' + i)
if bad:
    print('FAIL wiring audit'); [print(' -', b) for b in bad]; sys.exit(1)
print('OK wiring audit: %d UI calls, %d bridge funs, %d command kinds' % (len(called), len(bm), len(kinds)))
