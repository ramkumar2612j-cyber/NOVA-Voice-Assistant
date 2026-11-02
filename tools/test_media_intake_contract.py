#!/usr/bin/env python3
"""Part 11-A contract: MediaIntake.kt stays PURE (no Android, no network, no file access), limits are the owner's numbers,
JUnit file exists, and a small Python mirror re-checks the link / size / name rules. Wiring (manifest, store, bridge, UI) is 11-B..11-D."""
import os, re, sys
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
def rd(p): return open(os.path.join(R, p), encoding='utf-8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)
mi = rd('app/src/main/java/com/nova/assistant/MediaIntake.kt')
need(os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/MediaIntakeTest.kt')), 'MediaIntakeTest.kt missing')
need(not re.search(r'^\s*import\s+(android|java\.io|java\.net|org\.json)|HttpURLConnection|File\(|openInputStream|ContentResolver|Context\b', mi, re.M), 'MediaIntake must stay pure Kotlin (no Android / io / network)')
need('PersonaRules' not in mi.replace('Never touches PersonaRules', ''), 'MediaIntake must not call PersonaRules')
need('MAX_PHOTO_BYTES = 25L * 1024 * 1024' in mi and 'MAX_VIDEO_BYTES = 300L * 1024 * 1024' in mi, 'size limits must be 25 MB photo / 300 MB video')
need('MAX_FILES = 6' in mi and 'TTL_MS = 24L * 60 * 60 * 1000' in mi, 'file cap 6 / 24 h rule missing')
need('"content://"' in mi.lower() or 'content://' in mi, 'content:// only rule missing')
need('lowercase().startsWith("https://")' in mi, 'https-only link rule missing')
need('Regex("https://' in mi and 'http://' not in re.sub(r'//.*', '', mi.split('fun isSafeLink')[0].split('fun extractUrls')[1] if 'fun extractUrls' in mi else ''), 'extractUrls must match https only')
# 11-A does not touch the wiring yet (that is 11-B..D) but must not break what exists
need(os.path.exists(os.path.join(R, 'app/src/main/AndroidManifest.xml')), 'manifest missing')
# ---- mirror
MB = 1024 * 1024
def human(b):
    if b < 0: return '?'
    if b < 1024: return '%d B' % b
    kb = b / 1024.0
    if kb < 1024: return '%.1f KB' % (int(kb * 10 + 0.5) / 10.0)
    mb = kb / 1024.0
    if mb < 1024: return '%.1f MB' % (int(mb * 10 + 0.5) / 10.0)
    return '%.1f GB' % (int(mb / 1024.0 * 10 + 0.5) / 10.0)
for b, e in [(0, '0 B'), (1023, '1023 B'), (1024, '1.0 KB'), (1536, '1.5 KB'), (25 * MB, '25.0 MB'), (300 * MB, '300.0 MB'), (1024 * MB, '1.0 GB')]:
    need(human(b) == e, 'mirror humanSize(%d)=%s expected %s' % (b, human(b), e))
def safe_link(u):
    if len(u) > 2000 or not u.lower().startswith('https://'): return False
    auth = re.split(r'[/?#]', u[8:], maxsplit=1)[0]
    if not auth or '@' in auth: return False
    host = auth.split(':')[0].lower().rstrip('.')
    if len(host) < 4 or '.' not in host or not re.fullmatch(r'[a-z0-9.\-]+', host): return False
    if host == 'localhost' or host.endswith(('.local', '.internal', '.localhost')): return False
    if re.fullmatch(r'[0-9.]+', host): return False
    return not (host.startswith(('.', '-')) or '..' in host)
for u, e in [('https://www.instagram.com/reel/A/?igsh=x', True), ('http://example.com', False), ('https://user:pw@example.com/x', False), ('https://localhost/x', False),
             ('https://192.168.1.1/x', False), ('https://intranet.local/x', False), ('https://singleword/x', False), ('https://example.com:8443/p', True), ('https://' + 'a' * 2000 + '.com', False)]:
    need(safe_link(u) == e, 'mirror isSafeLink(%r) expected %r' % (u, e))
if bad:
    print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS media intake 11-A contract (static purity + mirror; real JUnit is MediaIntakeTest.kt)')
