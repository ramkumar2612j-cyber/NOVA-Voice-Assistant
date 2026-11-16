#!/usr/bin/env python3
"""v7.43 PART 6 static check (no browser needed): home board / dock added, nothing old removed."""
import re, sys
ui = open('app/src/main/assets/ui.html', encoding='utf8').read()
ok = True
def chk(name, cond):
    global ok
    print(('PASS ' if cond else 'FAIL ') + name)
    ok = ok and bool(cond)
for i in ['p6_home', 'p6_home_css']:
    chk('block ' + i, 'id="%s"' % i in ui)
for i in ['btn', 'quiet', 'silent', 'chatb', 'gear', 'st', 'autoT', 'gl', 'orb3d', 'fstat', 'feats', 'log']:
    chk('old id still present: ' + i, 'id="%s"' % i in ui)
chk('old row hidden, not removed', '#app>.row{display:none!important}' in ui and '<div class="row"><button id="btn">ACTIVATE</button>' in ui)
chk('orb has NO INTERNET state', "t:'NO INTERNET'" in ui and "/p6off/.test(k)" in ui)
chk('old orb states kept', all(x in ui for x in ["t:'SPEAKING'", "t:'THINKING'", "t:'LISTENING'", "t:'ONLINE'", "t:'STANDBY'"]))
chk('dock presses old buttons / old stop sentence', "press('chatb')" in ui and "press('quiet')" in ui and "press('silent')" in ui and "sendText('stop')" in ui)
chk('board uses only existing read bridges', "J('getStatus')" in ui and "J('getSettings')" in ui)
chk('reduced motion + lowfx respected', 'html:not(.lowfx) .p6c.bad i' in ui and 'prefers-reduced-motion' in ui[ui.index('id="p6_home_css"'):])
chk('refresh paused when hidden / covered', 'd.hidden||window.__cover' in ui[ui.index('id="p6_home"'):])
chk('no new network / no storage in p6', not re.search(r'fetch\(|XMLHttpRequest|localStorage', ui[ui.index('id="p6_home"'):ui.index('</script>', ui.index('id="p6_home"'))]))
chk('page self-check knows the new ids', "'p6_board','p6_dock'" in ui and "'p6_home'" in ui)
print('p6 home check:', 'PASS' if ok else 'FAIL')
sys.exit(0 if ok else 1)
