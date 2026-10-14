#!/usr/bin/env python3
"""v6.65 structural check (does NOT run Kotlin): the page learns LOW / MID / HIGH from the app and scales effects, orb quality and canvas sharpness."""
import os
here = os.path.dirname(os.path.abspath(__file__))
ui = open(os.path.join(here, '..', 'app/src/main/assets/ui.html'), encoding='utf-8').read()
ma = open(os.path.join(here, '..', 'app/src/main/java/com/nova/assistant/MainActivity.kt'), encoding='utf-8').read()
first_script = ui.find('<script')
chk = [
    ('tier script runs before every other script', ui.find('<script id="tier-init"') == first_script),
    ('bridge method getTier exists', 'fun getTier(): String' in ma and 'DeviceTier.of(' in ma),
    ('low phone is forced to light mode', 'if(t==="low"){window.__forceLow=true' in ui),
    ('orb starts lower on low and mid', 'si=(window.__tier==="low"?2:window.__tier==="mid"?1:0),FLOOR=si' in ui),
    ('orb never recovers above its tier floor', 'if(si>FLOOR)si--' in ui),
    ('orb capped to 30 fps unless HIGH', "window.__tier!=='high')?33:16" in ui),
    ('canvas sharpness capped by tier', ui.count('window.__dprCap') >= 4),
]
bad = [n for n, ok in chk if not ok]
print('FAIL: ' + ', '.join(bad) if bad else 'ALL PASSED (%d checks, structural only)' % len(chk))
raise SystemExit(1 if bad else 0)
