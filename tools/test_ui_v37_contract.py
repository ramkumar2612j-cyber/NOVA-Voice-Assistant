"""v6.18 UI contract: orb shader inlined == tools/orb/orb.frag, tab bar ids, LIMITS/SKILLS bridge calls, fallbacks, launcher assets."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
h = open(os.path.join(R, 'app/src/main/assets/ui.html'), encoding='utf-8').read()
frag = open(os.path.join(R, 'tools/orb/orb.frag'), encoding='utf-8').read().strip()
bad = []
def ok(c, m):
    print(('PASS ' if c else 'FAIL ') + m)
    if not c: bad.append(m)
m = re.search(r'<script type="x-shader/x-fragment" id="orb_fs">\s*(.*?)</script>', h, re.S)
ok(bool(m) and m.group(1).strip() == frag, 'inlined orb shader equals tools/orb/orb.frag')
for i in ['orb3d', 'gl', 'tabs', 't_home', 't_skills', 't_limits', 'chatb', 'gear', 'v_skills', 'v_limits', 'li_reset', 'orb_fs']:
    ok(h.count('id="%s"' % i) == 1, 'id %s exactly once' % i)
ok(h.index('id="t_home"') < h.index('id="t_skills"') < h.index('id="t_limits"'), 'tab order home/skills/limits')
for f in ['getApiUsage', 'setApiLimit', 'resetApiUsage', 'getProviderHealth']:
    ok(('Android.' + f in h) or ("J('" + f + "')" in h), 'UI calls ' + f)
ok("orb_poster.png" in h and os.path.exists(os.path.join(R, 'app/src/main/assets/orb_poster.png')), 'poster referenced and shipped')
ok('window.__orb3d' in h and 'function fallback' in h and "getContext('webgl2'" in h, '2D fallback path present')
ok("limit:-1" not in h and 'publish nahi' in h, 'unknown limit is never invented')
res = os.path.join(R, 'app/src/main/res')
for d in ['mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi']:
    ok(os.path.exists(res + '/drawable-%s/ic_launcher_foreground.png' % d), 'launcher foreground ' + d)
ok(not os.path.exists(res + '/drawable/ic_launcher_foreground.xml'), 'old foreground xml removed (no duplicate resource)')
ok('animated-vector' in open(res + '/drawable/splash_icon.xml').read(), 'splash icon is animated-vector')
print('ALL PASSED' if not bad else 'FAILED %d' % len(bad)); sys.exit(1 if bad else 0)
