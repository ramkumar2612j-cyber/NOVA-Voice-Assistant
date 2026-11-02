#!/usr/bin/env python3
"""v7.44 PART 7 static check: motion block added, nothing old removed, no network, no typed text read."""
import re,sys,pathlib
R=pathlib.Path(__file__).resolve().parent.parent
ui=(R/'app/src/main/assets/ui.html').read_text(encoding='utf8')
bad=[]
def need(c,m):
    print(('PASS ' if c else 'FAIL ')+m)
    if not c: bad.append(m)
need('id="p7_motion"' in ui and 'id="p7_motion_css"' in ui,'p7 css + js blocks exist')
need(ui.rstrip().endswith('</body></html>'),'ui.html still ends with </body></html>')
b=ui[ui.index('<style id="p7_motion_css">'):]
# v7.61: later parts (pt8/pt13d chat) legitimately use c_in after the p7 block, so only the p7 css + js block is scanned
_i=ui.index('<script id="p7_motion">'); b=b[:ui.index('</script>',_i)+9-ui.index('<style id="p7_motion_css">')]
need('c_in' not in b and 'fetch(' not in b and 'XMLHttpRequest' not in b,'p7: no typed-text read, no network')
need('.lowfx' in b and 'prefers-reduced-motion' in b,'p7: lowfx + reduced motion switch it off')
need("'p7_motion'" in ui,'page self-check lists p7_motion')
need("Android.vibrate" in b and 'nova_hap' in b,'haptics use old vibrate bridge, setting nova_hap')
need('#intro::after' in b and '#cap{' in b,'p7b: intro glass restyle present')
k=(R/'app/src/main/java/com/nova/assistant/NovaHud.kt').read_text(encoding='utf8')
need('bg.colors = if (lightBox) HudTheme.fillColors(true, false)' in k and '0xF2081A48.toInt()' in k and k.count('{')==k.count('}') and k.count('(')==k.count(')'),'p7b: HUD chat card glass gradient (dark glass kept, light branch added in v7.152), Kotlin braces/parens balanced')
for old in ['p6_home','p6_board','p6_dock','s_save','li_tot','t_home']:
    need(('id="%s"'%old in ui) or ("'%s'"%old in ui) or (old in ui),'old id still present: '+old)
print('p7 motion check:','PASS' if not bad else 'FAIL'); sys.exit(1 if bad else 0)
