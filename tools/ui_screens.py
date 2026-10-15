"""NOVA Part 5: screenshots of every main screen + simple layout checks (Chromium, FAKE Android bridge).
Usage: python3 tools/ui_screens.py <outdir> [--quick]
Checks per shot: sideways scroll, clipped text, tap targets < 44px, JS errors. Evidence = headless render only, not a real phone."""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ui_shots import FAKE, UI
from playwright.sync_api import sync_playwright

SIZES = [(320, 568), (360, 640), (412, 915), (820, 1180)]
SCREENS = [  # name, js that opens it, selector of the open screen (for checks)
 ('home', '', 'body'),
 ('featurestatus', "document.getElementById('fstat').scrollIntoView()", '#fstat'),
 ('chat', "document.getElementById('chatb').click()", '#chat'),
 ('chat_haan_nahi', "document.getElementById('chatb').click();window.__ask='Mummy ko call karun?';window.onAsk(window.__ask)", '#chat'),
 ('history', "document.getElementById('chatb').click();document.getElementById('c_hist').click()", '#hl'),
 ('help_madad', "document.getElementById('chatb').click();document.getElementById('c_help').click()", '#hw'),
 ('settings', "document.getElementById('gear').click()", '#panel'),
 ('settings_open', "document.getElementById('gear').click();setTimeout(function(){[].forEach.call(document.querySelectorAll('#panel .acch'),function(h){if(h.getAttribute('aria-expanded')!=='true')h.click()})},700)", '#panel'),
 ('connectors', "document.getElementById('t_conn').click()", '#v_conn'),
 ('update_center', "ucOpen()", '#uc'),
 ('skills', "document.getElementById('t_skills').click()", '#v_skills'),
 ('limits', "document.getElementById('t_limits').click()", '#v_limits'),
 ('sheet', "var f=document.querySelector('.f');if(f)f.click()", '#sheet'),
]
ON = {'chat': '#chat.on', 'chat_haan_nahi': '#chat.on', 'history': '#c_hl.on', 'help_madad': '#helpw.on', 'update_center': '#uc.on',
      'skills': '#v_skills.on', 'limits': '#v_limits.on', 'settings': '#panel[style*="block"]', 'settings_open': '#panel[style*="block"]', 'connectors': '#v_conn.on', 'sheet': '#sheet.on'}
CHECK = r"""(sel)=>{
 var root=document.querySelector(sel)||document.body, out={overflowX:false,small:[],clipped:[]};
 var de=document.documentElement; out.overflowX=de.scrollWidth>de.clientWidth+1;
 function vis(e){var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'&&s.opacity!=='0'}
 root.querySelectorAll('button,a[href],input:not([type=hidden]),select,textarea,[role=button]').forEach(function(e){
  if(!vis(e))return;var r=e.getBoundingClientRect();
  if((e.type==='checkbox'||e.type==='radio')&&e.closest('label'))return;   // the whole label row is the tap target
  var mw=e.offsetWidth||r.width,mh=e.offsetHeight||r.height;   // layout size: not shrunk by open/spring animations
  if(Math.min(mw,mh)<43.5)out.small.push((e.id||e.className||e.tagName)+':'+Math.round(r.width)+'x'+Math.round(r.height))});
 root.querySelectorAll('button,.msg,label,h2,h3,b,span,p').forEach(function(e){
  if(!vis(e))return;var s=getComputedStyle(e);
  if((s.overflow==='hidden'||s.textOverflow==='ellipsis')&&e.scrollWidth>e.clientWidth+2&&e.textContent.trim().length>2)out.clipped.push((e.id||e.className||e.tagName)+':'+e.textContent.trim().slice(0,30))});
 out.small=out.small.slice(0,12);out.clipped=out.clipped.slice(0,12);return out}"""


def run(outdir, quick=False, only=None, sizes_arg=None, screens_arg=None):
    os.makedirs(outdir, exist_ok=True)
    sizes = [(360, 640), (820, 1180)] if quick else SIZES
    if sizes_arg:
        sizes = [tuple(int(v) for v in x.split('x')) for x in sizes_arg.split(',')]
    screens = [x for x in SCREENS if not screens_arg or x[0] in screens_arg.split(',')]
    report = []
    with sync_playwright() as pw:
        b = pw.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
        for silent in (False, True):
            if only is not None and silent != only:
                continue
            tag = 'silentON' if silent else 'silentOFF'
            patch = "(function(){var A=window.Android;window.Android=new Proxy({},{get:function(t,k){if(k==='getSilentMode')return %s;if(k==='pendingAsk')return window.__ask||'';return A[k]}})})();" % ('true' if silent else 'false')
            for (w, h) in sizes:
                ctx = b.new_context(viewport={'width': w, 'height': h}, device_scale_factor=1)
                for name, js, sel in screens:
                    pg = ctx.new_page()
                    errs = []
                    pg.on('pageerror', lambda e: errs.append(str(e)))
                    pg.add_init_script(FAKE)
                    pg.add_init_script(patch)
                    pg.goto('file://' + os.path.abspath(UI))
                    try:
                        pg.wait_for_function("document.body.classList.contains('ready')", timeout=20000)
                    except Exception as e:
                        errs.append('not ready: ' + str(e)[:80])
                    pg.wait_for_timeout(2600)
                    if name != 'update_center':
                        pg.evaluate("var u=document.getElementById('uc');if(u)u.classList.remove('on')")   # Update Center can auto-pop over the screen under test
                    try:
                        if js:
                            pg.evaluate(js)
                    except Exception as e:
                        errs.append('open failed: ' + str(e)[:80])
                    pg.wait_for_timeout(2600 if name.startswith('settings') else 800)   # Settings panel fades in slowly
                    want = ON.get(name)
                    if want and not pg.query_selector(want):   # intro animation can swallow the first tap: retry up to 3 times
                        for _ in range(3):
                            pg.wait_for_timeout(1500)
                            try:
                                pg.evaluate(js)
                            except Exception:
                                pass
                            pg.wait_for_timeout(900)
                            if pg.query_selector(want):
                                break
                        if not pg.query_selector(want):
                            errs.append('screen did not open: ' + name)
                    res = pg.evaluate(CHECK, sel)
                    res.update(screen=name, size=f'{w}x{h}', silent=silent, errors=errs)
                    pg.screenshot(path=os.path.join(outdir, f'{name}_{w}_{tag}.png'))
                    report.append(res)
                    pg.close()
                ctx.close()
        b.close()
    json.dump(report, open(os.path.join(outdir, 'report_%s.json' % (('all' if only is None else ('on' if only else 'off')) + '_' + str(os.getpid()))), 'w'), indent=1)
    bad = [r for r in report if r['overflowX'] or r['small'] or r['clipped'] or r['errors']]
    print(f'{len(report)} shots, {len(bad)} with findings')
    for r in bad:
        print(r['screen'], r['size'], 'SILENT' if r['silent'] else '', {k: r[k] for k in ('overflowX', 'small', 'clipped', 'errors') if r[k]})
    return report


if __name__ == '__main__':
    only = True if '--silent-on' in sys.argv else (False if '--silent-off' in sys.argv else None)
    def opt(n):
        return sys.argv[sys.argv.index(n) + 1] if n in sys.argv else None
    run(sys.argv[1], '--quick' in sys.argv, only, opt('--sizes'), opt('--screens'))
