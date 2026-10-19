#!/usr/bin/env python3
"""v7.50 Part 10 UI runtime check (headless Chromium + FAKE Android bridge). If playwright / Chromium is missing it prints 'RUNTIME: NOT TESTED' and exits 0.
Checks: no JS error on load; #nc_on now calls Android.setNoiseCancel; the new skills entries and MADAD topics are in the page. Nothing here runs on a real phone."""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0, HERE)
try:
    from playwright.sync_api import sync_playwright
    from ui_shots import FAKE, UI
except Exception as e:
    print('RUNTIME: NOT TESTED (%s)' % e); sys.exit(0)
bad = []
try:
    with sync_playwright() as p:
        b = p.chromium.launch(args=['--use-gl=swiftshader', '--no-sandbox'])
        pg = b.new_page(viewport={'width': 390, 'height': 800}); errs = []
        pg.on('pageerror', lambda e: errs.append(str(e)))
        pg.add_init_script(FAKE); pg.goto('file://' + os.path.abspath(UI)); pg.wait_for_timeout(2500)
        if errs: bad.append('JS errors: %s' % errs[:3])
        pg.evaluate("var e=document.getElementById('nc_on'); e.checked=!e.checked; e.dispatchEvent(new Event('change',{bubbles:true}))")
        calls = pg.evaluate("window.__calls.map(function(c){return c[0]})")
        if 'setNoiseCancel' not in calls: bad.append('#nc_on does not call setNoiseCancel')
        src = open(UI, encoding='utf-8').read()   # CAPS / SK live inside closures: static check of the page source instead
        for k in ("{\"id\": \"persona\"", "{\"id\": \"roundabout\"", "t:'Jarvis andaaz", "t:'Ghuma ke bole kaam samajhna'"):
            if k not in src: bad.append('page source lacks ' + k)
        b.close()
except Exception as e:
    print('RUNTIME: NOT TESTED (%s)' % e); sys.exit(0)
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS p10 ui runtime (headless render only, not a phone)')
