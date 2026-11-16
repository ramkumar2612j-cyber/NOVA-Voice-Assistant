#!/usr/bin/env python3
"""v7.51 Part 12 UI runtime check (headless Chromium + FAKE bridge; NOT a phone). No playwright = 'RUNTIME: NOT TESTED', exit 0.
Checks: FRAMES NIKALO on video / photo rows (not on links); start calls frameExtractStart(id); bar width follows the real done/total only (0 events = 0%);
ROKO calls frameExtractCancel; done shows N thumbnails; error text shown; hostile text stays text; no sideways scroll; tap targets >= 44; no JS error."""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0, HERE)
try:
    from playwright.sync_api import sync_playwright
    from ui_shots import FAKE, UI
except Exception as e:
    print('RUNTIME: NOT TESTED (%s)' % e); sys.exit(0)
PNG = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFAEBAAAAAAAAAAAAAAAAAAAAAP/EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAMAwEAAhEDEQA/AL+AB//Z"
OVER = r"""
(function(){var base=window.Android;
var items=[{id:'m1_1',kind:'image',name:'IMG_2041.jpg',size:'2.4 MB',link:'',note:''},
{id:'m1_2',kind:'video',name:'<img src=x onerror=window.__xss=1>reel.mp4',size:'48.7 MB',link:'',note:''},
{id:'l1_1',kind:'link',name:'link_1',size:'0 B',link:'https://www.instagram.com/reel/ABC/',note:'Video file nahi mili.'}];
window.__f=[];window.__S={state:'idle',done:0,total:0,message:'',error:'',frames:0,secs:[]};window.__start='ok';
window.Android=new Proxy({},{get:function(t,k){return function(){var a=[].slice.call(arguments);
 if(/^frameExtract/.test(k)){window.__f.push([k].concat(a));
  if(k==='frameExtractStart')return window.__start;
  if(k==='frameExtractStatus')return JSON.stringify(window.__S);
  if(k==='frameExtractThumb')return '__PNG__';
  if(k==='frameExtractCancel'){window.__S={state:'cancelled',done:1,total:6,message:'Roka gaya.',error:'',frames:0,secs:[]};return 'ok'}
  return 'ok'}
 if(/^media/.test(k)){
  if(k==='mediaIntakeList')return JSON.stringify({items:items,busy:false,progress:0,name:'',message:'2 file aur 1 link mile hain.'});
  if(k==='mediaIntakeThumb')return '__PNG__';
  return 'ok'}
 return base[k]?base[k].apply(base,a):undefined}}})})();
""".replace('__PNG__', PNG)
bad = []
with sync_playwright() as p:
    b = p.chromium.launch(args=['--use-gl=swiftshader', '--no-sandbox'])
    for w, h in ((320, 640), (360, 740), (412, 800)):
        pg = b.new_page(viewport={'width': w, 'height': h}); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e)))
        pg.add_init_script(FAKE); pg.add_init_script(OVER); pg.goto('file://' + os.path.abspath(UI)); pg.wait_for_timeout(4500)
        pg.evaluate("[].slice.call(document.querySelectorAll('button')).forEach(function(x){if(/BAAD MEIN/.test(x.textContent))x.click()})")
        pg.evaluate("document.getElementById('chatb').click()"); pg.wait_for_timeout(1800)
        rows = pg.evaluate("[].slice.call(document.querySelectorAll('.m11_row')).map(function(r){return !!r.querySelector('.m12_btn')})")
        if rows != [True, True, False]: bad.append('%d: FRAMES NIKALO on rows %s (want photo, video, not link)' % (w, rows))
        # 1) start with no event yet: bar must be 0
        pg.evaluate("window.__S={state:'running',done:0,total:0,message:'',error:'',frames:0,secs:[]}")
        pg.evaluate("document.querySelectorAll('.m12_btn')[1].click()"); pg.wait_for_timeout(300)
        if not any(c[0] == 'frameExtractStart' and c[1] == 'm1_2' for c in pg.evaluate("window.__f")): bad.append('%d: start not called with the id' % w)
        bw0 = pg.evaluate("document.querySelector('#m12_bar i').style.width")
        if bw0 not in ('0%', '0px', ''): bad.append('%d: bar moved without an event: %s' % (w, bw0))
        # 2) a real event 3/6 -> 50%
        pg.evaluate("window.__S={state:'running',done:3,total:6,message:'',error:'',frames:0,secs:[]}"); pg.wait_for_timeout(900)
        bw = pg.evaluate("document.querySelector('#m12_bar i').style.width"); tx = pg.evaluate("document.getElementById('m12_txt').textContent")
        if bw != '50%' or tx != 'frames 3/6': bad.append('%d: running shows bar %s text %s' % (w, bw, tx))
        if not pg.evaluate("document.getElementById('m12_stop').classList.contains('on')"): bad.append('%d: ROKO not visible while running' % w)
        sx = pg.evaluate("document.documentElement.scrollWidth>window.innerWidth+1")
        small = pg.evaluate("[document.getElementById('m12_stop')].concat([].slice.call(document.querySelectorAll('.m12_btn'))).filter(function(x){return x.offsetParent!==null}).map(function(x){return Math.min(x.offsetWidth,x.offsetHeight)}).filter(function(n){return n<43.5}).length")
        if sx: bad.append('%d: sideways scroll' % w)
        if small: bad.append('%d: %d small tap targets' % (w, small))
        # 3) ROKO
        pg.evaluate("document.getElementById('m12_stop').click()"); pg.wait_for_timeout(500)
        if not any(c[0] == 'frameExtractCancel' for c in pg.evaluate("window.__f")): bad.append('%d: ROKO did not call cancel' % w)
        if 'Roka' not in pg.evaluate("document.getElementById('m12_txt').textContent"): bad.append('%d: cancelled text missing' % w)
        # 4) done with 4 frames
        pg.evaluate("window.__S={state:'done',done:6,total:6,message:'4 frame nikle (2 milte-julte hata diye).',error:'',frames:4,secs:[0,20,40,59.7]}")
        pg.evaluate("document.querySelectorAll('.m12_btn')[1].click()"); pg.wait_for_timeout(1500)
        n = pg.evaluate("document.querySelectorAll('#m12_strip .m12_f').length"); ok = pg.evaluate("[].slice.call(document.querySelectorAll('#m12_strip img')).filter(function(i){return i.src.indexOf('data:image/jpeg')===0}).length")
        if n != 4 or ok != 4: bad.append('%d: strip has %d frames, %d with image (want 4)' % (w, n, ok))
        # 5) error
        pg.evaluate("window.__S={state:'error',done:2,total:6,message:'x',error:'video se frame nahi nikle: test',frames:0,secs:[]}")
        pg.evaluate("document.querySelectorAll('.m12_btn')[1].click()"); pg.wait_for_timeout(800)
        if 'nahi nikle' not in pg.evaluate("document.getElementById('m12_txt').textContent"): bad.append('%d: error text not shown' % w)
        # 6) start refused (busy / no file)
        pg.evaluate("window.__start='video se frame nahi nikle: file nahi mili';window.__S={state:'idle',done:0,total:0,message:'',error:'',frames:0,secs:[]}")
        pg.evaluate("document.querySelectorAll('.m12_btn')[0].click()"); pg.wait_for_timeout(500)
        if 'file nahi mili' not in pg.evaluate("document.getElementById('m12_txt').textContent"): bad.append('%d: refusal text not shown' % w)
        if pg.evaluate("!!window.__xss"): bad.append('%d: file name ran as HTML' % w)
        if errs: bad.append('%d: JS errors %s' % (w, errs[:3]))
        if w == 360: pg.evaluate("window.__start='ok';window.__S={state:'done',done:6,total:6,message:'4 frame nikle (2 milte-julte hata diye).',error:'',frames:4,secs:[0,20,40,59.7]};document.querySelectorAll('.m12_btn')[1].click()"); pg.wait_for_timeout(1500); pg.screenshot(path=os.path.join(HERE, '..', 'docs', 'ui_shots', 'p12_frames_360.png'))
        pg.close()
    b.close()
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS p12 ui runtime (headless render only, not a phone)')
