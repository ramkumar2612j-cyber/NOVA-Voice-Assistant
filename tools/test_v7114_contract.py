#!/usr/bin/env python3
"""v7.114 wiring guard: reel / friend jobs, saved family numbers, movie-name fix, no forced refresh rate."""
import os, re, sys
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(R, 'app/src/main/java/com/nova/assistant/')
rd = lambda p: open(p, encoding='utf-8').read()
ns, ma, ui = rd(J + 'NovaService.kt'), rd(J + 'MainActivity.kt'), rd(os.path.join(R, 'app/src/main/assets/ui.html'))
nn, mv = rd(J + 'NovaNotificationService.kt'), rd(J + 'MovieScanRules.kt')
bad = []
def need(c, m):
    if not c: bad.append(m)
need('ReelRules.parse(text' in ns and 'startReelPlan(reelPlan)' in ns, 'handle() must route reel sentences')
need(ns.count('reelCancel = true;') >= 2, 'both STOP paths must end a reel job')
need('FamilyContacts.lookup(this, q)' in ns, 'resolveContact must use saved numbers first')
need('FamilyContacts.parseSave(text)' in ns, 'voice save of a number')
need('ReelRunner.onNotification' in nn and 'reelWatchFire' in ns, 'friend-post watch wiring')
for f in ('getFamilyJson', 'setFamilyNumber'):
    need('fun ' + f in ma and f in ui, f + ' bridge + ui')
need('id="pt19_family"' in ui and 'id="fam_p"' in ui and 'id="fam_m"' in ui, 'family card in Settings')
need(re.search(r'private fun boostRefresh\(\) \{\s*if \(true\) return', ma) is not None, 'boostRefresh must not force the highest refresh rate')
need('TELL' in mv and 'WATCH_FUTURE' in mv, 'movie rule fix')
need('"HATAO"' in ui or "'HATAO'" in ui, 'remove button for saved people')
if bad:
    print('FAIL v7114 contract'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS v7114 contract')
