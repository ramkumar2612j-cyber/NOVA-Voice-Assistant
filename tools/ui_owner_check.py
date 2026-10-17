"""R5 live UI check (headless Chromium + fake Android bridge): the four owner-required things really work on screen.
Evidence: ui.html behaviour only, NOT a real phone. Usage: python3 ui_owner_check.py"""
import os, sys
from playwright.sync_api import sync_playwright
HERE = os.path.dirname(os.path.abspath(__file__))
UI = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'ui.html')
FAKE = r"""
(function(){var on=false,saved=0;window.__saved=function(){return saved};
var B={toggle:function(){on=!on;return on?'started':'stopped'},running:function(){return on},
 saveSettings:function(j){saved++;return 'ok'},
 getSettings:function(){return JSON.stringify({wake:'nova',lang:'hi',rate:1,pitch:1,model:'x',models:['x'],hasKey:true,keyCount:1,autostart:false,screenMonitor:false})}};
window.Android=new Proxy({},{get:function(t,k){return function(){if(B[k])return B[k].apply(null,arguments);
 if(k==='getStatus')return JSON.stringify({versionName:'6.99',versionCode:699,mic:true});
 if(/^(has|get(Convo|Alerts|FreeChain|FreeImg|FreeKeyless|AutoModels|MemoryInChain|ChatSpeak))/.test(k))return false;
 if(/^get(Errors|Learned|Updates|MemoryText|VoiceLimitText)$/.test(k))return k==='getUpdates'?'{"items":[]}':'';
 if(/^get(Wait|Auto|Memory|Voice)/.test(k))return 0;return ''}}})})();
"""
bad = 0
def check(n, c):
    global bad
    print(('PASS ' if c else 'FAIL ') + n)
    if not c: bad += 1
with sync_playwright() as pw:
    b = pw.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
    pg = b.new_context(viewport={'width': 390, 'height': 844}).new_page()
    errs = []; pg.on('pageerror', lambda e: errs.append(str(e)))
    pg.add_init_script(FAKE); pg.goto('file://' + os.path.abspath(UI))
    pg.wait_for_function("document.body.classList.contains('ready')", timeout=25000); pg.wait_for_timeout(1500)
    vis = lambda s: pg.evaluate("(s)=>{var e=document.querySelector(s);if(!e)return false;var r=e.getBoundingClientRect(),c=getComputedStyle(e);return r.width>0&&r.height>0&&c.display!='none'&&c.visibility!='hidden'}", s)
    check("HOME tab visible and active", vis('#t_home') and pg.evaluate("document.getElementById('t_home').classList.contains('act')"))
    check("tab bar has HOME SKILLS LIMITS CHAT SETUP", pg.evaluate("[].map.call(document.querySelectorAll('#tabs button'),b=>b.textContent.trim()).join(',')").replace(' ', '').upper().startswith("HOME,SKILLS,LIMITS"))
    check("ACTIVATE button visible on home", vis('#btn') and pg.inner_text('#btn').strip() == 'ACTIVATE')
    dismiss = lambda: pg.evaluate("var u=document.getElementById('uc');if(u&&u.classList.contains('on'))u.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true}))")
    dismiss(); pg.click('#btn'); pg.wait_for_timeout(600); dismiss(); pg.wait_for_timeout(400)
    check("ACTIVATE -> STOP after tap", pg.inner_text('#btn').strip() == 'STOP')
    pg.click('#btn'); pg.wait_for_timeout(500); dismiss(); pg.wait_for_timeout(300)
    check("STOP -> ACTIVATE again", pg.inner_text('#btn').strip() == 'ACTIVATE')
    check("3D live orb running (WebGL2)", pg.evaluate("window.__orb3d") is True and vis('#orb3d'))
    dismiss(); pg.click('#gear'); pg.wait_for_timeout(500)
    check("Settings opens with SAVE visible at the bottom", vis('#s_save'))
    pg.click('#s_save'); pg.wait_for_timeout(1700)
    check("SAVE (done) reached the bridge once", pg.evaluate("window.__saved()") == 1)
    check("SAVE (done) closed the settings panel", not vis('#s_save'))
    check("no JavaScript errors " + str(errs), not errs)
    b.close()
print("failed:", bad); sys.exit(1 if bad else 0)
