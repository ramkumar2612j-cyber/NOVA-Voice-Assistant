#!/usr/bin/env python3
"""v7.79 PART 3A contract: core orb centering fix present, add-only (old rule kept), headless geometry check."""
import re, sys, os
p = "app/src/main/assets/ui.html"
s = open(p, encoding="utf-8").read()
bad = []
def need(c, m):
    if not c: bad.append(m)
need(".orbbox canvas{height:100%!important;width:auto!important;max-width:100%!important;aspect-ratio:1/1;object-fit:contain;position:relative;z-index:1}" in s, "old orb canvas rule must stay (add-only)")
need(".orbbox #p4_core{position:absolute!important;left:50%!important;top:50%!important" in s, "v7.79 orb centering rule missing")
_vn = re.search(r'versionName = "7\.(\d+)"', open("app/build.gradle.kts", encoding="utf-8").read())
need(_vn is not None and int(_vn.group(1)) >= 79, "versionName must be >= 7.79")
if os.environ.get("NOVA_HEADLESS") == "1":
    sh = open("tools/ui_shots.py", encoding="utf-8").read()
    FAKE = re.search(r'FAKE = r"""(.*?)"""', sh, re.S).group(1)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as pw:
        b = pw.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
        for w, h in [(320, 568), (360, 740), (412, 915)]:
            pg = b.new_context(viewport={'width': w, 'height': h}).new_page(); pg.add_init_script(FAKE)
            pg.goto('file://' + os.path.abspath(p)); pg.wait_for_function("document.body.classList.contains('ready')", timeout=20000); pg.wait_for_timeout(1500)
            d = pg.evaluate("()=>{const c=document.getElementById('p4_core').getBoundingClientRect(),b=document.querySelector('.orbbox').getBoundingClientRect();return Math.abs((c.left+c.width/2)-(b.left+b.width/2))}")
            need(d < 2, f"orb off-centre by {d}px at {w}x{h}")
        b.close()
print("PASS test_part3a_contract" if not bad else "FAIL: " + "; ".join(bad))
sys.exit(1 if bad else 0)
