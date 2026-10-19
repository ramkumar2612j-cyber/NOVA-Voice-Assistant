#!/usr/bin/env python3
"""v7.38 Part 3: native smoothness. STATIC always; RUNTIME (headless Chromium, fake bridge) prints NOT TESTED if playwright is missing -- a skip is not a pass.
Kotlin is only checked statically here (balance, wiring). A real compile / JUnit run is a separate step (tools/local_compile_check.sh) and must be reported separately."""
import os, re, sys, json
HERE = os.path.dirname(os.path.abspath(__file__)); R = os.path.join(HERE, '..')
J = os.path.join(R, 'app/src/main/java/com/nova/assistant'); UI = os.path.join(R, 'app/src/main/assets/ui.html')
rd = lambda p: open(p, encoding='utf-8').read()
ma, ns_, ui = rd(J + '/MainActivity.kt'), rd(J + '/NativeSmooth.kt'), rd(UI)
fails = []
def need(c, m):
    if not c: fails.append(m)
for name, t in (('NativeSmooth.kt', ns_), ('MainActivity.kt', ma)):
    need(t.count('{') == t.count('}') and t.count('(') == t.count(')'), name + ' braces / parens unbalanced')
for k in ('fun pressure(', 'fun shouldSend(', 'fun pressureJs(', 'fun backgroundJs(', 'window.novaNative&&novaNative('):
    need(k in ns_, 'NativeSmooth missing ' + k)
need('android.' not in ns_ and 'import ' not in ns_, 'NativeSmooth must stay pure (no Android / imports) so JUnit can run it')
need(len(re.findall(r'override fun onTrimMemory\(', ma)) == 1 and len(re.findall(r'override fun onLowMemory\(', ma)) == 1, 'MainActivity needs exactly one onTrimMemory and one onLowMemory')
need('NativeSmooth.pressure(level)' in ma and 'NativeSmooth.pressure(0, true)' in ma and 'NativeSmooth.shouldSend(' in ma, 'MainActivity does not use the pressure rules')
need('NativeSmooth.backgroundJs(true)' in ma and 'NativeSmooth.backgroundJs(false)' in ma, 'MainActivity does not send background / foreground')
need(re.search(r'override fun onPause\(\) \{\s*NovaService\.uiVisible = false\s*runJs\(NativeSmooth\.backgroundJs\(true\)\)', ma) is not None, 'onPause hook position')
need(re.search(r'super\.onResume\(\)\s*NovaService\.uiVisible = true\s*try \{ web\.onResume\(\)', ma) is not None, 'onResume hook position')
need('pauseTimers' not in ma, 'pauseTimers would hold back replies that arrive while the screen is off -- not allowed')
need(os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/NativeSmoothTest.kt')), 'NativeSmoothTest.kt missing')
need(ui.count('id="nova-native-v738"') == 1 and 'W.novaNative=function' in ui and 'pressure:function' in ui, 'page side missing')
need(ui.index('id="nova-native-v738"') < ui.index('<script id="p5_log">'), 'page hook must stay before p5_log (old guards scan the page tail for localStorage)')
need(ui.index('id="nova-perf-v737"') < ui.index('id="nova-native-v738"'), 'native hook must come after the governor')
print('STATIC:', 'PASS' if not fails else 'FAIL')

def runtime():
    try:
        from playwright.sync_api import sync_playwright
        pw = sync_playwright().start(); b = pw.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
    except Exception as e:
        print('RUNTIME: NOT TESTED (%s)' % str(e)[:100]); return
    FAKE = "window.Android=new Proxy({},{get:function(t,k){return function(){return k==='getTier'?'high':''}}});"
    def page():
        ctx = b.new_context(viewport={'width': 412, 'height': 800}); pg = ctx.new_page(); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e))); pg.add_init_script(FAKE)
        pg.goto('file://' + os.path.abspath(UI)); pg.wait_for_function("!!(window.NovaPerf&&window.novaNative)", timeout=60000)
        pg.evaluate("NovaPerf._halt()"); return ctx, pg, errs
    def chk(c, m):
        if not c: fails.append('RUNTIME ' + m)
    ctx, pg, errs = page()
    pg.evaluate("novaNative('bg',1)"); chk(pg.evaluate("document.documentElement.classList.contains('nbg')"), 'bg 1 did not set nbg')
    chk(pg.evaluate("getComputedStyle(document.documentElement).getPropertyValue('--x')||true"), 'style probe')
    pg.evaluate("novaNative('bg',0)"); chk(not pg.evaluate("document.documentElement.classList.contains('nbg')"), 'bg 0 did not clear nbg')
    chk(pg.evaluate("novaNative('mem',1);NovaPerf.level()") == 2, 'light pressure should go 1 -> 2')
    chk(pg.evaluate("novaNative('mem',2);NovaPerf.level()") == 3, 'heavy pressure should reach 3')
    chk(pg.evaluate("document.documentElement.classList.contains('lowfx')"), 'level 3 lacks lowfx')
    chk(pg.evaluate("localStorage.getItem('nova_perf')") is None, 'memory pressure must not be saved as a learned level')
    pg.evaluate("window.__lost=0;document.getElementById('orb3d').addEventListener('webglcontextlost',function(){window.__lost++});novaNative('mem',3)")
    pg.wait_for_timeout(1500)
    chk(pg.evaluate("NovaPerf.level()") == 4 and pg.evaluate("window.__lost") >= 1, 'critical pressure should reach level 4 and fire webglcontextlost')
    chk(pg.evaluate("novaNative('bogus',1);novaNative('mem','x');true"), 'bad input must not throw')
    chk(not errs, 'page errors %s' % errs[:2]); ctx.close()
    ctx, pg, errs = page()   # pinned by Settings: light / heavy ignored, critical still wins
    pg.evaluate("NovaPerf.set(1)"); chk(pg.evaluate("novaNative('mem',2);NovaPerf.level()") == 1, 'pinned level must ignore light / heavy pressure')
    chk(pg.evaluate("novaNative('mem',3);NovaPerf.level()") == 4, 'critical pressure must beat a pin'); ctx.close()
    ctx, pg, errs = page(); pg.evaluate("window.NovaPerf=undefined"); chk(pg.evaluate("novaNative('mem',3);true"), 'novaNative without NovaPerf must not throw'); ctx.close()
    b.close(); pw.stop()
    print('RUNTIME:', 'PASS (headless Chromium only, not a phone)' if not [f for f in fails if f.startswith('RUNTIME')] else 'FAIL')
if not fails: runtime()
for f in fails: print('FAIL:', f)
sys.exit(1 if fails else 0)
