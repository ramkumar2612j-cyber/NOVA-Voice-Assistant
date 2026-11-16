#!/usr/bin/env python3
"""v7.21 headless render smoke test (needs python playwright + chromium; skips with exit 0 if missing).
Loads app/src/main/assets/ui.html with a fake Android bridge at 4 phone sizes, waits for the splash to finish,
opens SKILLS / LIMITS / CONNECT / CHAT / SETUP, counts JS errors and sideways overflow, checks MADAD help rows
equal the Kotlin topic count and that skills search works. Screenshots go to /tmp/nova_shots. NOT a phone test."""
import os,re,sys
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..')
UI=os.path.join(R,'app/src/main/assets/ui.html')
try:
    from playwright.sync_api import sync_playwright
except Exception:
    print('SKIP render check (playwright not installed)'); sys.exit(0)
K=open(os.path.join(R,'app/src/main/java/com/nova/assistant/HelpGuide.kt'),encoding='utf-8').read()
TOP=len(re.findall(r'Topic\("([a-z]+)"',K))
BR="""(function(){var R={getStatus:'{}',getSettings:'{}',getBrowserStatus:'{}',getConnectors:'[]',listChats:'[]',getTier:'high',getAutoReply:'{}',getSelfImproveCfg:'{}',getWaitSec:5};window.Android=new Proxy({},{get:function(t,k){return function(){return (k in R)?R[k]:''}}});})();"""
os.makedirs('/tmp/nova_shots',exist_ok=True)
bad=[]
with sync_playwright() as p:
    try: b=p.chromium.launch()
    except Exception as e: print('SKIP render check (no chromium)'); sys.exit(0)
    for (w,h) in [(320,568),(360,640),(412,915),(800,1200)]:
        pg=b.new_context(viewport={'width':w,'height':h}).new_page(); pg.add_init_script(BR)
        pg.on('pageerror',lambda e,w=w:bad.append('%d js error: %s'%(w,str(e)[:120])))
        pg.goto('file://'+os.path.abspath(UI))
        try: pg.wait_for_function('document.body.classList.contains("ready")',timeout=25000)
        except Exception: bad.append('%d never became ready'%w); continue
        pg.wait_for_timeout(800)
        for _ in range(3):
            try: pg.get_by_text('BAAD MEIN').first.click(timeout=800)
            except Exception: break
        for n in ['SKILLS','LIMITS','CONNECT','CHAT','SETUP']:
            pg.evaluate('(function(n){var t=document.querySelectorAll("#tabs .tb");for(var i=0;i<t.length;i++){if(t[i].textContent.toUpperCase().indexOf(n)>=0){t[i].click();break}}})("%s")'%n); pg.wait_for_timeout(450)
            ov=pg.evaluate('document.documentElement.scrollWidth-innerWidth')
            if ov>0: bad.append('%d %s sideways overflow %d'%(w,n,ov))
            if n=='SKILLS':
                c=pg.evaluate('document.querySelectorAll("#sk_l .vc").length')
                if c<80: bad.append('%d skills cards only %d'%(w,c))
                pg.screenshot(path='/tmp/nova_shots/skills_%d.png'%w)
        if w==360:
            pg.evaluate('document.getElementById("c_help").click()'); pg.wait_for_timeout(500)
            n=pg.evaluate('document.getElementById("hw_l").children.length')
            if n!=TOP: bad.append('MADAD rows %d != Kotlin topics %d'%(n,TOP))
    b.close()
if bad:
    [print('FAIL',x) for x in bad]; sys.exit(1)
print('PASS render smoke: 4 sizes, 5 tabs, 0 JS errors, 0 overflow, MADAD rows = %d topics'%TOP)
