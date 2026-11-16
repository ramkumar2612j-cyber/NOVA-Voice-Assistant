#!/usr/bin/env python3
"""v7.47 PART 8 static check: chat UI block added, nothing old removed, no network, no typed text read, reduced-motion respected."""
import sys, pathlib
R = pathlib.Path(__file__).resolve().parent.parent
ui = (R / 'app/src/main/assets/ui.html').read_text(encoding='utf8')
bad = []
def need(c, m):
    print(('PASS ' if c else 'FAIL ') + m)
    if not c: bad.append(m)
need('id="pt8_chat_css"' in ui and 'id="pt8_chat"' in ui, 'pt8 css + js blocks exist')
need(ui.rstrip().endswith('</body></html>'), 'ui.html still ends with </body></html>')
_a = ui.index('<style id="pt8_chat_css">')
_j = ui.index('<script id="pt8_chat">')
b = ui[_a:ui.index('</script>', _j) + 9]   # v7.49: only the Part 8 blocks (later parts, e.g. Part 9 persona, are checked by their own scripts)
need('c_in' not in b and 'fetch(' not in b and 'XMLHttpRequest' not in b and 'Android.' not in b, 'pt8: no typed-text read, no network, no bridge call')
need('prefers-reduced-motion' in b and '.lowfx' in b, 'pt8: reduced-motion + lowfx handled')
need("data-fut" in b and 'SUN RAHA HU' in b and 'BOL RAHA HU' in b and 'SOCH RAHA HU' in b, 'pt8: waveform is tied to the real state class')
need('nodeValue' not in b and 'pt8_rev' not in b, 'pt8: no second typing reveal (p3_fx already has one)')
need("'pt8_chat'" in ui, 'page self-check lists pt8_chat')
need('p7_motion' in ui and 'p7_motion_css' in ui, 'Part 7 still present')
for old in ['clog', 'c_in', 'c_send', 'c_mic', 'c_plus', 'c_close', 'c_ai', 'p6_board', 'p6_dock', 's_save', 'ar_on']:
    need(('id="%s"' % old in ui) or (old in ui), 'old id still present: ' + old)
print('p8 chat check:', 'PASS' if not bad else 'FAIL'); sys.exit(1 if bad else 0)
