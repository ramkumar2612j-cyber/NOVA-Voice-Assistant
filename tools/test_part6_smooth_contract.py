#!/usr/bin/env python3
"""v7.84 SMOOTH-mode check (real ui.html in headless Chrome, fake Android bridge). SMOOTH must keep the live theme still (drawn per state change, not every frame),
must apply at once from Settings (no app restart), and AUTO / SHARP must keep animating as before. Skips with exit 0 if playwright / chromium is missing. NOT a phone test."""
import os, sys, re
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'); UI = os.path.join(R, 'app/src/main/assets/ui.html')
src = open(UI, encoding='utf-8').read()
bad = []
# ---- static: what must exist (add-only) ----
for need in ['id="qsmooth-init-v784"', 'id="qsmooth-v784"', 'id="qsmooth-live-v784"', 'html.qsmooth', 'window.__isSmooth', 'window.__qsGate', 'NovaQuality',
             'id="s_quality"', 'id="p17_q"', 'id="nova-perf-v737"', 'webglcontextlost']:
    if need not in src: bad.append('missing in ui.html: ' + need)
for old in ['value="sharp"', 'value="smooth"', 'value="auto"', "data-v=\"smooth\"", 'floor=qs==="smooth"?3:1']:
    if old not in src: bad.append('old thing removed: ' + old)
try:
    from playwright.sync_api import sync_playwright
except Exception:
    print('static part done; SKIP headless (no playwright)'); print('FAIL' if bad else 'PASS'); sys.exit(1 if bad else 0)
BR = """(function(){var R={getStatus:'{}',getSettings:'{}',getBrowserStatus:'{}',getConnectors:'[]',listChats:'[]',getTier:'high',getAutoReply:'{}',getSelfImproveCfg:'{}',getWaitSec:5};
window.Android=new Proxy({},{get:function(t,k){return function(a,b){ if(k==='running')return true; return R[k]!==undefined?R[k]:''}}})})();"""
COUNT = """(function(){window.__n={gl:0,star:0,core:0,orb2d:0};
var cr=CanvasRenderingContext2D.prototype.clearRect;CanvasRenderingContext2D.prototype.clearRect=function(){var id=this.canvas&&this.canvas.id;if(id==='p4_stars')window.__n.star++;else if(id==='p4_core')window.__n.core++;else if(id==='gl'||id==='orb')window.__n.orb2d++;return cr.apply(this,arguments)};
['WebGL2RenderingContext','WebGLRenderingContext'].forEach(function(c){if(window[c]){var f=window[c].prototype.drawArrays;window[c].prototype.drawArrays=function(){window.__n.gl++;return f.apply(this,arguments)}}})})();"""
def boot(b, q, w=360, h=700):
    ctx = b.new_context(viewport={'width': w, 'height': h}, device_scale_factor=1)
    pg = ctx.new_page()
    pg.add_init_script('try{localStorage.setItem("nova_q","%s")}catch(e){}' % q if q else '')
    pg.add_init_script(BR); pg.add_init_script(COUNT)
    pg.on('pageerror', lambda e: bad.append('%s js error: %s' % (q, str(e)[:150])))
    pg.goto('file://' + os.path.abspath(UI))
    try: pg.wait_for_function('document.body.classList.contains("ready")', timeout=30000)
    except Exception: bad.append('%s never ready' % q); return None
    pg.evaluate('(function(){var u=document.getElementById("uc");if(u)u.classList.remove("on");Object.defineProperty(window,"__cover",{get:function(){return false},set:function(){}})})()')
    return pg
def window_counts(pg, ms=2000):
    pg.evaluate('window.__n={gl:0,star:0,core:0,orb2d:0}'); pg.wait_for_timeout(ms); return pg.evaluate('window.__n')
with sync_playwright() as p:
    try: b = p.chromium.launch(args=['--use-gl=swiftshader', '--enable-unsafe-swiftshader'])
    except Exception: print('SKIP (no chromium)'); sys.exit(0)
    # 1. AUTO (control): live theme animates
    pg = boot(b, 'auto')
    if pg:
        pg.evaluate('window.__forceLow=false;document.documentElement.classList.remove("lowfx")'); pg.wait_for_timeout(1500)
        cls = pg.evaluate('document.documentElement.className')
        if 'qsmooth' in cls: bad.append('AUTO has qsmooth class: ' + cls)
        n = window_counts(pg)
        print('AUTO   2s draws:', n)
        if n['star'] < 20 or n['core'] < 20: bad.append('AUTO live theme not animating (control broken): %s' % n)
        # 2. live switch AUTO -> SMOOTH from the Settings select (no restart)
        pg.evaluate('(function(){var s=document.getElementById("s_quality");s.value="smooth";s.dispatchEvent(new Event("change",{bubbles:true}))})()'); pg.wait_for_timeout(1500)
        if 'qsmooth' not in pg.evaluate('document.documentElement.className'): bad.append('live switch to SMOOTH did not set qsmooth')
        n = window_counts(pg)
        print('LIVE->SMOOTH 2s draws:', n)
        if n['star'] > 6 or n['core'] > 6 or n['gl'] > 8: bad.append('live SMOOTH still draws too often: %s' % n)
        # state change must still repaint once (listening colour / text)
        pg.evaluate('window.__n={gl:0,star:0,core:0,orb2d:0};document.getElementById("app").className="wrap listen"'); pg.wait_for_timeout(900)
        n = pg.evaluate('window.__n')
        if n['core'] < 1: bad.append('SMOOTH did not repaint the core on state change: %s' % n)
        if pg.evaluate('window.__p4.state()') != 'LISTENING': bad.append('SMOOTH state text wrong')
        # 3. live switch SMOOTH -> SHARP removes qsmooth
        pg.evaluate('(function(){var s=document.getElementById("s_quality");s.value="sharp";s.dispatchEvent(new Event("change",{bubbles:true}))})()'); pg.wait_for_timeout(600)
        if 'qsmooth' in pg.evaluate('document.documentElement.className'): bad.append('switching away from SMOOTH kept qsmooth')
        if pg.evaluate('localStorage.getItem("nova_q")') != 'sharp': bad.append('nova_q not saved')
        pg.context.close()
    # 4. start in SMOOTH (saved choice)
    pg = boot(b, 'smooth')
    if pg:
        pg.wait_for_timeout(1500)
        if 'qsmooth' not in pg.evaluate('document.documentElement.className'): bad.append('SMOOTH start: qsmooth missing')
        n = window_counts(pg)
        print('SMOOTH 2s draws:', n)
        if n['star'] > 6 or n['core'] > 6 or n['gl'] > 8 or n['orb2d'] > 8: bad.append('SMOOTH start draws too often: %s' % n)
        anim = pg.evaluate('(function(){var n=0,out=[];document.querySelectorAll("*").forEach(function(e){var cs=getComputedStyle(e);if(cs.animationName!=="none"&&cs.animationIterationCount==="infinite"&&cs.display!=="none"&&e.offsetParent!==null){n++;out.push((e.id||e.className||e.tagName)+":"+cs.animationName)}});return out})()')
        print('SMOOTH visible infinite CSS animations:', anim)
        heavy = [a for a in anim if re.search(r'nvSpin|orbf|breathe|futspin|futorbit|c3|ping|dotp', a)]
        if heavy: bad.append('SMOOTH heavy decorative CSS loops still running: %s' % heavy)
        # nothing broken: tabs / panels still switch
        errs = pg.evaluate('typeof window.NovaQuality')
        if errs != 'object': bad.append('NovaQuality missing')
        pg.context.close()
    b.close()
print('FAIL' if bad else 'PASS')
for m in bad: print(' -', m)
sys.exit(1 if bad else 0)
