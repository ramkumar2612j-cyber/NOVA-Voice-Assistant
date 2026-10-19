#!/usr/bin/env python3
"""v7.62 Part 14 GREEN LASER SCAN: static contract + headless render (FAKE bridge, NOT a phone). Overlay only while a REAL analysis runs; number + stage chip from the real status;
ROKO calls analysisCancel; touch-through; no network, no typed-text read; lowfx / reduced motion switch the canvas off; old blocks untouched. No playwright = runtime part 'NOT TESTED', exit 0."""
import os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__)); R = os.path.join(HERE, '..')
U = open(os.path.join(R, 'app/src/main/assets/ui.html'), encoding='utf-8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)
need('<script id="pt14_laser">' in U and '<style id="pt14_laser_css">' in U, 'pt14 css + js blocks missing')
need(U.rstrip().endswith('</body></html>'), 'ui.html must end with </body></html>')
b = U[U.index('<style id="pt14_laser_css">'):U.rindex('</body></html>')]
need('fetch(' not in b and 'XMLHttpRequest' not in b and 'c_in' not in b and 'm13_q' not in b and '.value' not in b, 'pt14: no network, no typed text read')
need('innerHTML' not in b, 'pt14: textContent only')
need('Android.analysisStatus' in b and 'Android.analysisCancel' in b, 'pt14 must use analysisStatus + analysisCancel only')
need(len(re.findall(r'Android\.(\w+)', b)) and set(re.findall(r'Android\.(\w+)', b)) <= {'analysisStatus', 'analysisCancel'}, 'pt14 uses another bridge call')
need('pointer-events:none' in b and 'pointer-events:auto' in b, 'overlay must be touch-through except ROKO')
need('.lowfx' in b and 'prefers-reduced-motion' in b, 'lowfx + reduced motion switch')
need("'pt14_laser'" in U, 'page self-check lists pt14_laser')
for old in ('pt13_analyse', 'pt13d_chat', 'p7_motion', 'pt8_chat'):
    need('id="%s"' % old in U, 'old block missing: ' + old)
try:
    from playwright.sync_api import sync_playwright
    sys.path.insert(0, HERE); from ui_shots import FAKE, UI
    src = open(os.path.join(HERE, 'test_p13_ui_runtime.py'), encoding='utf-8').read(); ns = {}
    exec(src[src.index('PNG = '):src.index('bad = []')], ns)
    with sync_playwright() as p:
        br = p.chromium.launch(args=['--use-gl=swiftshader', '--no-sandbox'])
        for w, h in ((320, 640), (360, 740), (412, 800)):
            pg = br.new_page(viewport={'width': w, 'height': h}); errs = []
            pg.on('pageerror', lambda e: errs.append(str(e)))
            pg.add_init_script(FAKE); pg.add_init_script(ns['OVER']); pg.goto('file://' + os.path.abspath(UI)); pg.wait_for_timeout(4500)
            on = lambda: pg.evaluate("document.getElementById('p14_scan').classList.contains('on')")
            if on(): bad.append('%d: overlay visible while idle' % w)
            pg.evaluate("window.__S={state:'running',stage:'FRAMES',done:1,total:4,percent:25,message:'Frames nikal raha hoon',error:'',id:'m1_2'}"); pg.wait_for_timeout(1700)
            if not on(): bad.append('%d: overlay not shown while running' % w)
            if pg.evaluate("document.querySelector('#p14_scan .pc').textContent") != '25%': bad.append('%d: percent is not the real 25%%' % w)
            ch = pg.evaluate("[].slice.call(document.querySelectorAll('#p14_scan .sg b')).map(function(b){return b.className})")
            if ch != ['dn', 'act', '', '', '']: bad.append('%d: stage chips %s (want INTAKE done, FRAMES active)' % (w, ch))
            if pg.evaluate("getComputedStyle(document.getElementById('p14_scan')).pointerEvents") != 'none': bad.append('%d: overlay blocks touches' % w)
            if pg.evaluate("document.documentElement.scrollWidth>window.innerWidth+1"): bad.append('%d: sideways scroll' % w)
            r = pg.evaluate("(function(){var b=document.getElementById('p14_stop').getBoundingClientRect();return [b.width,b.height,b.bottom,innerHeight]})()")
            if min(r[0], r[1]) < 44 or r[2] > r[3]: bad.append('%d: ROKO small or off screen %s' % (w, r))
            pg.evaluate("window.__S={state:'running',stage:'SEARCH',done:3,total:4,percent:75,message:'Web par jaanch',error:'',id:'m1_2'}"); pg.wait_for_timeout(900)
            if pg.evaluate("document.querySelector('#p14_scan .pc').textContent") != '75%': bad.append('%d: percent did not follow the status' % w)
            pg.evaluate("document.getElementById('p14_stop').click()"); pg.wait_for_timeout(500)
            if not any(c[0] == 'analysisCancel' for c in pg.evaluate("window.__f")): bad.append('%d: ROKO did not call analysisCancel' % w)
            pg.evaluate("window.__S={state:'done',stage:'DONE',done:1,total:1,percent:100,message:'Report taiyar',error:'',id:'m1_2'}"); pg.wait_for_timeout(2600)
            if on(): bad.append('%d: overlay stayed after done' % w)
            pg.evaluate("window.__S={state:'running',stage:'VISION',done:0,total:2,percent:0,message:'x',error:'',id:'m1_3'}"); pg.wait_for_timeout(1700)
            pg.evaluate("document.documentElement.classList.add('lowfx')")
            if pg.evaluate("getComputedStyle(document.querySelector('#p14_scan canvas')).display") != 'none': bad.append('%d: lowfx keeps the matrix canvas' % w)
            pg.evaluate("window.__S={state:'error',stage:'FAILED',done:0,total:0,percent:0,message:'',error:'boom',id:'m1_3'}"); pg.wait_for_timeout(2000)
            if on(): bad.append('%d: overlay stayed after error' % w)
            if errs: bad.append('%d: JS errors %s' % (w, errs[:2]))
            pg.close()
        br.close()
    rt = 'headless render PASS (not a phone)'
except ImportError:
    rt = 'RUNTIME: NOT TESTED (no playwright)'
if bad:
    print('FAIL'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS p14 laser contract; ' + rt)
