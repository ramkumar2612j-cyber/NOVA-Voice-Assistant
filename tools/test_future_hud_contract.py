#!/usr/bin/env python3
"""v7.03 future HUD static contract: ids exist, no $('literal') in the new block, lowfx rules, onNova wrapper, speak_end emitted by NovaService."""
import re, os, sys
H = os.path.dirname(os.path.abspath(__file__)); R = os.path.join(H, '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf8').read()
ui = rd('app/src/main/assets/ui.html'); ns = rd('app/src/main/java/com/nova/assistant/NovaService.kt')
bad = 0
def chk(n, c):
    global bad; print(('PASS ' if c else 'FAIL ') + n); bad += 0 if c else 1
for i in ('fut_hud', 'fut_edge', 'fut_bg', 'fut_scan'): chk('id ' + i, ("id='%s'" % i) in ui or ('id="%s"' % i) in ui)
a = ui.find('nova-future-css'); b = ui.find('nova-future-js')
chk('blocks exist', a >= 0 and b > a)
css = ui[a:b]; js = ui[b:ui.find('</script>', b)]
chk('lowfx rule in css or js', 'lowfx' in css or 'lowfx' in js)
chk("no $('literal') in future js", not re.search(r"(?<![\w.])\$\('", js))
chk('onNova wrapper', 'onNova' in js)
chk('data-fut state attr', 'data-fut' in js)
chk('NovaService emits speak and speak_end', 'emit("mode", "speak")' in ns.replace('emit("mode","speak")', 'emit("mode", "speak")') and 'speak_end' in ns)
sys.exit(1 if bad else 0)
