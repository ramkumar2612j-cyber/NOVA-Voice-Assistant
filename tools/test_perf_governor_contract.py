#!/usr/bin/env python3
"""v7.37 Part 2: performance governor in ui.html (script id nova-perf-v737).
STATIC checks always run. RUNTIME checks need playwright + Chromium (headless, fake Android bridge, SwiftShader):
if they are not available the script prints 'RUNTIME: NOT TESTED' and still exits 0 -- read the output, a skipped runtime is not a pass.
Nothing here runs on a real phone."""
import os, re, sys, json
HERE = os.path.dirname(os.path.abspath(__file__))
UI = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'ui.html')
src = open(UI, encoding='utf-8').read()
fails = []
def need(c, m):
    if not c: fails.append(m)

m = re.search(r'<script id="nova-perf-v737">(.*?)</script>', src, re.S)
need(src.count('id="nova-perf-v737"') == 1 and m, 'governor script missing / duplicated')
js = m.group(1) if m else ''
need('id="perf-v737"' in src, 'governor style missing')
need(src.rstrip().endswith('</body></html>'), 'page must still end with </body></html>')
need(src.index('nova-perf-v737') < src.rindex('</body>'), 'governor must sit inside <body>')
for k in ('requestAnimationFrame', 'PerformanceObserver', 'longtask', 'visibilitychange', 'webglcontextlost', 'cancelAnimationFrame', 'data-perf', 'NovaPerf', 'nova_perf'):
    need(k in js, 'governor lacks ' + k)
need(not re.search(r'\bAndroid\s*\.', js) and 'Android[' not in js, 'governor must not call the Android bridge')
need('setInterval' not in js, 'governor must not add a timer loop (rAF only)')
need('innerHTML' not in js and 'eval(' not in js and 'document.write' not in js, 'unsafe DOM API in governor')
need(js.count('try{') >= 6, 'storage / observer / dispatch calls must be wrapped in try')
# existing hooks it relies on must still exist
need("cv.addEventListener('webglcontextlost'" in src and "function fallback(){window.__orb3d=false" in src, 'orb3d-run webglcontextlost hook changed')
need('id="orb3d"' in src, '#orb3d canvas missing')
print('STATIC:', 'PASS' if not fails else 'FAIL')

def runtime():
    try:
        from playwright.sync_api import sync_playwright
    except Exception as e:
        print('RUNTIME: NOT TESTED (playwright missing: %s)' % e); return
    FAKE = "window.Android=new Proxy({},{get:function(t,k){return function(){if(k==='getTier')return window.__fakeTier||'';return ''}}});"
    try:
        pw = sync_playwright().start()
        b = pw.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
    except Exception as e:
        print('RUNTIME: NOT TESTED (no browser: %s)' % str(e)[:120]); return
    url = 'file://' + os.path.abspath(UI)
    def page(tier='', q=None, stored=None):
        ctx = b.new_context(viewport={'width': 412, 'height': 800})
        pg = ctx.new_page(); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e)))
        init = FAKE + "window.__fakeTier=%s;" % json.dumps(tier)
        init += "try{localStorage.setItem('nova_q_mig1','1');localStorage.setItem('nova_q',%s)}catch(e){}" % json.dumps(q or 'auto')   # v7.113: default is SMOOTH now
        if stored: init += "try{localStorage.setItem('nova_perf',%s)}catch(e){}" % json.dumps(json.dumps(stored))
        pg.add_init_script(init)
        pg.goto(url); pg.wait_for_function("window.NovaPerf", timeout=20000)
        return ctx, pg, errs
    def chk(c, m): 
        if not c: fails.append('RUNTIME ' + m)
    # 1 start levels by phone class
    for tier, lo, hi in (('high', 1, 1), ('mid', 2, 2), ('low', 3, 3)):
        ctx, pg, errs = page(tier)
        pg.evaluate("NovaPerf._halt()")
        lv = pg.evaluate("NovaPerf.level()"); attr = pg.evaluate("document.documentElement.getAttribute('data-perf')")
        chk(lo <= lv <= hi and attr == str(lv), 'tier %s start level %s attr %s' % (tier, lv, attr))
        if tier == 'low': chk(pg.evaluate("document.documentElement.classList.contains('lowfx')"), 'low tier lacks lowfx')
        chk(not errs, 'page errors %s' % errs[:2]); ctx.close()
    # 2 downgrade on jank, deterministic through _step; cooldown; no downgrade on single stall
    ctx, pg, errs = page('high'); pg.evaluate("NovaPerf._halt()")
    r = pg.evaluate("""(function(){var t=performance.now()+10000,o=[];
      NovaPerf._step(900,t);t+=900;o.push(NovaPerf.level());                       // one stall alone: stay
      for(var i=0;i<60;i++){t+=40;NovaPerf._step(40,t)}o.push(NovaPerf.level());   // 2.4 s at 25 fps: one step down
      for(var i=0;i<60;i++){t+=40;NovaPerf._step(40,t)}o.push(NovaPerf.level());   // inside the 8 s cooldown: no second step
      t+=9000;for(var i=0;i<60;i++){t+=40;NovaPerf._step(40,t)}o.push(NovaPerf.level());
      for(var i=0;i<40;i++){t+=16;NovaPerf._step(16,t)}                            // jank is over: flush the average (jank that continues rightly drops again after the cooldown)
      return o})()""")
    chk(r[0] == 1 and r[1] == 2 and r[2] == 2 and r[3] == 3, 'jank ladder %s (want [1,2,2,3])' % r)
    chk(pg.evaluate("document.documentElement.classList.contains('lowfx')") and pg.evaluate("window.__forceLow===true"), 'level 3 must hold lowfx')
    # 3 climb back only after long smooth time, and not twice more than 2
    r = pg.evaluate("""(function(){var t=performance.now()+100000,o=[];
      for(var i=0;i<400;i++){t+=16;NovaPerf._step(16,t)}o.push(NovaPerf.level());  // smooth but only 6 s: stay
      for(var i=0;i<400;i++){t+=16;NovaPerf._step(16,t)}o.push(NovaPerf.level());  // 12 s smooth and > 25 s since change: up
      return o})()""")
    chk(r[0] == 3 and r[1] == 2, 'climb ladder %s (want [3,2])' % r)
    chk(not errs, 'page errors %s' % errs[:2]); ctx.close()
    # 4 three stalls inside 8 s drop one level
    ctx, pg, errs = page('high'); pg.evaluate("NovaPerf._halt()")
    lv = pg.evaluate("""(function(){var t=performance.now()+10000,o=[];NovaPerf._stall(300,t);t+=1000;NovaPerf._stall(300,t);o.push(NovaPerf.level());t+=1000;NovaPerf._stall(600,t);o.push(NovaPerf.level());return o})()""")
    chk(lv == [1, 2], 'stall rule: 2 light stalls stay, 3rd (weight 2) drops one level; got %s' % lv)
    pg.evaluate("NovaPerf.set('auto')")
    ctx.close()
    # 4b slow frames WITHOUT a long task (GPU-bound phone) count as stalls too; a frame next to a long task is not counted twice
    ctx, pg, errs = page('high'); pg.evaluate("NovaPerf._halt()")
    lv = pg.evaluate("""(function(){var t=performance.now()+10000,o=[];NovaPerf._step(300,t);t+=1000;NovaPerf._step(300,t);t+=1000;NovaPerf._step(300,t);o.push(NovaPerf.level());
      t+=20000;NovaPerf._stall(300,t);NovaPerf._step(300,t+100);o.push(NovaPerf.level());return o})()""")
    chk(lv == [2, 2], 'slow-frame stalls: 3 drop one level, a doubled report counts once; got %s' % lv)
    ctx.close()
    # 5 level 4 fires the orb's own webglcontextlost hook, once the orb is stopped it stays stopped; Sharp never reaches 4; Smooth never below 3
    ctx, pg, errs = page('high'); pg.evaluate("NovaPerf._halt()")
    pg.evaluate("window.__lost=0;document.getElementById('orb3d').addEventListener('webglcontextlost',function(){window.__lost++});NovaPerf.set(4)")
    pg.wait_for_timeout(1500)
    chk(pg.evaluate("window.__lost")>=1, 'level 4 did not fire webglcontextlost on #orb3d')
    chk(pg.evaluate("window.__orb3d!==true"), 'orb still marked as running at level 4')
    chk(pg.evaluate("document.documentElement.classList.contains('perf4')"), 'perf4 class missing')
    chk(pg.evaluate("NovaPerf.set(1);NovaPerf.level()") == 1 and pg.evaluate("NovaPerf.set('auto');NovaPerf.level()") == 1, 'manual set / auto')
    chk(not errs, 'page errors %s' % errs[:2]); ctx.close()
    ctx, pg, errs = page('high', q='sharp'); pg.evaluate("NovaPerf._halt();NovaPerf.set(4)")
    chk(pg.evaluate("NovaPerf.level()") <= 3, 'sharp reached level 4'); ctx.close()
    ctx, pg, errs = page('high', q='smooth'); pg.evaluate("NovaPerf._halt();NovaPerf.set(1)")
    chk(pg.evaluate("NovaPerf.level()") >= 3, 'smooth went below level 3'); ctx.close()
    # 6 learned level is reused, old one is ignored
    import time
    ctx, pg, errs = page('high', stored={'l': 3, 't': int(time.time() * 1000)}); pg.evaluate("NovaPerf._halt()")
    chk(pg.evaluate("NovaPerf.level()") == 3, 'fresh learned level not used'); ctx.close()
    ctx, pg, errs = page('high', stored={'l': 3, 't': int((time.time() - 5 * 86400) * 1000)}); pg.evaluate("NovaPerf._halt()")
    chk(pg.evaluate("NovaPerf.level()") == 1, 'expired learned level used'); ctx.close()
    # 7 hidden: loop stops and does not run, resumes with a fresh baseline
    ctx, pg, errs = page('high')
    pg.wait_for_timeout(600)
    chk(pg.evaluate("NovaPerf.stats().running"), 'loop not running while visible')
    pg.evaluate("Object.defineProperty(document,'hidden',{configurable:true,get:function(){return true}});document.dispatchEvent(new Event('visibilitychange'))")
    f0 = pg.evaluate("NovaPerf.stats().frames"); pg.wait_for_timeout(500); f1 = pg.evaluate("NovaPerf.stats().frames")
    chk(not pg.evaluate("NovaPerf.stats().running") and f0 == f1, 'loop kept running while hidden (%s -> %s)' % (f0, f1))
    pg.evaluate("Object.defineProperty(document,'hidden',{configurable:true,get:function(){return false}});document.dispatchEvent(new Event('visibilitychange'))")
    chk(pg.evaluate("NovaPerf.stats().running"), 'loop did not resume')
    chk(not errs, 'page errors %s' % errs[:2]); ctx.close()
    b.close(); pw.stop()
    print('RUNTIME:', 'PASS (headless Chromium only, not a phone)' if not [f for f in fails if f.startswith('RUNTIME')] else 'FAIL')

if not fails:
    runtime()
for f in fails: print('FAIL:', f)
sys.exit(1 if fails else 0)
