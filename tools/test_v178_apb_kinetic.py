#!/usr/bin/env python3
"""v7.178 headless check: App Builder kinetic caption follows the stage (RESEARCH, CODE, DONE), hides when idle / lowfx. NOT a phone test."""
import os,sys
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..')
UI=os.path.join(R,'app/src/main/assets/ui.html')
s=open(UI,encoding='utf-8').read()
bad=[]
for k in ('v178-apb-kinetic-css','v178-apb-kinetic-js','id="apbKin"' if False else "K.id='apbKin'"):
    if k not in s: bad.append('missing '+k)
if 'v177-kinetic-js' not in s or 'v176-apb' not in s: bad.append('older blocks removed')
try:
    from playwright.sync_api import sync_playwright
except Exception:
    print('SKIP render (no playwright)'); sys.exit(1 if bad else 0)
BR="""(function(){window.__st={state:'IDLE',stage:'',lines:[],secs:0};window.Android=new Proxy({appBuilderStatus:function(){return JSON.stringify(window.__st)}},{get:function(t,k){if(k in t)return t[k];return function(){return '[]'}}})})();"""
with sync_playwright() as p:
    try: b=p.chromium.launch()
    except Exception: print('SKIP render (no chromium)'); sys.exit(1 if bad else 0)
    pg=b.new_context(viewport={'width':360,'height':740}).new_page(); pg.add_init_script(BR)
    pg.on('pageerror',lambda e:bad.append('js error '+str(e)[:120]))
    pg.goto('file://'+os.path.abspath(UI))
    pg.wait_for_function('document.body.classList.contains("ready")',timeout=25000); pg.wait_for_timeout(500)
    pg.evaluate('document.documentElement.classList.remove("lowfx");window.__apbOpen()'); pg.wait_for_timeout(1300)  # headless auto-sets lowfx
    def kin(): return pg.evaluate('(function(){var k=document.getElementById("apbKin");return k?[k.className,(k.querySelector(".kb")||{}).textContent||"",getComputedStyle(k).display]:null})()')
    r=kin()
    if not r or r[0]=='on': bad.append('idle should be hidden: %s'%r)
    for st,want in (('RESEARCH','KHOJ.'),('CODE','CODE.')):
        pg.evaluate('document.documentElement.classList.remove("lowfx");window.__st={state:"RUNNING",stage:"%s",lines:[],secs:3}'%st); pg.wait_for_timeout(1400)
        r=kin()
        if not r or r[0]!='on' or r[1]!=want: bad.append('%s -> %s'%(st,r))
    pg.evaluate('document.documentElement.classList.remove("lowfx");window.__st={state:"DONE",stage:"DOWNLOAD",lines:[],secs:9}'); pg.wait_for_timeout(1400)
    r=kin()
    if not r or r[1]!='APP TAIYAAR.': bad.append('DONE -> %s'%r)
    pg.evaluate('document.documentElement.classList.add("lowfx")'); pg.wait_for_timeout(200)
    r=kin()
    if r and r[2]!='none': bad.append('lowfx should hide: %s'%r)
    pg.evaluate('document.documentElement.classList.remove("lowfx");window.__st={state:"IDLE",stage:"",lines:[],secs:0}'); pg.wait_for_timeout(1400)
    r=kin()
    if r and r[0]=='on': bad.append('back to idle should hide: %s'%r)
    b.close()
for x in bad: print('FAIL',x)
print('FAIL v178 apb kinetic' if bad else 'PASS v178 apb kinetic')
sys.exit(1 if bad else 0)
