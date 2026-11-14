import os, sys, shutil, time
from playwright.sync_api import sync_playwright
os.chdir('/home/claude/nova')
src=open('tools/ui_shots.py',encoding='utf8').read()
ns={'__file__':os.path.abspath('tools/ui_shots.py'),'__name__':'x'}
exec(compile(src.split('def run')[0],'x','exec'),ns)
FAKE=ns['FAKE']; UI=os.path.abspath('app/src/main/assets/ui.html')
OUT=sys.argv[1]; SECS=float(sys.argv[2]); VOICE=(sys.argv[3]=='voice') if len(sys.argv)>3 else True
MOD=int(sys.argv[4]) if len(sys.argv)>4 else 1; REM=int(sys.argv[5]) if len(sys.argv)>5 else 0
os.makedirs(OUT,exist_ok=True)
VCLOCK=r"""
(function(){var V=0,id=1,T=[],R=[];
performance.now=function(){return V};var D0=Date.now();Date.now=function(){return D0+V};
window.requestAnimationFrame=function(cb){var i=id++;R.push([i,cb]);return i};
window.cancelAnimationFrame=function(i){R=R.filter(function(r){return r[0]!==i})};
window.setTimeout=function(f,ms){var i=id++;var a=[].slice.call(arguments,2);T.push({i:i,at:V+(+ms||0),f:f,a:a,iv:0});return i};
window.setInterval=function(f,ms){var i=id++;var a=[].slice.call(arguments,2);ms=Math.max(1,+ms||1);T.push({i:i,at:V+ms,f:f,a:a,iv:ms});return i};
window.clearTimeout=window.clearInterval=function(i){T=T.filter(function(t){return t.i!==i})};
window.__step=function(dt){V+=dt;var g=0;
 while(g++<5000){var d=null;for(var k=0;k<T.length;k++){if(T[k].at<=V&&(!d||T[k].at<d.at))d=T[k]}if(!d)break;
  if(d.iv){d.at+=d.iv}else{T=T.filter(function(t){return t!==d})}
  try{if(typeof d.f==='function')d.f.apply(window,d.a)}catch(e){}}
 var q=R;R=[];q.forEach(function(r){try{r[1](V)}catch(e){}});
 try{document.getAnimations().forEach(function(a){if(a.__v===undefined){a.__v=V;try{a.pause()}catch(e){}}try{a.currentTime=V-a.__v}catch(e){}})}catch(e){}
 return V};
window.__V=function(){return V};
})();
"""
with sync_playwright() as pw:
    b=pw.chromium.launch(args=['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist'])
    ctx=b.new_context(viewport={'width':390,'height':844},device_scale_factor=1)
    pg=ctx.new_page(); errs=[]
    pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.on('console',lambda m:errs.append('console:'+m.text) if m.type=='error' else None)
    pg.add_init_script(VCLOCK); pg.add_init_script(FAKE)
    pg.goto('file://'+UI); pg.wait_for_load_state('load'); time.sleep(3); pg.add_style_tag(content='#setupBanner{display:none!important}')
    dt=1000/30.0; n=int(SECS*30); v=False
    for f in range(n):
        pg.evaluate(f"window.__step({dt})")
        if VOICE and not v and f*dt>=600:
            v=True; pg.evaluate("try{onOpeningVoice(5200)}catch(e){}")
        if f%MOD==REM: pg.screenshot(path=f"{OUT}/f{f:04d}.jpg",type='jpeg',quality=88)
    print('frames',n,'errors',errs[:5],'ready',pg.evaluate("document.body.classList.contains('ready')"))
    b.close()
