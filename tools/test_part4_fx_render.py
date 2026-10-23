#!/usr/bin/env python3
"""v7.33 PART 4 headless check of the live NOVA core + touch starfield (fake Android bridge). Skips with exit 0 if playwright/chromium missing. NOT a phone test."""
import os, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'); UI = os.path.join(R, 'app/src/main/assets/ui.html')
try:
    from playwright.sync_api import sync_playwright
except Exception:
    print('SKIP (no playwright)'); sys.exit(0)
BR = """(function(){var R={getStatus:'{}',getSettings:'{}',getBrowserStatus:'{}',getConnectors:'[]',listChats:'[]',getTier:'high',getAutoReply:'{}',getSelfImproveCfg:'{}',getWaitSec:5};
window.Android=new Proxy({},{get:function(t,k){return function(a,b){
 if(k==='detectIntent'){var s=String(a).toLowerCase();if(b===true&&s.indexOf('background')>=0)return JSON.stringify({kind:'PHOTO_EDIT',subject:'BG_REMOVE',blocked:false});if(s.indexOf('kahani')>=0)return JSON.stringify({kind:'WRITE',subject:'',blocked:false});if(s.indexOf('nude')>=0)return JSON.stringify({kind:'IMAGE',subject:'',blocked:true});if(s.indexOf('website')>=0||s.indexOf('site')>=0)return JSON.stringify({kind:'SITE',subject:'gym',blocked:false});if(s.indexOf('logo')>=0)return JSON.stringify({kind:'IMAGE',subject:'x',blocked:false});return JSON.stringify({kind:'NONE'})}
 window.__calls=window.__calls||[];if(k==='editPhoto'||k==='makeImage'||k==='sendText')window.__calls.push(k+':'+a);
 if(k==='editPhoto')return 'started'; if(k==='makeImage')return 'started'; if(k==='sendText')return 'sent'; if(k==='running')return true;
 return R[k]!==undefined?R[k]:''}}})})();"""
bad = []
with sync_playwright() as p:
    try: b = p.chromium.launch()
    except Exception: print('SKIP (no chromium)'); sys.exit(0)
    pg = None
    os.makedirs(os.path.join(R, 'docs/ui_shots'), exist_ok=True)
    def count(pg, id):
        return pg.evaluate('(function(i){var c=document.getElementById(i),x=c.getContext("2d"),d=x.getImageData(0,0,c.width,c.height).data,n=0;for(var k=3;k<d.length;k+=16)if(d[k]>8)n++;return n})("%s")' % id)
    for (w, h) in [(320, 640), (360, 700), (412, 800), (800, 900)]:
        ctx = b.new_context(viewport={'width': w, 'height': h}, device_scale_factor=2)
        pg = ctx.new_page(); pg.add_init_script('try{localStorage.setItem("nova_q_mig1","1");localStorage.setItem("nova_q","auto")}catch(e){}'); pg.add_init_script(BR)
        pg.on('pageerror', lambda e: bad.append('js error: ' + str(e)[:150]))
        pg.goto('file://' + os.path.abspath(UI))
        try: pg.wait_for_function('document.body.classList.contains("ready")', timeout=25000)
        except Exception: bad.append('%dx%d never ready' % (w, h)); continue
        pg.evaluate('window.__forceLow=false;document.documentElement.classList.remove("lowfx")')
        pg.wait_for_timeout(1200)
        tag = '%dx%d' % (w, h)
        # elements + DPR sizes
        sz = pg.evaluate('(function(){var a=document.getElementById("p4_core"),s=document.getElementById("p4_stars");return a&&s?[a.width,a.height,s.width,s.height,innerWidth,innerHeight]:null})()')
        if not sz: bad.append(tag + ' p4 canvases missing'); continue
        if sz[0] != 500 or sz[1] != 500: bad.append(tag + ' core canvas not 250*DPR: %s' % sz[:2])
        if abs(sz[2] - sz[4] * 2) > 2 or abs(sz[3] - sz[5] * 2) > 2: bad.append(tag + ' star canvas not css*DPR: %s' % sz)
        if count(pg, 'p4_core') < 200: bad.append(tag + ' core canvas looks empty')
        if count(pg, 'p4_stars') < 20: bad.append(tag + ' star canvas looks empty')
        # old things kept but hidden
        o = pg.evaluate('["gl","orb3d","fut_bg","fut_ring"].map(function(i){var e=document.getElementById(i);return e?getComputedStyle(e).display:"MISSING"})')
        if 'MISSING' in o: bad.append(tag + ' old element removed: %s' % o)
        if o[0] != 'none' or o[1] != 'none': bad.append(tag + ' old orb still visible: %s' % o[:2])
        # core inside viewport, no sideways overflow
        r = pg.evaluate('(function(){var r=document.getElementById("p4_core").getBoundingClientRect();return [r.left,r.right,document.documentElement.scrollWidth,innerWidth]})()')
        if r[0] < -1 or r[1] > r[3] + 1: bad.append(tag + ' core outside screen %s' % r)
        if r[2] > r[3] + 1: bad.append(tag + ' horizontal overflow %s' % r)
        # states
        for cls, txt in [('wrap listen', 'LISTENING'), ('wrap think', 'THINKING'), ('wrap speak', 'SPEAKING'), ('wrap on', 'ONLINE'), ('wrap', 'STANDBY')]:
            got = pg.evaluate('document.getElementById("app").className="%s";window.__p4.state()' % cls)
            if got != txt: bad.append(tag + ' state %s gave %s' % (cls, got))
        pg.evaluate('document.getElementById("app").className="wrap listen"'); pg.wait_for_timeout(250)
        pg.screenshot(path=os.path.join(R, 'docs/ui_shots/p4_home_%s.png' % tag))
        # touch: finger near a star pushes it, tap makes ripple
        pg.evaluate('(function(){var u=document.getElementById("uc");if(u)u.classList.remove("on");Object.defineProperty(window,"__cover",{get:function(){return false},set:function(){}})})()')   # headless stub leaves the Update Center panel open; old sync() then sets __cover=true (not part of Part 4)
        if w == 360:
            st = pg.evaluate('(function(){var s=window.__p4.stars()[5];return [s.bx,s.by]})()')
            pg.evaluate('(function(x,y){document.dispatchEvent(new PointerEvent("pointerdown",{clientX:x,clientY:y,pointerType:"touch",bubbles:true}))})(%s,%s)' % (st[0] + 10, st[1] + 10)); pg.wait_for_timeout(250)
            moved = pg.evaluate('(function(){var s=window.__p4.stars();var m=0;for(var i=0;i<s.length;i++)m=Math.max(m,Math.abs(s[i].ox)+Math.abs(s[i].oy));return m})()')
            rip = pg.evaluate('window.__p4.rips()')
            for _i in range(5):
                pg.wait_for_timeout(150); moved = max(moved, pg.evaluate('(function(){var s=window.__p4.stars();var m=0;for(var i=0;i<s.length;i++)m=Math.max(m,Math.abs(s[i].ox)+Math.abs(s[i].oy));return m})()'))
            pg.evaluate('document.dispatchEvent(new PointerEvent("pointerup",{pointerType:"touch",bubbles:true}))')
            if moved < 1: bad.append(tag + ' touch did not move stars (%s) dbg=%s' % (moved, pg.evaluate('[window.__p4.ptr.on,window.__p4.ptr.x,window.__p4.ptr.y,window.__cover,document.documentElement.className,document.hidden]')))
            if rip < 1: bad.append(tag + ' tap made no ripple')
            pg.wait_for_timeout(2200)
            back = pg.evaluate('(function(){var s=window.__p4.stars();var m=0;for(var i=0;i<s.length;i++)m=Math.max(m,Math.abs(s[i].ox)+Math.abs(s[i].oy));return m})()')
            if back > moved * .35 + 0.5: bad.append(tag + ' stars did not spring back (%s -> %s)' % (moved, back))
            # tap on core still activates (same as the old orb)
            pg.evaluate('window.__calls=[];document.getElementById("btn").addEventListener("click",function(){window.__calls.push("btn")})')
            pg.evaluate('document.getElementById("p4_core").click()')
            if pg.evaluate('window.__calls.length') < 1: bad.append(tag + ' core tap did not press ACTIVATE')
            # lowfx: fewer stars
            n1 = pg.evaluate('window.__p4.active()')
            pg.evaluate('document.documentElement.classList.add("lowfx")'); pg.wait_for_timeout(300)
            n2 = pg.evaluate('window.__p4.active()')
            if not (n2 < n1 and n2 <= 45): bad.append(tag + ' lowfx star count %s -> %s' % (n1, n2))
        ctx.close()
    # reduced motion: still draws, stars do not move
    ctx = b.new_context(viewport={'width': 360, 'height': 700}, device_scale_factor=2, reduced_motion='reduce')
    pg = ctx.new_page(); pg.add_init_script('try{localStorage.setItem("nova_q_mig1","1");localStorage.setItem("nova_q","auto")}catch(e){}'); pg.add_init_script(BR); pg.on('pageerror', lambda e: bad.append('js error: ' + str(e)[:150]))
    pg.goto('file://' + os.path.abspath(UI)); pg.wait_for_function('document.body.classList.contains("ready")', timeout=25000)
    pg.evaluate('window.__forceLow=false;document.documentElement.classList.remove("lowfx")'); pg.wait_for_timeout(1200)
    if count(pg, 'p4_core') < 200 or count(pg, 'p4_stars') < 20: bad.append('reduced-motion: canvases empty')
    y0 = pg.evaluate('window.__p4.stars()[3].by'); pg.wait_for_timeout(600)
    if abs(pg.evaluate('window.__p4.stars()[3].by') - y0) > .01: bad.append('reduced-motion: stars still drifting')
    ctx.close(); b.close()
if bad:
    print('FAIL'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS part4 core + starfield (4 sizes, states, touch, tap, lowfx, reduced-motion, old ids kept)')
