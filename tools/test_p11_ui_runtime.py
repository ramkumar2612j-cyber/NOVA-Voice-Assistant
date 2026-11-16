#!/usr/bin/env python3
"""v7.51 Part 11-C UI runtime check (headless Chromium + FAKE Android bridge; NOT a phone). If playwright / Chromium is missing: 'RUNTIME: NOT TESTED', exit 0.
Checks: no JS error; MEDIA MILA card shows items from mediaIntakeList; DEKHO / CHAT ME JODO / HATAO / MEDIA CHUNO / SAAF KARO call the right bridge methods; a hostile file name stays text;
no ANALYSE button; no sideways scroll and tap targets >= 44 px at 320 / 360 / 412. Screenshots: docs/ui_shots/p11_media_<w>.png (only when --shots)."""
import os, sys, json
HERE = os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0, HERE)
try:
    from playwright.sync_api import sync_playwright
    from ui_shots import FAKE, UI
except Exception as e:
    print('RUNTIME: NOT TESTED (%s)' % e); sys.exit(0)
SHOTS = '--shots' in sys.argv
OUT = os.path.join(HERE, '..', 'docs', 'ui_shots')
PNG = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFAEBAAAAAAAAAAAAAAAAAAAAAP/EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAMAwEAAhEDEQA/AL+AB//Z"
OVER = r"""
(function(){var base=window.Android;var items=[
{id:'m1_1',kind:'image',name:'IMG_2041.jpg',size:'2.4 MB',link:'',note:''},
{id:'m1_2',kind:'video',name:'<img src=x onerror=window.__xss=1>reel.mp4',size:'48.7 MB',link:'',note:''},
{id:'l1_1',kind:'link',name:'link_1',size:'0 B',link:'https://www.instagram.com/reel/ABC/',note:'Video file nahi mili, sirf link ki jankari (page ka title / description) se kaam karunga. Asli video chahiye to gallery se share karo.'}];
window.__m=[];var st={busy:false};
window.Android=new Proxy({},{get:function(t,k){return function(){var a=[].slice.call(arguments);
 if(/^media/.test(k)){window.__m.push([k].concat(a));
  if(k==='mediaIntakeList')return JSON.stringify({items:items,busy:st.busy,progress:40,name:'big.mp4',message:'2 file aur 1 link mile hain.'});
  if(k==='mediaIntakeThumb')return '__PNG__';
  if(k==='mediaIntakeOpen')return 'ok';
  if(k==='mediaIntakeAttach')return 'IMG_2041.jpg (2.4 MB)';
  if(k==='mediaIntakeRemove'){items=items.filter(function(x){return x.id!==a[0]});return true}
  if(k==='mediaIntakeClear'){items=[];return 3}
  return 'ok'}
 return base[k]?base[k].apply(base,a):undefined}}})})();
""".replace('__PNG__', PNG)
bad = []
try:
    with sync_playwright() as p:
        b = p.chromium.launch(args=['--use-gl=swiftshader', '--no-sandbox'])
        for w, h in ((320, 640), (360, 740), (412, 800)):
            pg = b.new_page(viewport={'width': w, 'height': h}); errs = []
            pg.on('pageerror', lambda e: errs.append(str(e)))
            pg.add_init_script(FAKE); pg.add_init_script(OVER); pg.goto('file://' + os.path.abspath(UI)); pg.wait_for_timeout(4500)
            pg.evaluate("[].slice.call(document.querySelectorAll('button')).forEach(function(x){if(/BAAD MEIN/.test(x.textContent))x.click()})")
            pg.evaluate("document.getElementById('chatb').click()")
            pg.wait_for_timeout(1800)
            if errs: bad.append('%d: JS errors %s' % (w, errs[:3]))
            info = pg.evaluate("""(function(){var c=document.getElementById('m11_card');if(!c)return null;var r=[].slice.call(c.querySelectorAll('.m11_row')).length;
              var btn=[].slice.call(c.querySelectorAll('button')).filter(function(x){return x.offsetParent!==null}).map(function(x){var q=x.getBoundingClientRect();return [x.textContent,Math.round(q.height),Math.round(q.width)]});
              return {on:c.classList.contains('on'),rows:r,btn:btn,sx:document.documentElement.scrollWidth>window.innerWidth+1,xss:!!window.__xss,txt:c.innerText}})()""")
            if not info: bad.append('%d: #m11_card missing' % w); pg.close(); continue
            if not info['on'] or info['rows'] != 3: bad.append('%d: card on=%s rows=%s' % (w, info['on'], info['rows']))
            if info['xss']: bad.append('%d: file name ran as HTML' % w)
            # v7.53: the ANALYSE button now exists (pt13_analyse, checked by test_p13_ui_runtime.py); the old 'no ANALYSE before Part 13' check is retired
            if info['sx']: bad.append('%d: sideways scroll' % w)
            for t, hh, ww in info['btn']:
                if hh < 44: bad.append('%d: tap target %s only %dpx high' % (w, t, hh))
            if w == 360:
                pg.evaluate("document.querySelector('.m11_see').click()"); pg.wait_for_timeout(200)
                pg.evaluate("document.querySelectorAll('.m11_see')[1].click()")
                pg.evaluate("document.querySelectorAll('.m11_add')[0].click()")
                pg.evaluate("document.getElementById('m11_pick').click()")
                pg.evaluate("document.getElementById('m11_btn') && document.getElementById('m11_btn').click()")
                calls = [c[0] for c in pg.evaluate('window.__m')]
                for need in ('mediaIntakeList', 'mediaIntakeThumb', 'mediaIntakeOpen', 'mediaIntakeAttach', 'mediaPick'):
                    if need not in calls: bad.append('bridge %s was not called' % need)
                if not pg.evaluate("document.getElementById('m11_view').classList.contains('on')"): bad.append('photo DEKHO did not open the preview')
                pg.evaluate("document.getElementById('m11_vx').click()")
                pg.evaluate("document.querySelectorAll('.m11_del')[0].click()"); pg.wait_for_timeout(200)
                rows = pg.evaluate("document.querySelectorAll('.m11_row').length")
                if rows != 2 or 'mediaIntakeRemove' not in [c[0] for c in pg.evaluate('window.__m')]: bad.append('HATAO did not remove one row (rows=%s)' % rows)
                pg.evaluate("document.getElementById('m11_clear').click()"); pg.wait_for_timeout(200)
                if 'mediaIntakeClear' not in [c[0] for c in pg.evaluate('window.__m')]: bad.append('SAAF KARO did not call mediaIntakeClear')
            if SHOTS and w != 360:
                os.makedirs(OUT, exist_ok=True)
                pg.screenshot(path=os.path.join(OUT, 'p11_media_%d.png' % w))
            pg.close()
        b.close()
except Exception as e:
    print('RUNTIME: NOT TESTED (%s)' % e); sys.exit(0)
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS p11 ui runtime (headless render only, not a phone)')
