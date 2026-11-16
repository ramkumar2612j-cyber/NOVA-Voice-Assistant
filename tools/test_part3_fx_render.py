#!/usr/bin/env python3
"""v7.32 PART 3 headless check of chat effects (typing reveal, image reveal, photo zoom, website progress card, lowfx = none) (fake Android bridge). Skips with exit 0 if playwright/chromium missing. NOT a phone test."""
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
    vis=None
    PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=='
    ev = pg.evaluate
    ev('window.__forceLow=false;document.documentElement.classList.remove("lowfx")')
    print('lowfx at start was removed for test; app default can be lowfx on weak phones')
    ev('document.documentElement.classList.remove("lowfx")')
    # 1 typing reveal: text ends complete, flag removed, no stuck min-height
    ev('(function(){var m=document.createElement("div");m.className="msg ai";m.id="t1";m.textContent="Namaste ye ek chhota sa test jawab hai jo dheere dheere aana chahiye";document.getElementById("clog").appendChild(m)})()')
    pg.wait_for_timeout(60)
    if not ev('document.getElementById("t1").classList.contains("p3_typing")'): bad.append('typing reveal did not start')
    pg.wait_for_timeout(1900)
    if ev('document.getElementById("t1").textContent') != "Namaste ye ek chhota sa test jawab hai jo dheere dheere aana chahiye": bad.append('typing reveal lost text')
    if ev('document.getElementById("t1").classList.contains("p3_typing")') or ev('document.getElementById("t1").style.minHeight'): bad.append('typing flag/minHeight stuck')
    # 2 image reveal
    ev('window.onImage(true,"ok","%s","pic.jpg")' % PNG)
    pg.wait_for_timeout(100)
    if not ev('!!document.querySelector("#clog .msg.p3_img img.gimg")'): bad.append('image reveal class missing')
    pg.wait_for_timeout(1900)
    if not ev('document.querySelector("#clog .msg.p3_img").classList.contains("p3_off")'): bad.append('image effect never switched off')
    # 3 user photo thumb zoom
    ev('document.documentElement.classList.remove("lowfx")')
    ev('document.getElementById("c_thumb").src="data:image/png;base64,%s"' % PNG); pg.wait_for_timeout(150)
    if not ev('document.getElementById("c_thw").classList.contains("p3_zoom")'): bad.append('photo thumb zoom missing')
    ev('document.documentElement.classList.remove("lowfx")')
    # 4 website progress: chip site + send -> card; onSite -> card done
    ev('(function(){var c=document.getElementById("c_auto");c.className="on site"})()')
    pg.fill('#c_in','gym ke liye website chahiye'); pg.press('#c_in','Enter'); pg.wait_for_timeout(300)
    n = ev('document.querySelectorAll("#clog .p3_site").length')
    if n != 1: bad.append('website card count %s' % n)
    pg.screenshot(path='/tmp/nova_p3_site.png')
    ev('window.onSite(true,"gym","<html><body>x</body></html>","")'); pg.wait_for_timeout(1300)
    if ev('document.querySelectorAll("#clog .p3_site").length') != 0: bad.append('website card stuck after onSite')
    if not ev('!!document.querySelector("#clog button.sm")'): bad.append('old DEKHO button missing after onSite')
    # 5 website refused (plain ai text) removes card
    ev('(function(){var c=document.getElementById("c_auto");c.className="on site"})()')
    pg.fill('#c_in','gym ke liye website chahiye'); pg.press('#c_in','Enter'); pg.wait_for_timeout(300)
    ev('(function(){var m=document.createElement("div");m.className="msg ai";m.textContent="Ye website main nahi bana sakta";document.getElementById("clog").appendChild(m)})()'); pg.wait_for_timeout(200)
    if ev('document.querySelectorAll("#clog .p3_site").length') != 0: bad.append('card not removed after plain refusal')
    # 6 normal chat: no card
    ev('document.getElementById("c_auto").className=""'); pg.fill('#c_in','hello'); pg.press('#c_in','Enter'); pg.wait_for_timeout(300)
    if ev('document.querySelectorAll("#clog .p3_site").length') != 0: bad.append('card shown for normal chat')
    # 7 bulk render (old chat) = no effects
    ev('(function(){var f=document.createDocumentFragment();for(var i=0;i<5;i++){var m=document.createElement("div");m.className="msg ai bulk";m.textContent="purana message number "+i;f.appendChild(m)}document.getElementById("clog").appendChild(f)})()'); pg.wait_for_timeout(100)
    if ev('document.querySelectorAll("#clog .bulk.p3_typing").length') != 0: bad.append('bulk render got typing effect')
    # 8 lowfx: no typing
    ev('document.documentElement.classList.add("lowfx")')
    ev('(function(){var m=document.createElement("div");m.className="msg ai";m.id="t2";m.textContent="low effect message";document.getElementById("clog").appendChild(m)})()'); pg.wait_for_timeout(100)
    if ev('document.getElementById("t2").classList.contains("p3_typing")') or ev('document.getElementById("t2").textContent') != "low effect message": bad.append('lowfx still animates')
    pg.screenshot(path='/tmp/nova_p3_end.png')
    for e in bad[:0]: pass
    b.close()
if bad:
    print('FAIL'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS part3 chat effects render (8 checks)')
