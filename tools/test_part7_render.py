#!/usr/bin/env python3
"""v7.110 PART 7 headless behaviour of the BADA BRAIN + SEEKHA card (headless chromium, fake Android bridge). NOT a phone test."""
import os, sys, json
H = os.path.dirname(os.path.abspath(__file__)); R = os.path.join(H, '..')
bad = []
def need(c, m):
    print(('PASS: ' if c else 'FAIL: ') + m)
    if not c: bad.append(m)
STATE = {'ram': 8000, 'headline': 'Is phone ke liye suggested: Qwen2.5 3B. Aap chuno, tabhi download hoga.', 'busy': False, 'id': '', 'pct': 0, 'msg': '',
         'options': [{'id': 'qwen25-3b', 'label': 'Qwen2.5 3B', 'mb': 2050, 'state': 'OFFER', 'rec': True, 'note': 'Recommended for this phone. Download starts only when you tap.'},
                     {'id': 'qwen25-15b', 'label': 'Qwen2.5 1.5B', 'mb': 1100, 'state': 'NEEDS_SPACE', 'rec': False, 'note': 'Not enough free storage.'}]}
BRIDGE_JS = """(function(){window.__c=[];window.__clr='3';window.__confirm=true;window.confirm=function(){window.__c.push('confirm');return window.__confirm};
var ST=%s;var learn='ROUTE: mail: Gmail app theek chala\\n\\nSITES: amazon.in: 1 baar login wall';
window.Android=new Proxy({},{get:function(t,k){return function(a){
 if(k==='getBrainChoice')return JSON.stringify(ST);
 if(k==='startBrainInstall'){window.__c.push('start:'+a);return 'started'}
 if(k==='cancelBrainInstall'){window.__c.push('cancel');return 'ok'}
 if(k==='getLearnedSummary')return learn;
 if(k==='clearJobLearning'){window.__c.push('clear');learn='';return window.__clr}
 var R={getStatus:'{}',getSettings:'{}',getBrowserStatus:'{}',getConnectors:'[]',listChats:'[]',getTier:'high',getAutoReply:'{}',getSelfImproveCfg:'{}',getWaitSec:5};
 return R[k]!==undefined?R[k]:''}}})})();""" % json.dumps(STATE)
try:
    from playwright.sync_api import sync_playwright
except Exception:
    print('SKIP headless (no playwright)'); sys.exit(0)
with sync_playwright() as p:
    try: b = p.chromium.launch()
    except Exception: print('SKIP headless (no chromium)'); sys.exit(0)
    pg = b.new_context(viewport={'width': 320, 'height': 700}).new_page(); pg.add_init_script(BRIDGE_JS)
    errs = []; pg.on('pageerror', lambda e: errs.append(str(e)[:150]))
    pg.goto('file://' + os.path.abspath(os.path.join(R, 'app/src/main/assets/ui.html')))
    pg.wait_for_function('document.body.classList.contains("ready")', timeout=25000); pg.wait_for_timeout(600)
    ev = lambda s: pg.evaluate(s)
    need(ev('!!document.getElementById("p7_card")'), 'card rendered')
    need('Qwen2.5 3B' in ev('document.getElementById("p7_head").textContent'), 'headline shows the suggested brain')
    need('SUGGESTED' in ev('document.getElementById("p7_opts").textContent'), '3B marked SUGGESTED')
    need(ev('document.querySelectorAll("#p7_opts button").length') == 1, 'only the OFFER option has a DOWNLOAD button (NEEDS_SPACE has none)')
    ev('document.querySelector("#p7_opts button").click()')
    need('start:qwen25-3b' not in ev('window.__c'), 'first tap does NOT start a download')
    need('PAKKA' in ev('document.querySelector("#p7_opts button").textContent'), 'first tap asks PAKKA?')
    ev('document.querySelector("#p7_opts button").click()')
    need('start:qwen25-3b' in ev('window.__c'), 'second tap starts exactly that download')
    need(ev('window.__c.filter(function(x){return x.indexOf("start:")===0}).length') == 1, 'only one start call')
    need('Download shuru' in ev('document.getElementById("p7_prog").textContent'), 'start message shown')
    ev('window.onBrainInstall(true,"Qwen2.5 3B installed and verified")')
    need('Ho gaya' in ev('document.getElementById("p7_prog").textContent'), 'onBrainInstall success shown')
    ev('window.onBrainInstall(false,"no internet")')
    need('Nahi hua' in ev('document.getElementById("p7_prog").textContent'), 'onBrainInstall failure shown honestly')
    need('amazon.in' in ev('document.getElementById("p7_learn").textContent'), 'learned summary shown')
    ev('window.__confirm=false;document.getElementById("p7_clr").click()')
    need('clear' not in ev('window.__c'), 'clear is NOT called when the owner says no')
    ev('window.__confirm=true;document.getElementById("p7_clr").click()')
    need('clear' in ev('window.__c'), 'clear called after yes')
    need('3 / 3' in ev('document.getElementById("p7_lmsg").textContent'), '3 / 3 reported')
    need('Abhi kuch seekha' in ev('document.getElementById("p7_learn").textContent'), 'summary empty after clear')
    ev('window.__clr="2";document.getElementById("p7_clr").click()')
    need('Poora saaf nahi hua' in ev('document.getElementById("p7_lmsg").textContent'), 'partial clear is reported honestly, not as success')
    need(ev('document.documentElement.scrollWidth <= 321'), 'no horizontal scroll at 320 px')
    need(ev('Array.prototype.every.call(document.querySelectorAll("#p7_card button"),function(b){return b.offsetParent===null||b.getBoundingClientRect().height>=43.5})'), 'visible buttons are at least 44 px tall')
    need(not errs, 'no page error %s' % errs)
if bad:
    print('FAILED', len(bad)); sys.exit(1)
print('PASS part7 render')
