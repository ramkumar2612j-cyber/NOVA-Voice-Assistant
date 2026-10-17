#!/usr/bin/env python3
"""Part 5 UI contract: duplicate-id fix (al_on/al_bat), 44px .sm rule, and a real-browser layout check
(Chromium + fake Android bridge) on Chat+HAAN/NAHI and Home at 320 and 360 wide. The browser part is SKIPPED
(not failed) when Playwright/Chromium is missing or NOVA_SKIP_BROWSER=1."""
import os, re, sys, collections, tempfile
here = os.path.dirname(os.path.abspath(__file__))
ui = open(os.path.join(here, '..', 'app/src/main/assets/ui.html'), encoding='utf-8').read()
ma = open(os.path.join(here, '..', 'app/src/main/java/com/nova/assistant/MainActivity.kt'), encoding='utf-8').read()
hud = open(os.path.join(here, '..', 'app/src/main/java/com/nova/assistant/NovaHud.kt'), encoding='utf-8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)

# 1. no duplicate ids at all for the two switches that used to clash
ids = collections.Counter(re.findall(r'\sid="([^"]+)"', ui))
need(ids['al_on'] == 1, 'al_on must appear exactly once')
need(ids['al_bat'] == 1, 'al_bat (battery switch) missing')
need('Android.getAlerts()' in ui and 'Android.setAlerts(' in ui, 'battery switch lost getAlerts/setAlerts')
need('Android.getAutoLearn()' in ui and 'Android.setAutoLearn(' in ui, 'Auto-seekho switch not wired')
need('fun getAutoLearn' in ma and 'fun setAutoLearn' in ma and 'fun getAlerts' in ma and 'fun setAlerts' in ma, 'bridge methods missing')

# 2. CSS: every .sm button is a 44px target; 320px tab label rule exists
need('min-height:var(--v76-tap)!important' in ui, '44px .sm rule missing')
need('#tabs .tb em{font-size:7.5px' in ui, '320px CONNECT label rule missing')

# 3. native HUD: STOP and HAAN/NAHI are separate, hit areas match the drawn rectangles, labels readable
need('btnStop.set(' in hud and 'hitControl' in hud and 'inside(btnStop' in hud, 'STOP hit area is not the drawn rect')
need('btnDots.set(' in hud and 'inside(btnDots' in hud, 'three dots hit area is not the drawn rect')
need('val bh = 44f * d' in hud and 'onAnswerTap?.invoke(true)' in hud and 'onAnswerTap?.invoke(false)' in hud, 'HAAN/NAHI 44dp or callbacks missing')
need('onStop?.invoke()' in hud and 'btnText.textSize = 11f * d' in hud, 'STOP callback/size missing')

# 4. browser check
if os.environ.get('NOVA_SKIP_BROWSER') == '1':
    print('SKIP browser part (NOVA_SKIP_BROWSER=1)')
else:
    try:
        sys.path.insert(0, here)
        import ui_screens
        out = tempfile.mkdtemp()
        rep = ui_screens.run(out, False, False, '320x568,360x640', 'home,chat_haan_nahi')
        for r in rep:
            tag = '%s %s' % (r['screen'], r['size'])
            need(not r['overflowX'], tag + ': sideways scroll')
            need(not r['small'], tag + ': small targets %s' % r['small'])
            need(not r['clipped'], tag + ': clipped text %s' % r['clipped'])
            need(not r['errors'], tag + ': %s' % r['errors'])
    except ImportError as e:
        print('SKIP browser part (Playwright missing):', e)
    except Exception as e:
        if 'Executable doesn' in str(e) or 'playwright install' in str(e):
            print('SKIP browser part (Chromium missing)')
        else:
            bad.append('browser check crashed: %s' % str(e)[:200])
if bad:
    print('FAIL'); [print(' -', b) for b in bad]; sys.exit(1)
print('PASS part5 ui screens contract')
