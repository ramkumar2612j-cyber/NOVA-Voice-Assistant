#!/usr/bin/env python3
"""v7.69/7.70 PART 18 HANG PROTECTION = SAFE MODE (static contract + headless). Evidence: static + headless chromium, NOT phone tested.
1 CrashGuard.kt rules + store exist; crash handler records crashes; NovaService skips the laser in safe mode
2 bridge getSafeMode / exitSafeMode in Kotlin and BRIDGE; pt18_safe in SCR; help topic safemode
3 headless: safe mode ON shows the banner + lowfx, button calls exitSafeMode and removes the banner; safe mode OFF shows nothing"""
import os, re, sys
H = os.path.dirname(os.path.abspath(__file__)); R = os.path.join(H, '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
KT = 'app/src/main/java/com/nova/assistant/'
cg, cn, ns, ma, hg, ui = rd(KT+'CrashGuard.kt'), rd(KT+'CrashNote.kt'), rd(KT+'NovaService.kt'), rd(KT+'MainActivity.kt'), rd(KT+'HelpGuide.kt'), rd('app/src/main/assets/ui.html')
bad = []
def need(c, m):
    print(('PASS ' if c else 'FAIL ') + m)
    if not c: bad.append(m)
need('const val THRESHOLD = 2' in cg and 'WINDOW_MS = 10 * 60_000L' in cg and 'SAFE_HOLD_MS = 30 * 60_000L' in cg, 'rules: 2 crashes / 10 min => 30 min')
need('.commit()' in cg, 'crash time written with commit() (process is dying)')
need('CrashGuardStore.recordCrash(app)' in cn and cn.index('recordCrash') < cn.index('prev.uncaughtException'), 'crash handler records the crash BEFORE the normal handling')
need('CrashGuardStore.isSafe(this)) return' in ns, 'NovaService.startLaser skips the overlay in safe mode')
need('fun getSafeMode(): Boolean' in ma and 'fun exitSafeMode(): String' in ma, 'Kotlin bridge getSafeMode / exitSafeMode')
br = re.search(r'var BRIDGE=(\[.*?\]);', ui, re.S).group(1)
need('"getSafeMode"' in br and '"exitSafeMode"' in br, 'BRIDGE lists both')
need("'pt18_safe'" in ui and 'id="pt18_safe"' in ui, 'pt18_safe in SCR and present')
need('Topic("safemode"' in hg and '"id": "safemode"' in ui, 'help topic safemode in Kotlin and ui.html')
need(os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/CrashGuardTest.kt')), 'CrashGuardTest.kt present')
def bridge(safe):
    return """(function(){window.__c=[];var R={getStatus:'{}',getSettings:'{}',getBrowserStatus:'{}',getConnectors:'[]',listChats:'[]',getTier:'high',getAutoReply:'{}',getSelfImproveCfg:'{}',getWaitSec:5};
window.Android=new Proxy({},{get:function(t,k){return function(a){if(k==='getSafeMode')return %s;if(k==='exitSafeMode'){window.__c.push('exit');return 'ok'}
 return R[k]!==undefined?R[k]:''}}})})();""" % ('true' if safe else 'false')
try:
    from playwright.sync_api import sync_playwright
except Exception:
    print('SKIP headless'); sync_playwright = None
if sync_playwright:
    with sync_playwright() as p:
        try: b = p.chromium.launch()
        except Exception: print('SKIP headless (no chromium)'); b = None
        if b:
            for safe in (True, False):
                pg = b.new_context(viewport={'width': 320, 'height': 700}).new_page(); pg.add_init_script(bridge(safe)); errs = []
                pg.on('pageerror', lambda e: errs.append(str(e)[:150]))
                pg.goto('file://' + os.path.abspath(os.path.join(R, 'app/src/main/assets/ui.html')))
                pg.wait_for_function('document.body.classList.contains("ready")', timeout=25000); pg.wait_for_timeout(500)
                has = pg.evaluate('!!document.getElementById("pt18_bar")')
                need(has == safe, 'headless safe=%s: banner %s' % (safe, 'shown' if safe else 'absent'))
                if safe:
                    need(pg.evaluate('document.documentElement.classList.contains("lowfx")'), 'headless: lowfx forced')
                    need(pg.evaluate('document.documentElement.scrollWidth<=321'), 'headless: no horizontal scroll at 320 px')
                    pg.evaluate('document.getElementById("pt18_exit").click()')
                    need(pg.evaluate('window.__c').count('exit') == 1 and not pg.evaluate('!!document.getElementById("pt18_bar")'), 'headless: button calls exitSafeMode and removes banner')
                need(not errs, 'headless: no page error %s' % errs)
if bad: print('FAILED', len(bad)); sys.exit(1)
print('PASS part18 contract')
