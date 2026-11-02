#!/usr/bin/env python3
"""v7.69 PART 17 SETTINGS RESTYLE (static contract + headless behaviour, add-only). Evidence: static + headless chromium with a fake Android bridge. NOT a Kotlin compile, NOT phone tested.
1 Kotlin bridge getStopPowersOff / setStopPowersOff exists and is backed by ExtCfg; both listed in ui.html BRIDGE; script pt17_switches is in SCR
2 old controls still there (s_quality, pt9_pers, s_save): add-only
3 STOP default stays OFF (SIRF ROKE) in ExtCfg and SafeStop
4 headless: the card renders, each switch calls the right bridge method, quality writes nova_q, old select / persona button stay in step"""
import os, re, sys
H = os.path.dirname(os.path.abspath(__file__)); R = os.path.join(H, '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
KT = 'app/src/main/java/com/nova/assistant/'
ma, ec, ss, ui = rd(KT + 'MainActivity.kt'), rd(KT + 'ExtCfg.kt'), rd(KT + 'SafeStop.kt'), rd('app/src/main/assets/ui.html')
hg = rd(KT + 'HelpGuide.kt')
bad = []
def need(c, m):
    print(('PASS ' if c else 'FAIL ') + m)
    if not c: bad.append(m)
need('fun getStopPowersOff(): Boolean = ExtCfg.stopPowersOff(' in ma, 'Kotlin getStopPowersOff bridge')
need('fun setStopPowersOff(on: Boolean)' in ma and 'ExtCfg.setStopPowersOff(' in ma, 'Kotlin setStopPowersOff bridge')
br = re.search(r'var BRIDGE=(\[.*?\]);', ui, re.S).group(1)
need('"getStopPowersOff"' in br and '"setStopPowersOff"' in br, 'BRIDGE lists both stop methods')
need("'pt17_switches'" in ui and 'id="pt17_switches"' in ui, 'pt17_switches script exists and is in SCR')
for i in ('id="s_quality"', 'id="s_save"', "b.id='pt9_pers'"):
    need(i in ui, 'old control kept: ' + i)
need('getBoolean(STOP_OFF, false)' in ec and 'catch (e: Exception) { false }' in ec, 'ExtCfg.stopPowersOff default OFF')
need('if (stopPowersOff) HudStop.POWER_OFF else HudStop.SOFT' in ss, 'SafeStop SOFT unless the owner turns the switch ON')
need('Topic("switches"' in hg and '"id": "switches"' in ui, 'MADAD topic switches in Kotlin and ui.html')
need(os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/HelpGuidePart17Test.kt')), 'HelpGuidePart17Test.kt present')
BRIDGE_JS = """(function(){window.__c=[];var S={stop:false,pers:true};
window.Android=new Proxy({},{get:function(t,k){return function(a){
 if(k==='getStopPowersOff')return S.stop; if(k==='setStopPowersOff'){S.stop=!!a;window.__c.push('stop:'+a);return 'ok'}
 if(k==='getPersona')return S.pers; if(k==='setPersona'){S.pers=!!a;window.__c.push('pers:'+a);return 'ok'}
 var R={getStatus:'{}',getSettings:'{}',getBrowserStatus:'{}',getConnectors:'[]',listChats:'[]',getTier:'high',getAutoReply:'{}',getSelfImproveCfg:'{}',getWaitSec:5};
 return R[k]!==undefined?R[k]:''}}})})();"""
try:
    from playwright.sync_api import sync_playwright
except Exception:
    print('SKIP headless (no playwright)'); sync_playwright = None
if sync_playwright:
    with sync_playwright() as p:
        try: b = p.chromium.launch()
        except Exception: print('SKIP headless (no chromium)'); b = None
        if b:
            pg = b.new_context(viewport={'width': 320, 'height': 700}).new_page(); pg.add_init_script(BRIDGE_JS)
            errs = []; pg.on('pageerror', lambda e: errs.append(str(e)[:150]))
            pg.goto('file://' + os.path.abspath(os.path.join(R, 'app/src/main/assets/ui.html')))
            pg.wait_for_function('document.body.classList.contains("ready")', timeout=25000); pg.wait_for_timeout(600)
            ev = lambda s: pg.evaluate(s)
            need(ev('!!document.getElementById("pt17_card")'), 'headless: SWITCHES card rendered')
            ev('document.querySelector("#p17_s button[data-v=\\"1\\"]").click()')
            ev('document.querySelector("#p17_p button[data-v=\\"0\\"]").click()')
            ev('document.querySelector("#p17_q button[data-v=\\"smooth\\"]").click()')
            c = ev('window.__c')
            need('stop:true' in c, 'headless: STOP switch calls setStopPowersOff(true)')
            need('pers:false' in c, 'headless: PERSONA switch calls setPersona(false)')
            need(ev('localStorage.getItem("nova_q")') == 'smooth', 'headless: quality writes nova_q=smooth')
            need(ev('document.getElementById("s_quality").value') == 'smooth', 'headless: old #s_quality select kept in step')
            need(ev('document.getElementById("pt9_pers").textContent') == 'PERSONA OFF', 'headless: old persona button kept in step')
            need(ev('document.querySelector("#p17_s button[data-v=\\"1\\"]").getAttribute("aria-pressed")') == 'true', 'headless: pressed state shown')
            need(ev('document.documentElement.scrollWidth <= 321'), 'headless: no horizontal scroll at 320 px')
            need(not errs, 'headless: no page error %s' % errs)
if bad:
    print('FAILED', len(bad)); sys.exit(1)
print('PASS part17 contract')
