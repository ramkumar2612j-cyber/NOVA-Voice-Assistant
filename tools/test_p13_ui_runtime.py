#!/usr/bin/env python3
"""v7.53 Part 13B-1 UI runtime check (headless Chromium + FAKE bridge; NOT a phone). No playwright = 'RUNTIME: NOT TESTED', exit 0.
Checks: ANALYSE on video / photo rows (not on links); start calls analysisStart(id, question) with the typed question; bar follows the real percent only (no event = 0%);
ROKO calls analysisCancel; done shows the report text via textContent; refusal / error text shown; hostile text stays text; no sideways scroll; tap targets >= 44; no JS error.
v7.60 (Part 13D acceptance): + chat photo + question flow (pt13d_chat): chatPhotoAsk called with the typed question, old send route NOT run while the HAAN / NAHI box is open,
"notquestion" keeps the old route, a refusal sentence is shown as text, edit / image sentences (kind != NONE) and a chat without a photo never call chatPhotoAsk."""
import os, sys, json
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
window.__f=[];window.__S={state:'idle',stage:'INTAKE',done:0,total:0,percent:0,message:'',error:'',id:''};window.__start='ok';window.__T='';
window.Android=new Proxy({},{get:function(t,k){return function(){var a=[].slice.call(arguments);
 if(/^analysis/.test(k)){window.__f.push([k].concat(a));
  if(k==='analysisStart')return window.__start;
  if(k==='analysisStatus')return JSON.stringify(window.__S);
  if(k==='analysisResultText')return window.__T;
  if(k==='analysisCancel'){window.__S={state:'cancelled',stage:'FAILED',done:1,total:2,percent:50,message:'Roka gaya.',error:'',id:'m1_2'};return 'ok'}
  return ''}
 if(/^frameExtract/.test(k)){return k==='frameExtractStatus'?'{"state":"idle"}':'ok'}
 if(/^media/.test(k)){
  if(k==='mediaIntakeList')return JSON.stringify({items:items,busy:false,progress:0,name:'',message:'2 file aur 1 link mile hain.'});
  if(k==='mediaIntakeThumb')return '__PNG__';
  return 'ok'}
 return base[k]?base[k].apply(base,a):undefined}}})})();
""".replace('__PNG__', PNG)

# v7.60 13D: wraps the bridge above; records EVERY call in window.__g and fakes chatPhotoAsk / detectIntent / sendText. Everything else is passed on unchanged.
OVER3 = r"""
(function(){var ae=document.addEventListener.bind(document);document.addEventListener=function(t,f,c){
 if((t==='keydown'||t==='click')&&c===true&&typeof f==='function'&&f.name!=='go'){return ae(t,function(e){if(window.__onlygo)return;return f.call(this,e)},c)}
 return ae(t,f,c)}})();
(function(){var prev=window.Android;window.__g=[];window.__cpa='asking';window.__kind='NONE';
window.Android=new Proxy({},{get:function(t,k){return function(){var a=[].slice.call(arguments);window.__g.push([k].concat(a));
 if(k==='chatPhotoAsk')return window.__cpa;
 if(k==='detectIntent')return JSON.stringify({kind:window.__kind,blocked:false});
 if(k==='sendText')return 'sent';
 var f=prev[k];return f?f.apply(prev,a):undefined}}})})();
"""
bad = []
S = lambda st, **k: "window.__S=" + str(dict(dict(state=st, stage='VISION', done=0, total=0, percent=0, message='', error='', id='m1_2'), **k))
with sync_playwright() as p:
    b = p.chromium.launch(args=['--use-gl=swiftshader', '--no-sandbox'])
    for w, h in ((320, 640), (360, 740), (412, 800)):
        pg = b.new_page(viewport={'width': w, 'height': h}); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e)))
        pg.add_init_script(FAKE); pg.add_init_script(OVER); pg.add_init_script(OVER3); pg.goto('file://' + os.path.abspath(UI)); pg.wait_for_timeout(4500)
        pg.evaluate("[].slice.call(document.querySelectorAll('button')).forEach(function(x){if(/BAAD MEIN/.test(x.textContent))x.click()})")
        pg.evaluate("document.getElementById('chatb').click()"); pg.wait_for_timeout(1800)
        rows = pg.evaluate("[].slice.call(document.querySelectorAll('.m11_row')).map(function(r){return !!r.querySelector('.m13_btn')})")
        if rows != [True, True, False]: bad.append('%d: ANALYSE on rows %s (want photo, video, not link)' % (w, rows))
        # 1) start with the typed question; no event yet = bar 0
        pg.evaluate("document.getElementById('m13_q').value='ye kya hai?'")
        pg.evaluate(S('running', percent=0).replace('window.__S=', "window.__S=").replace("'", '"'))
        pg.evaluate("document.querySelectorAll('.m13_btn')[1].click()"); pg.wait_for_timeout(300)
        calls = pg.evaluate("window.__f")
        if not any(c[0] == 'analysisStart' and c[1] == 'm1_2' and c[2] == 'ye kya hai?' for c in calls): bad.append('%d: analysisStart not called with id + question' % w)
        bw0 = pg.evaluate("document.querySelector('#m13_bar i').style.width")
        if bw0 not in ('0%', '0px', ''): bad.append('%d: bar moved without an event: %s' % (w, bw0))
        # 2) real event: percent 50 -> bar 50%
        pg.evaluate("window.__S={state:'running',stage:'VISION',done:1,total:2,percent:50,message:'AI tasveer dekh raha hai 1/2',error:'',id:'m1_2'}"); pg.wait_for_timeout(900)
        bw = pg.evaluate("document.querySelector('#m13_bar i').style.width"); tx = pg.evaluate("document.getElementById('m13_txt').textContent")
        if bw != '50%' or 'dekh raha hai 1/2' not in tx: bad.append('%d: running shows bar %s text %s' % (w, bw, tx))
        if not pg.evaluate("document.getElementById('m13_stop').classList.contains('on')"): bad.append('%d: ROKO not visible while running' % w)
        sx = pg.evaluate("document.documentElement.scrollWidth>window.innerWidth+1")
        small = pg.evaluate("[document.getElementById('m13_stop'),document.getElementById('m13_q')].concat([].slice.call(document.querySelectorAll('.m13_btn'))).filter(function(x){return x.offsetParent!==null}).map(function(x){return Math.min(x.offsetWidth,x.offsetHeight)}).filter(function(n){return n<44}).length")
        if sx: bad.append('%d: sideways scroll' % w)
        if small: bad.append('%d: %d small tap targets' % (w, small))
        # 3) ROKO
        pg.evaluate("document.getElementById('m13_stop').click()"); pg.wait_for_timeout(600)
        if not any(c[0] == 'analysisCancel' for c in pg.evaluate("window.__f")): bad.append('%d: ROKO did not call cancel' % w)
        if 'Roka' not in pg.evaluate("document.getElementById('m13_txt').textContent"): bad.append('%d: cancelled text missing' % w)
        # 4) done: report text shown as TEXT (hostile html stays text)
        pg.evaluate("window.__T='Ek sadak hai <img src=x onerror=window.__xss=1>\\nBharosa: low - test';window.__S={state:'done',stage:'DONE',done:1,total:1,percent:100,message:'Report taiyar',error:'',id:'m1_2'}")
        pg.evaluate("document.querySelectorAll('.m13_btn')[1].click()"); pg.wait_for_timeout(1200)
        rt = pg.evaluate("document.getElementById('m13_res').textContent"); on = pg.evaluate("document.getElementById('m13_res').classList.contains('on')")
        if 'Ek sadak hai' not in rt or 'Bharosa: low' not in rt or not on: bad.append('%d: report text not shown (%r, on=%s)' % (w, rt[:40], on))
        if pg.evaluate("!!document.querySelector('#m13_res img')"): bad.append('%d: report text became HTML' % w)
        # 5) error
        pg.evaluate("window.__S={state:'error',stage:'FAILED',done:0,total:0,percent:0,message:'x',error:'AI ka jawab samajh nahi aaya.',id:'m1_2'}")
        pg.evaluate("document.querySelectorAll('.m13_btn')[1].click()"); pg.wait_for_timeout(800)
        if 'samajh nahi aaya' not in pg.evaluate("document.getElementById('m13_txt').textContent"): bad.append('%d: error text not shown' % w)
        if pg.evaluate("document.getElementById('m13_res').classList.contains('on')"): bad.append('%d: old report still shown after an error' % w)
        # 6) refusal (start returns a sentence, not ok)
        pg.evaluate("window.__start='Main kisi insaan ko chehre se pehchaan kar naam nahi batata.';window.__S={state:'idle',stage:'INTAKE',done:0,total:0,percent:0,message:'',error:'',id:''}")
        pg.evaluate("document.querySelectorAll('.m13_btn')[0].click()"); pg.wait_for_timeout(500)
        if 'chehre' not in pg.evaluate("document.getElementById('m13_txt').textContent"): bad.append('%d: refusal text not shown' % w)
        if pg.evaluate("!!window.__xss"): bad.append('%d: text ran as HTML' % w)
        # 7) v7.60 13D: chat photo + question (pt13d_chat). The photo thumb is shown by display only (the real attach path is Kotlin / pickPhoto).
        def press(txt):
            pg.evaluate("window.__g=[];document.getElementById('c_in').value=" + json.dumps(txt))
            pg.evaluate("document.getElementById('c_in').dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}))")
            pg.wait_for_timeout(250)
            return pg.evaluate("window.__g")
        names = lambda cs: [c[0] for c in cs]
        pg.evaluate("document.getElementById('c_thw').style.display='flex'")
        # A) a question + HAAN / NAHI box open ('asking'): chatPhotoAsk gets the text, the old send route is NOT run, the input is kept until HAAN
        pg.evaluate("window.__kind='NONE';window.__cpa='asking'")
        cs = press('ye kya hai?')
        if not any(c[0] == 'chatPhotoAsk' and c[1] == 'ye kya hai?' for c in cs): bad.append('%d: chatPhotoAsk not called with the question' % w)
        if 'sendText' in names(cs): bad.append('%d: old send route ran while the HAAN / NAHI box is open' % w)
        if pg.evaluate("document.getElementById('c_in').value") != 'ye kya hai?': bad.append('%d: input cleared before HAAN' % w)
        pg.evaluate("window.onChatPhotoGo()")
        if pg.evaluate("document.getElementById('c_in').value") != '': bad.append('%d: input not cleared after HAAN (onChatPhotoGo)' % w)
        # B) not a question: the old chat route goes on unchanged
        pg.evaluate("window.__cpa='notquestion'")
        cs = press('thanks')
        if 'chatPhotoAsk' not in names(cs) or 'sendText' not in names(cs): bad.append('%d: notquestion did not keep the old route (%s)' % (w, names(cs)))
        # C) refused / busy / NOVA off: one honest line shown as TEXT, old route not run, nothing sent
        pg.evaluate("window.__cpa='Main kisi insaan ko chehre se pehchaan kar naam nahi batata. <img src=x onerror=window.__xss=1>'")
        cs = press('ye kaun hai?')
        if 'sendText' in names(cs): bad.append('%d: old route ran after a refusal' % w)
        lastm = pg.evaluate("(function(){var m=document.querySelectorAll('#clog .msg');return m.length?m[m.length-1].textContent:''})()")
        if 'chehre se pehchaan' not in lastm: bad.append('%d: refusal line not shown in chat (%r)' % (w, lastm[:40]))
        if pg.evaluate("!!document.querySelector('#clog img[src=\"x\"]')") or pg.evaluate("!!window.__xss"): bad.append('%d: refusal text became HTML' % w)
        # D) edit / picture / website sentence (kind != NONE): never goes to chatPhotoAsk
        pg.evaluate("window.__kind='PHOTO_EDIT';window.__cpa='asking'")
        cs = press('isko black and white kar do?')
        if 'chatPhotoAsk' in names(cs): bad.append('%d: an edit sentence reached chatPhotoAsk' % w)
        # D2) v7.61 mutation-proof: all OTHER handlers off, only pt13d_chat's own kind guard decides
        pg.evaluate("window.__onlygo=true;window.__kind='PHOTO_EDIT';window.__cpa='asking'")
        cs = press('isko black and white kar do?')
        if 'chatPhotoAsk' in names(cs): bad.append('%d: pt13d_chat guard alone let an edit sentence reach chatPhotoAsk' % w)
        pg.evaluate("window.__onlygo=false")
        # E) no photo attached: chatPhotoAsk is never asked
        pg.evaluate("window.__kind='NONE';document.getElementById('c_thw').style.display='none'")
        cs = press('ye kya hai?')
        if 'chatPhotoAsk' in names(cs): bad.append('%d: chatPhotoAsk called without a photo' % w)
        if errs: bad.append('%d: JS errors %s' % (w, errs[:3]))
        pg.close()
    b.close()
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS p13 ui runtime incl. chat photo flow (headless render only, not a phone)')
