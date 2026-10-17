"""v7.06 no-cover / scroll audit (headless Chromium + FAKE Android bridge). Evidence = ui.html layout only, NOT a real phone.
For every main screen and overlay: scroll to the very end, then the last visible element must end ABOVE the bottom tab bar
(or above the screen bottom for overlays that sit over the tab bar). Usage: python3 tools/ui_scroll_audit.py [outdir]
Exit code 1 if any check FAILs."""
import os, sys
from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
UI = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'ui.html')
OUT = sys.argv[1] if len(sys.argv) > 1 else '/tmp/nova_scroll_audit'
os.makedirs(OUT, exist_ok=True)

FAKE = r"""
(function(){var on=false;
var B={toggle:function(){on=!on;return on?'started':'stopped'},running:function(){return on},saveSettings:function(){return 'ok'},
 getSettings:function(){return JSON.stringify({wake:'nova',lang:'hi',rate:1,pitch:1,model:'x',models:['x'],hasKey:true,keyCount:1,autostart:false,screenMonitor:false})}};
window.Android=new Proxy({},{get:function(t,k){return function(){if(B[k])return B[k].apply(null,arguments);
 if(k==='getStatus')return JSON.stringify({versionName:'7.06',versionCode:706,mic:true});
 if(/^(has|get(Convo|Alerts|FreeChain|FreeImg|FreeKeyless|AutoModels|MemoryInChain|ChatSpeak))/.test(k))return false;
 if(/^get(Errors|Learned|Updates|MemoryText|VoiceLimitText)$/.test(k))return k==='getUpdates'?'{"items":[]}':'';
 if(/^get(Wait|Auto|Memory|Voice)/.test(k))return 0;return ''}}})})();
"""

# JS: scroll the element to the end and return how far the last visible descendant reaches (page px) + scroll facts.
MEASURE = r"""
(sel)=>{
  var el=document.querySelector(sel); if(!el) return {missing:true};
  var cs=getComputedStyle(el);
  if(cs.display==='none') return {hidden:true};
  el.style.scrollBehavior='auto'; el.scrollTop=1e7;
  var best=0, n=0;
  var all=el.querySelectorAll('*');
  for(var i=0;i<all.length;i++){
    var e=all[i], c=getComputedStyle(e);
    if(c.display==='none'||c.visibility==='hidden'||c.position==='fixed') continue;
    var r=e.getBoundingClientRect();
    if(r.width<2||r.height<2) continue;
    if(e.children.length>0 && c.overflowY!=='visible' && c.overflowY!=='') {} // inner scrollers count by their own box
    var p=e.parentElement, inner=false; while(p&&p!==el){var po=getComputedStyle(p).overflowY; if(po==='auto'||po==='scroll'){inner=true;break;} p=p.parentElement;}
    if(inner) continue;
    if(r.bottom>best){best=r.bottom;} n++;
  }
  var t=document.getElementById('tabs'); var tt=t?t.getBoundingClientRect().top:innerHeight;
  return {lastBottom:Math.round(best), tabsTop:Math.round(tt), vh:innerHeight, scrollable:el.scrollHeight>el.clientHeight+1, sh:el.scrollHeight, ch:el.clientHeight, count:n};
}
"""

bad = 0
def report(name, m, limit_key):
    global bad
    if m.get('missing'):
        print('NOT TESTED %s (selector not found)' % name); return
    if m.get('hidden'):
        print('NOT TESTED %s (not visible after opening)' % name); return
    limit = m[limit_key]
    ok = m['lastBottom'] <= limit + 1
    print(('PASS ' if ok else 'FAIL ') + '%s: last element bottom %d <= %s %d (scrollHeight %d / client %d)' % (name, m['lastBottom'], limit_key, limit, m['sh'], m['ch']))
    if not ok: bad += 1

TABS = [('SKILLS', 't_skills', '#v_skills'), ('LIMITS', 't_limits', '#v_limits'), ('CONNECT', 't_conn', '#v_conn')]
OVERLAYS = [  # name, selector, how to open, limit
    ('HELP sheet #helpw', '#helpw .aib', "document.getElementById('helpw').classList.add('on')", 'vh'),
    ('AI choice #aiw', '#aiw .aib', "document.getElementById('aiw').classList.add('on')", 'vh'),
    ('SITE overlay #ws_ov', '#ws_ov .bd', "document.getElementById('ws_ov').classList.add('on')", 'vh'),
    ('AUTOMATION overlay #au_ov', '#au_ov', "document.getElementById('au_ov').classList.add('on')", 'vh'),
    ('CHAT sheet #sheet', '#sheet', "document.getElementById('sheet').classList.add('on')", 'tabsTop'),
    ('HISTORY #hist', '#hist .hl', "document.getElementById('hist').style.display='flex'", 'vh'),
]
SIZES = [(390, 844), (360, 640), (844, 390)]  # normal phone, small phone, landscape

with sync_playwright() as pw:
    b = pw.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
    for (w, h) in SIZES:
        print('=== viewport %dx%d' % (w, h))
        pg = b.new_context(viewport={'width': w, 'height': h}).new_page()
        errs = []; pg.on('pageerror', lambda e: errs.append(str(e)))
        pg.add_init_script(FAKE); pg.goto('file://' + os.path.abspath(UI))
        pg.wait_for_function("document.body.classList.contains('ready')", timeout=25000); pg.wait_for_timeout(1200)
        dismiss = lambda: pg.evaluate("var u=document.getElementById('uc');if(u&&u.classList.contains('on'))u.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true}))")
        dismiss(); pg.wait_for_timeout(300)
        # HOME: the page itself (#app) must reach the end above the tab bar
        report('HOME #app', pg.evaluate(MEASURE, '#app'), 'tabsTop')
        for name, tab, sel in TABS:
            dismiss()
            try: pg.evaluate("(i)=>document.getElementById(i).click()", tab); pg.wait_for_timeout(350)
            except Exception as e: print('NOT TESTED %s (tab click failed)' % name); continue
            report(name + ' ' + sel, pg.evaluate(MEASURE, sel), 'tabsTop')
        try:
            dismiss(); pg.evaluate("document.getElementById('chatb').click()"); pg.wait_for_timeout(400)
            report('CHAT #chat', pg.evaluate(MEASURE, '#chat'), 'tabsTop')
        except Exception: print('NOT TESTED CHAT (click failed)')
        try:
            dismiss(); pg.evaluate("var p=document.getElementById('panel');if(p&&getComputedStyle(p).display!=='none'){}else{document.getElementById('gear').click()}"); pg.wait_for_timeout(700)
            report('SETTINGS #panel', pg.evaluate(MEASURE, '#panel'), 'tabsTop')
            pg.evaluate("[].forEach.call(document.querySelectorAll('#panel details'),function(d){d.open=true})"); pg.wait_for_timeout(200)
            report('SETTINGS #panel (all accordions open)', pg.evaluate(MEASURE, '#panel'), 'tabsTop')
        except Exception: print('NOT TESTED SETTINGS (click failed)')
        # overlays slide in with a CSS animation; headless + software WebGL can leave it at its start offset -> measure the settled layout
        pg.add_style_tag(content='#helpw .aib,#aiw .aib,#ws_ov,#au_ov,#sheet .sh{animation:none!important;transform:none!important}')
        for name, sel, opener, key in OVERLAYS:
            try:
                pg.evaluate(opener); pg.wait_for_timeout(900)
                report(name, pg.evaluate(MEASURE, sel), key)
                pg.screenshot(path=os.path.join(OUT, '%dx%d_%s.png' % (w, h, name.split('#')[-1].replace(' ', '_'))))
                pg.evaluate("(sel)=>{var o=document.querySelector(sel.split(' ')[0]);if(o){o.classList.remove('on');o.style.display=''}}", sel)
            except Exception as e:
                print('NOT TESTED %s (%s)' % (name, str(e)[:60]))
        # fut_edge / HUD layers must never swallow taps
        for fid in ('fut_bg', 'fut_scan', 'fut_edge'):
            pe = pg.evaluate("(i)=>{var e=document.getElementById(i);return e?getComputedStyle(e).pointerEvents:'absent'}", fid)
            if pe == 'absent': continue
            ok = pe == 'none'
            print(('PASS ' if ok else 'FAIL ') + '#%s pointer-events=%s' % (fid, pe))
            if not ok: bad += 1
        print(('PASS ' if not errs else 'FAIL ') + 'no JavaScript errors %s' % errs[:2])
        if errs: bad += 1
        pg.close()
    b.close()
print('failed:', bad); sys.exit(1 if bad else 0)
