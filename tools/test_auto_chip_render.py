#!/usr/bin/env python3
"""v7.30 PART 1 headless render check of the live 'NOVA SAMJHA' chip + hidden IMAGE switch (fake Android bridge). Skips with exit 0 if playwright/chromium missing. NOT a phone test."""
import os, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'); UI = os.path.join(R, 'app/src/main/assets/ui.html')
try:
    from playwright.sync_api import sync_playwright
except Exception:
    print('SKIP (no playwright)'); sys.exit(0)
BR = """(function(){var R={getStatus:'{}',getSettings:'{}',getBrowserStatus:'{}',getConnectors:'[]',listChats:'[]',getTier:'high',getAutoReply:'{}',getSelfImproveCfg:'{}',getWaitSec:5};
window.Android=new Proxy({},{get:function(t,k){return function(a,b){
 if(k==='detectIntent'){var s=String(a).toLowerCase();if(b===true&&s.indexOf('background')>=0)return JSON.stringify({kind:'PHOTO_EDIT',subject:'BG_REMOVE',blocked:false});if(s.indexOf('kahani')>=0)return JSON.stringify({kind:'WRITE',subject:'',blocked:false});if(s.indexOf('nude')>=0)return JSON.stringify({kind:'IMAGE',subject:'',blocked:true});if(s.indexOf('website')>=0||s.indexOf('site')>=0)return JSON.stringify({kind:'SITE',subject:'gym',blocked:false});if(s.indexOf('logo')>=0)return JSON.stringify({kind:'IMAGE',subject:'x',blocked:false});return JSON.stringify({kind:'NONE'})}
 window.__calls=window.__calls||[];if(k==='editPhoto'||k==='makeImage'||k==='sendText')window.__calls.push(k+':'+a);
 if(k==='editPhoto')return 'started'; if(k==='makeImage')return 'started'; if(k==='sendText')return 'sent'; if(k==='running')return true;
 return R[k]!==undefined?R[k]:''}}})})();"""
bad = []
with sync_playwright() as p:
    try: b = p.chromium.launch()
    except Exception: print('SKIP (no chromium)'); sys.exit(0)
    pg = b.new_context(viewport={'width': 360, 'height': 700}).new_page(); pg.add_init_script(BR)
    pg.on('pageerror', lambda e: bad.append('js error: ' + str(e)[:150]))
    pg.goto('file://' + os.path.abspath(UI))
    try: pg.wait_for_function('document.body.classList.contains("ready")', timeout=25000)
    except Exception: print('FAIL never ready'); sys.exit(1)
    pg.wait_for_timeout(800)
    for _ in range(3):
        try: pg.get_by_text('BAAD MEIN').first.click(timeout=800)
        except Exception: break
    for _ in range(4):
        for _k in range(3):
            try: pg.get_by_text('BAAD MEIN').first.click(timeout=500)
            except Exception: break
        pg.evaluate('document.getElementById("chatb").click()')
        try: pg.wait_for_selector('#c_in', state='visible', timeout=3000); break
        except Exception: pg.wait_for_timeout(1500)
    else:
        print('FAIL chat input never visible'); sys.exit(1)
    vis = lambda i: pg.evaluate('(function(i){var e=document.getElementById(i);return !!e&&getComputedStyle(e).display!=="none"})("%s")' % i)
    if vis('c_imode'): bad.append('IMAGE switch still visible')
    def typ(t):
        pg.fill('#c_in', t); pg.wait_for_timeout(600)
        return pg.evaluate('(function(){var c=document.getElementById("c_auto");return c&&c.className.indexOf("on")>=0?c.textContent:""})()')
    a = typ('gym ke liye website chahiye')
    if 'WEBSITE' not in a: bad.append('site chip missing: %r' % a)
    a = typ('mujhe ek logo chahiye')
    if 'PHOTO' not in a: bad.append('image chip missing: %r' % a)
    a = typ('aaj mausam kaisa hai')
    if a: bad.append('chip shown for normal chat: %r' % a)
    a = typ('nude logo banao')
    if a: bad.append('chip shown for blocked: %r' % a)
    pg.screenshot(path='/tmp/nova_chip.png')
    # stuck-ON protection: switch forced ON without a photo, then a send must turn it OFF first
    pg.evaluate('document.getElementById("c_imode").click()')
    if 'imon' not in pg.evaluate('document.getElementById("c_imode").className'): bad.append('test setup: switch did not turn on')
    pg.fill('#c_in', 'hello nova'); pg.press('#c_in', 'Enter'); pg.wait_for_timeout(200)
    if 'imon' in pg.evaluate('document.getElementById("c_imode").className'): bad.append('IMAGE switch stayed ON after send without photo')
    a = typ('ek kahani likh do')
    if 'LIKH' not in a: bad.append('write chip missing: %r' % a)
    # photo attached: an edit sentence must call editPhoto (not makeImage / sendText)
    pg.evaluate('document.getElementById("c_thw").style.display="inline-flex";window.__calls=[]')
    pg.fill('#c_in', 'background hatao'); pg.press('#c_in', 'Enter'); pg.wait_for_timeout(300)
    calls = pg.evaluate('window.__calls')
    if calls != ['editPhoto:background hatao']: bad.append('photo edit did not go to editPhoto only: %r' % calls)
    if 'Edit ho raha hai' not in pg.evaluate('document.getElementById("clog").textContent'): bad.append('no edit-started bubble')
    # photo attached + a non-edit sentence keeps the old path (not editPhoto)
    pg.evaluate('window.__calls=[]'); pg.fill('#c_in', 'hello nova'); pg.press('#c_in', 'Enter'); pg.wait_for_timeout(300)
    calls = pg.evaluate('window.__calls')
    if any(c.startswith('editPhoto') for c in calls): bad.append('normal sentence wrongly sent to editPhoto: %r' % calls)
    b.close()
if bad: [print('FAIL', x) for x in bad]; sys.exit(1)
print('PASS chip: site / image / write / none / blocked + hidden switch + no stuck image mode + photo-attached edit goes to editPhoto')
