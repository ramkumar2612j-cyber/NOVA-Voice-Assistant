#!/usr/bin/env python3
"""v7.113: fresh install = SMOOTH; in SMOOTH no requestAnimationFrame wake-ups; AUTO / SHARP still animate. Headless chromium, fake bridge. NOT a phone test."""
import os, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'); UI = os.path.join(R, 'app/src/main/assets/ui.html')
try:
    from playwright.sync_api import sync_playwright
except Exception:
    print('SKIP (no playwright)'); sys.exit(0)
BR = """(function(){var R={getStatus:'{}',getSettings:'{}',getBrowserStatus:'{}',getConnectors:'[]',listChats:'[]',getTier:'high',getAutoReply:'{}',getSelfImproveCfg:'{}',getWaitSec:5};
window.Android=new Proxy({},{get:function(t,k){return function(a,b){if(k==='detectIntent')return JSON.stringify({kind:'NONE'});if(k==='running')return true;return R[k]!==undefined?R[k]:''}}})})();
window.__raf=0;var r=window.requestAnimationFrame;window.requestAnimationFrame=function(f){return r.call(window,function(t){window.__raf++;return f(t)})};"""
bad = []
def run(b, pre, name):
    ctx = b.new_context(viewport={'width': 412, 'height': 800}); pg = ctx.new_page(); errs = []
    pg.on('pageerror', lambda e: errs.append(str(e)[:120]))
    if pre: pg.add_init_script(pre)
    pg.add_init_script(BR); pg.goto('file://' + os.path.abspath(UI))
    try: pg.wait_for_function('document.body.classList.contains("ready")', timeout=25000)
    except Exception: bad.append(name + ' never ready')
    pg.wait_for_timeout(2500); n0 = pg.evaluate('window.__raf'); pg.wait_for_timeout(3000); n1 = pg.evaluate('window.__raf')
    q = pg.evaluate('localStorage.getItem("nova_q")'); sm = pg.evaluate('document.documentElement.classList.contains("qsmooth")')
    ctx.close()
    if errs: bad.append(name + ' js errors: %s' % errs[:2])
    return (n1 - n0) / 3.0, q, sm
with sync_playwright() as p:
    try: b = p.chromium.launch()
    except Exception: print('SKIP (no chromium)'); sys.exit(0)
    r, q, sm = run(b, None, 'fresh');                                   print('fresh install: raf/s=%.1f q=%s smooth=%s' % (r, q, sm))
    if not sm or q != 'smooth': bad.append('fresh install is not SMOOTH')
    if r > 5: bad.append('SMOOTH still wakes up %.1f rAF/s' % r)
    r, q, sm = run(b, 'try{localStorage.setItem("nova_q","auto")}catch(e){}', 'old-auto');  print('old stored AUTO migrates once: q=%s smooth=%s' % (q, sm))
    if not sm: bad.append('stored AUTO was not migrated to SMOOTH')
    r, q, sm = run(b, 'try{localStorage.setItem("nova_q_mig1","1");localStorage.setItem("nova_q","sharp")}catch(e){}', 'sharp'); print('SHARP: raf/s=%.1f smooth=%s' % (r, sm))
    if sm: bad.append('SHARP wrongly smooth')
    if r < 20: bad.append('SHARP does not animate (%.1f rAF/s)' % r)
    b.close()
print('FAIL\n - ' + '\n - '.join(bad) if bad else 'PASS'); sys.exit(1 if bad else 0)
