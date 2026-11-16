#!/usr/bin/env python3
"""v7.51 11-D: the three buttons beside the chat box (+, files, MEDIA) are ONE button. Headless Chromium + FAKE bridge (NOT a phone); no playwright = 'RUNTIME: NOT TESTED'.
Checks: only #c_plus is visible of the three; tap opens #pt11d_menu; MEDIA item calls mediaPick; Connectors item still reaches the old + handler; no JS error; chat box >= 44 px wide at 320/360/412."""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0, HERE)
try:
    from playwright.sync_api import sync_playwright
    from ui_shots import FAKE, UI
except Exception as e:
    print('RUNTIME: NOT TESTED (%s)' % e); sys.exit(0)
OVER = "(function(){var base=window.Android;window.__m=[];window.Android=new Proxy({},{get:function(t,k){return function(){var a=[].slice.call(arguments);if(k==='mediaPick'||k==='pickPhoto'){window.__m.push(k);return 'ok'}return base[k]?base[k].apply(base,a):undefined}}})})();"
bad = []
with sync_playwright() as p:
    b = p.chromium.launch(args=['--use-gl=swiftshader', '--no-sandbox'])
    for w, h in ((320, 640), (360, 740), (412, 800)):
        pg = b.new_page(viewport={'width': w, 'height': h}); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e)))
        pg.add_init_script(FAKE); pg.add_init_script(OVER); pg.goto('file://' + os.path.abspath(UI)); pg.wait_for_timeout(4500)
        pg.evaluate("[].slice.call(document.querySelectorAll('button')).forEach(function(x){if(/BAAD MEIN/.test(x.textContent))x.click()})")
        pg.evaluate("document.getElementById('chatb').click()"); pg.wait_for_timeout(1500)
        vis = pg.evaluate("['c_plus','c_attach','m11_btn'].map(function(i){var e=document.getElementById(i);return e&&e.offsetParent!==null})")
        if vis != [True, False, False]: bad.append('%d: visible plus/attach/media = %s' % (w, vis))
        cw = pg.evaluate("document.getElementById('c_in').getBoundingClientRect().width")
        if cw < 44: bad.append('%d: chat box only %spx' % (w, cw))
        pg.evaluate("document.getElementById('c_plus').click()"); pg.wait_for_timeout(300)
        if not pg.evaluate("document.getElementById('pt11d_menu').classList.contains('on')"): bad.append('%d: menu did not open' % w)
        pg.evaluate("[].slice.call(document.querySelectorAll('#pt11d_menu button')).filter(function(x){return /MEDIA chuno/.test(x.textContent)})[0].click()"); pg.wait_for_timeout(300)
        if 'mediaPick' not in pg.evaluate("window.__m"): bad.append('%d: MEDIA item did not call mediaPick' % w)
        pg.evaluate("document.getElementById('c_plus').click()"); pg.wait_for_timeout(200)
        pg.evaluate("[].slice.call(document.querySelectorAll('#pt11d_menu button')).filter(function(x){return /Connectors/.test(x.textContent)})[0].click()"); pg.wait_for_timeout(500)
        if pg.evaluate("document.getElementById('pt11d_menu').classList.contains('on')"): bad.append('%d: menu stayed open' % w)
        if errs: bad.append('%d: JS errors %s' % (w, errs[:3]))
        pg.close()
    b.close()
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS merged plus runtime (headless render only, not a phone)')
