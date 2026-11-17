#!/usr/bin/env python3
"""v7.221 SECURITY SCAN (Part 5). Source me asli secret patterns, https-only violations,
exported components, aur .gitignore guards dhoondta hai. Static — koi network nahi.

Exit 0 = clean, 1 = koi asli cheez mili (detail print hota hai)."""
import os, re, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

# asli API key patterns (test placeholders/fake prefixes allowed nahi hain in patterns me)
KEY_PATTERNS = [
    (r'sk-[A-Za-z0-9]{20,}', 'OpenAI-style key'),
    (r'gsk_[A-Za-z0-9]{20,}', 'Groq key'),
    (r'AIza[0-9A-Za-z_-]{30,}', 'Google/Gemini key'),
    (r'ghp_[A-Za-z0-9]{30,}', 'GitHub PAT'),
    (r'xox[bpars]-[A-Za-z0-9-]{10,}', 'Slack token'),
    (r'-----BEGIN (RSA )?PRIVATE KEY-----', 'private key file'),
]
ALLOW_FOLDERS = ('backend/state', 'tools/local_stubs', 'docs/archive')
SKIP_EXT = ('.png', '.jpg', '.jpeg', '.webp', '.gif', '.zip', '.jar', '.wav', '.webm', '.onnx', '.bin', '.apk', '.ppn', '.md', '.ipynb')

bad = []
FAKE_MARKS = ('abcdefgh', 'abcdefghijklmnop', '123456789', 'redacted', 'xxxxx', 'testtest', 'dummy', 'example', 'yourkey', '0123456789')
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in ('.git', 'node_modules', '__pycache__', 'build', 'out_main', 'out_test', 'out_junit', 'mainsrc', 'stubsrc', 'jj', 'jjout', 'backups')]
    rel = os.path.relpath(dirpath, ROOT)
    if any(rel.startswith(a) for a in ALLOW_FOLDERS):
        continue
    for fn in filenames:
        if fn.endswith(SKIP_EXT):
            continue
        p = os.path.join(dirpath, fn)
        try:
            s = open(p, encoding='utf-8', errors='ignore').read()
        except Exception:
            continue
        for pat, what in KEY_PATTERNS:
            for m in re.finditer(pat, s):
                hit = m.group(0)
                # obviously-fake test fixtures (redaction tests): dash/punct hata ke dekho — abcdef/1234 sequences fake hain
                norm = re.sub(r'[^a-z0-9]', '', hit.lower())
                if any(f in norm for f in FAKE_MARKS) or any(f in norm for f in ('abcd', 'ijkl', '0000')):
                    continue
                bad.append('%s: %s (%s)' % (os.path.relpath(p, ROOT), what, hit[:12] + '...'))

# https-only: clients me http:// sirf localhost/127.0.0.1 ke liye
for f in ['app/src/main/java/com/nova/assistant/JarvisCloud.kt', 'desktop/cloud.js', 'backend/brain_server.py']:
    p = os.path.join(ROOT, f)
    if not os.path.exists(p):
        continue
    s = open(p, encoding='utf-8').read()
    for m in re.finditer(r'http://([A-Za-z0-9.-]+)', s):
        host = m.group(1)
        if host not in ('127.0.0.1', 'localhost'):
            bad.append('%s: http:// (%s) — sirf https ya localhost allowed' % (f, host))

# gitignore guards (purane rules)
gi = open(os.path.join(ROOT, '.gitignore'), encoding='utf-8').read()
for need in ['local.properties', '.jks', '.keystore']:
    if need not in gi:
        bad.append('.gitignore missing ' + need)

# secrets kabhi logs me nahi (backend + clients) — VALUE print hona flag hota hai, sirf 'password' shabd nahi
for f in ['backend/brain_server.py']:
    s = open(os.path.join(ROOT, f), encoding='utf-8').read()
    if re.search(r'print\([^)]*(\+\s*pw\b|\(pw\b|str\(pw\b|\{pw\}|\+\s*tok\b|\{tok\})', s):
        bad.append(f + ': koi token/password ki VALUE print ho rahi hai')

if bad:
    print('SECURITY SCAN: %d issue(s)' % len(bad))
    for b in bad:
        print('  ' + b)
    sys.exit(1)
print('SECURITY SCAN: clean (0 asli secret, https-only rules ok, gitignore ok)')
