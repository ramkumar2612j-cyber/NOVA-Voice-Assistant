#!/usr/bin/env python3
"""v7.35 headless check of the page side of AUTO VERIFY (fake Android bridge). Two runs: complete bridge = all OK, bridge with 2 missing functions + missing callback = reported. Skips with exit 0 if playwright/chromium missing. NOT a phone test."""
import os, sys, json
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'); UI = os.path.join(R, 'app/src/main/assets/ui.html')
try:
    from playwright.sync_api import sync_playwright
except Exception:
    print('SKIP (no playwright)'); sys.exit(0)
def BR(drop):
    return """(function(){var DROP=%s;var R={getStatus:'{}',getSettings:'{}',getBrowserStatus:'{}',getConnectors:'[]',listChats:'[]',getTier:'high',getAutoReply:'{}',getSelfImproveCfg:'{}',getWaitSec:5};
window.__v=[];window.__now=0;
var A={};
function mk(k){return function(a,b){if(k==='verifyPage'){window.__v.push(a);return 'ok'}if(k==='verifyNow'){window.__now++;return 'started'}
 if(k==='detectIntent')return JSON.stringify({kind:'NONE'});if(k==='running')return true;return R[k]!==undefined?R[k]:''}}
window.Android=new Proxy(A,{get:function(t,k){if(DROP.indexOf(k)>=0)return undefined;return mk(k)}})})();""" % json.dumps(drop)
bad = []
with sync_playwright() as p:
    try: b = p.chromium.launch()
    except Exception: print('SKIP (no chromium)'); sys.exit(0)
    for drop, want_missing in (([], False), (['editPhoto', 'uiLog'], True)):
        pg = b.new_context(viewport={'width': 360, 'height': 700}).new_page(); pg.add_init_script(BR(drop))
        errs = []; pg.on('pageerror', lambda e: errs.append(str(e)[:150]))
        pg.goto('file://' + os.path.abspath(UI))
        try: pg.wait_for_function('document.body.classList.contains("ready")', timeout=25000)
        except Exception: print('FAIL never ready'); sys.exit(1)
        ev = pg.evaluate
        if want_missing: ev('delete window.onSite')
        r = ev('window.p6Verify()')
        v = ev('window.__v')
        if not v: bad.append('page did not answer Android.verifyPage')
        else:
            o = json.loads(v[-1])
            if want_missing:
                if sorted(o['missingBridge']) != ['editPhoto', 'uiLog']: bad.append('missing bridge not found: %s' % o['missingBridge'])
                if o['missingCb'] != ['onSite']: bad.append('missing callback not found: %s' % o['missingCb'])
            else:
                for k in ('missingBridge', 'missingCb', 'missingIds', 'missingScripts'):
                    if o[k]: bad.append('complete page reports %s = %s' % (k, o[k]))
                if o['bridge'] < 190 or o['cb'] < 20: bad.append('lists too small %s/%s' % (o['bridge'], o['cb']))
        if not want_missing:
            # result box: shows the summary, tap opens the report, long press asks Kotlin for a new check
            ev('window.onFeatureVerify("AUTO VERIFY: 40 pass, koi FAIL nahi, sab features jude hain","NOVA AUTO VERIFY\\nPASS x")')
            if not ev('document.getElementById("p6_box")&&document.getElementById("p6_box").style.display!=="none"'): bad.append('p6_box not shown')
            if 'koi FAIL nahi' not in ev('document.getElementById("p6_box").firstChild.textContent'): bad.append('summary text missing')
            pg.evaluate('document.getElementById("p6_box").click()')
            if 'PASS x' not in ev('document.getElementById("p6_rep").textContent'): bad.append('report not opened on tap')
            box = pg.locator('#p6_box'); bb = None
            try:
                ev('document.getElementById("autoT").style.display=""')
                box.dispatch_event('pointerdown'); pg.wait_for_timeout(1100); box.dispatch_event('pointerup')
            except Exception as e: bad.append('long press failed: %s' % str(e)[:80])
            if ev('window.__now') != 1: bad.append('long press did not call verifyNow (%s)' % ev('window.__now'))
            ev('window.onFeatureVerify("AUTO VERIFY: 2 FAIL, 40 pass","x")')
            if ev('document.getElementById("p6_box").style.borderColor') == '': bad.append('fail colour not set')
        real = [e for e in errs]
        if real: bad.append('page errors: %s' % real[:2])
    b.close()
if bad:
    print('FAIL part6 render'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS part6 render (complete page = no missing; dropped bridge/callback detected; box, tap, long press)')
