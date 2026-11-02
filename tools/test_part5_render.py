#!/usr/bin/env python3
"""v7.34 PART 5 headless check of the p5_log script (fake Android bridge records uiLog calls). Skips with exit 0 if playwright/chromium missing. NOT a phone test."""
import os, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'); UI = os.path.join(R, 'app/src/main/assets/ui.html')
try:
    from playwright.sync_api import sync_playwright
except Exception:
    print('SKIP (no playwright)'); sys.exit(0)
BR = """(function(){var R={getStatus:'{}',getSettings:'{}',getBrowserStatus:'{}',getConnectors:'[]',listChats:'[]',getTier:'high',getAutoReply:'{}',getSelfImproveCfg:'{}',getWaitSec:5};
window.__ui=[];
window.Android=new Proxy({},{get:function(t,k){return function(a,b){
 if(k==='uiLog'){window.__ui.push([String(a),String(b)]);return 'ok'}
 if(k==='detectIntent')return JSON.stringify({kind:'NONE'});
 if(k==='running')return true;
 return R[k]!==undefined?R[k]:''}}})})();"""
bad = []
with sync_playwright() as p:
    try: b = p.chromium.launch()
    except Exception: print('SKIP (no chromium)'); sys.exit(0)
    pg = b.new_context(viewport={'width': 360, 'height': 700}).new_page(); pg.add_init_script(BR)
    errs = []
    pg.on('pageerror', lambda e: errs.append(str(e)[:150]))
    pg.goto('file://' + os.path.abspath(UI))
    try: pg.wait_for_function('document.body.classList.contains("ready")', timeout=25000)
    except Exception: print('FAIL never ready'); sys.exit(1)
    ev = pg.evaluate
    # p4 / lowfx lines come by themselves after ~4 s
    pg.wait_for_timeout(5000)
    tags = ev('window.__ui.map(function(x){return x[0]})')
    if 'p4' not in tags: bad.append('no p4 line after load: %s' % tags)
    if 'lowfx' not in tags: bad.append('no lowfx line after load: %s' % tags)
    # (a) website card line + rate cap (2 cards in a row = 1 line)
    ev('window.__ui.length=0')
    pg.wait_for_timeout(10200)   # clear the 10 s window of the page
    ev('(function(){for(var i=0;i<2;i++){var m=document.createElement("div");m.className="msg p3_site";document.getElementById("clog").appendChild(m)}})()')
    pg.wait_for_timeout(300)
    n = ev('window.__ui.filter(function(x){return x[0]==="p3"}).length')
    if n != 1: bad.append('p3 card line count %s (want 1, rate cap)' % n)
    # (b) photo edit lines
    ev('(function(){var m=document.createElement("div");m.className="msg ai";m.textContent="Edit ho raha hai...";document.getElementById("clog").appendChild(m)})()')
    pg.wait_for_timeout(300)
    if ev('window.__ui.filter(function(x){return x[0]==="photo_edit"}).length') != 1: bad.append('photo_edit line missing')
    # (c) lowfx flip
    ev('window.__ui.length=0')
    pg.wait_for_timeout(10200)
    ev('document.documentElement.classList.toggle("lowfx")'); pg.wait_for_timeout(300)
    if ev('window.__ui.filter(function(x){return x[0]==="lowfx"}).length') != 1: bad.append('lowfx flip line missing')
    # (d) JS error line
    ev('setTimeout(function(){throw new Error("p5 test boom")},0)'); pg.wait_for_timeout(400)
    if ev('window.__ui.filter(function(x){return x[0]==="js_error"}).length') != 1: bad.append('js_error line missing')
    # (e) privacy: the typed sentence never appears in any line
    secret = 'mera gupt vaakya 9876543210'
    ev('(function(){var i=document.getElementById("c_in");if(i){i.value="%s"}})()' % secret)
    pg.wait_for_timeout(300)
    allm = ' '.join(a + ' ' + b for a, b in ev('window.__ui'))
    if 'gupt' in allm or '9876543210' in allm: bad.append('typed sentence leaked into uiLog')
    real = [e for e in errs if 'p5 test boom' not in e]
    if real: bad.append('page errors: %s' % real[:2])
    b.close()
if bad:
    print('FAIL part5 render'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS part5 render (p4, lowfx, p3 card, photo_edit, js_error lines, rate cap, privacy)')
