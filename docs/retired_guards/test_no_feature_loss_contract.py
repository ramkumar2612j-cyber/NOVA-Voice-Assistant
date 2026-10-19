"""NO FEATURE LOSS guard: every element id that existed in ui.html at baseline (v6.99 upload, tools/ui_ids_baseline.txt)
must still exist. Adding ids is fine, removing one FAILS. If an id was truly renamed, ADD the new one and keep the old (hidden)."""
import re, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ui = open(os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'ui.html'), encoding='utf8').read()
now = set(re.findall(r'\bid="([^"]+)"', ui)) | set(re.findall(r"""\.id\s*=\s*['"]([^'"]+)['"]""", ui))
base = [l.strip() for l in open(os.path.join(HERE, 'ui_ids_baseline.txt')) if l.strip()]
missing = [i for i in base if i not in now]
print(('PASS' if not missing else 'FAIL') + ' no feature loss: %d baseline ids, missing %s' % (len(base), missing[:20]))
for need in ['hub_b', 'au_open', 'ws_go', 'tr_box', 'c_plus', 'btn', 'quiet', 's_save', 'orb3d', 'u_feed', 'gh_save']:
    ok = need in now
    print(('PASS' if ok else 'FAIL') + ' id ' + need); missing += [] if ok else [need]
sys.exit(1 if missing else 0)
