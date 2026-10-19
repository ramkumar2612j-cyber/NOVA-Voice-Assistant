#!/usr/bin/env python3
"""v7.80 PART 3B contract: short-phone Home rule present + add-only, headless geometry (orb >= 140px, centred, rows reachable, no side overflow)."""
import re, sys, os
p = "app/src/main/assets/ui.html"
s = open(p, encoding="utf-8").read()
bad = []
def need(c, m):
    if not c: bad.append(m)
need("@media(max-height:520px){.orbbox{display:none}}" in s, "old <=520px rule must stay (add-only)")
need("@media(max-height:640px){.logo{font-size:18px}.orbbox{min-height:92px}" in s, "old <=640px rule must stay (add-only)")
need(".orbbox #p4_core{position:absolute!important;left:50%!important;top:50%!important" in s, "v7.79 centring rule must stay")
need("@media(max-height:640px) and (min-height:521px){#app.wrap{overflow-y:auto!important" in s, "v7.80 short-phone rule missing")
need("#app.wrap>.orbbox{flex:0 0 150px!important;min-height:150px!important}" in s, "v7.80 orb min size missing")
_vn = re.search(r'versionName = "7\.(\d+)"', open("app/build.gradle.kts", encoding="utf-8").read())
need(_vn is not None and int(_vn.group(1)) >= 80, "versionName must be >= 7.80")
if os.environ.get("NOVA_HEADLESS") == "1":
    sh = open("tools/ui_shots.py", encoding="utf-8").read()
    FAKE = re.search(r'FAKE = r"""(.*?)"""', sh, re.S).group(1)
    from playwright.sync_api import sync_playwright
    JS = """()=>{const q=s=>document.querySelector(s),r=e=>e.getBoundingClientRect(),app=q('#app'),o=r(q('.orbbox')),c=r(q('#p4_core'));
    const kids=[...app.children].filter(k=>getComputedStyle(k).display!=='none');const last=kids.reduce((m,k)=>Math.max(m,r(k).bottom),0);
    const before=app.scrollTop;app.scrollTop=app.scrollHeight;const after=app.scrollTop;app.scrollTop=before;
    return {orbH:o.height,dx:Math.abs((c.left+c.width/2)-(o.left+o.width/2)),canScroll:app.scrollHeight>app.clientHeight,scrolled:after,sx:document.documentElement.scrollWidth>innerWidth,fits:app.scrollHeight<=app.clientHeight+1}}"""
    with sync_playwright() as pw:
        b = pw.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
        for w, h in [(320, 568), (360, 640), (360, 740), (412, 915)]:
            pg = b.new_context(viewport={'width': w, 'height': h}).new_page(); pg.add_init_script(FAKE)
            pg.goto('file://' + os.path.abspath(p)); pg.wait_for_function("document.body.classList.contains('ready')", timeout=20000); pg.wait_for_timeout(1500)
            d = pg.evaluate(JS)
            need(not d['sx'], f"side overflow at {w}x{h}")
            need(d['dx'] < 2, f"orb off-centre {d['dx']}px at {w}x{h}")
            if h <= 640:
                need(d['orbH'] >= 140, f"orb squeezed to {d['orbH']}px at {w}x{h}")
                need(d['fits'] or d['scrolled'] > 0, f"rows clipped, Home not scrollable at {w}x{h}")
        b.close()
print("PASS test_part3b_contract" if not bad else "FAIL: " + "; ".join(bad))
sys.exit(1 if bad else 0)
