#!/usr/bin/env python3
"""v7.147 Step 7: KYA BAND HAI only reads and opens Settings pages; Kotlin rules, snapshot, bridge, voice route, UI block and tests are all connected."""
import os, re, sys
here = os.path.dirname(os.path.abspath(__file__))
J = os.path.join(here, '..', 'app/src/main/java/com/nova/assistant/')
def rd(f): return open(os.path.join(J, f), encoding='utf-8').read()
br, bs, fv, ma = rd('BlockedRules.kt'), rd('BlockedSnapshot.kt'), rd('FeatureVoice.kt'), rd('MainActivity.kt')
U = open(os.path.join(here, '..', 'app/src/main/assets/ui.html'), encoding='utf-8').read()
test = open(os.path.join(here, '..', 'app/src/test/java/com/nova/assistant/BlockedRulesTest.kt'), encoding='utf-8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)
for name, src in (('BlockedRules', br), ('BlockedSnapshot', bs)):
    code = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    for w in ['startActivity', 'putBoolean', 'edit()', 'requestPermissions', 'setComponentEnabled', 'Settings.System.put', 'Secure.put', 'grantRuntime']:
        need(w not in code, name + ' must stay read-only: found ' + w)
need('autostart' in br and '"check"' in br, 'autostart must be only a check row')
need('BlockedSnapshot.read(this@MainActivity)' in ma and 'fun fixBlocked(id: String)' in ma and 'fun getBlocked()' in ma, 'MainActivity bridge missing')
for i in ['acc', 'battery', 'overlay', 'notifAccess', 'write', 'dnd', 'install', 'autostart', 'mic', 'notif']:
    need('"%s"' % i in ma.split('fun fixBlocked')[1][:900], 'fixBlocked misses id ' + i)
need('"blocked"' in fv and 'BlockedSnapshot.read(ctx)' in fv, 'FeatureVoice route missing')
need('id="blk"' in U and 'id="blk_list"' in U and 'blk-v7147-js' in U, 'ui.html block missing')
need('Android.getBlocked()' in U and 'Android.fixBlocked(' in U, 'ui.html does not call the bridge')
need('"getBlocked", "fixBlocked"' in U, 'BRIDGE self-check list missing the new names')
for t in ['allOnGivesNoItemsAndAnHonestLine', 'autostartIsOnlyCheckNeverOffAndOnlyForBrandsWithAScreen', 'importantOnesComeFirstInFixedOrder']:
    need(t in test, 'JUnit missing: ' + t)
if bad:
    print('FAIL'); [print(' -', b) for b in bad]; sys.exit(1)
print('PASS step7 blocked contract')
