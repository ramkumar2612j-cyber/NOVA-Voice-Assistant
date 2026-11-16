#!/usr/bin/env python3
"""v7.68 PART 16 MISSING WIRES (static contract, add-only). Evidence: static only, NOT a Kotlin compile, NOT phone tested.
1 ANSWER_PHONE_CALLS is asked at runtime in the one-time permission round (was manifest + check only) and SetupChainRules.VERSION >= 2
2 FeatureStatus has the callanswer row
3 HelpGuide has callanswer + calendar, both in PRIORITY, both mirrored in ui.html CAPS (test_help_contract checks equality)
4 tools/audit_dead_ui.py finds no dead control / missing help topic / retired-rule leftover"""
import os, re, subprocess, sys
H = os.path.dirname(os.path.abspath(__file__)); R = os.path.join(H, '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
KT = 'app/src/main/java/com/nova/assistant/'
ma, fs, hg, sc, ui = rd(KT + 'MainActivity.kt'), rd(KT + 'FeatureStatus.kt'), rd(KT + 'HelpGuide.kt'), rd(KT + 'SetupChainRules.kt'), rd('app/src/main/assets/ui.html')
man = rd('app/src/main/AndroidManifest.xml')
bad = []
def need(c, m):
    print(('PASS ' if c else 'FAIL ') + m)
    if not c: bad.append(m)
need('ask.add(Manifest.permission.ANSWER_PHONE_CALLS)' in ma, 'MainActivity asks ANSWER_PHONE_CALLS in the permission round')
need('ANSWER_PHONE_CALLS' in man, 'manifest declares ANSWER_PHONE_CALLS')
m = re.search(r'const val VERSION = (\d+)', sc)
need(bool(m) and int(m.group(1)) >= 2, 'SetupChainRules.VERSION >= 2 (old phones get one more round)')
need('row(a, "callanswer"' in fs, 'FeatureStatus has the callanswer row')
for t in ('callanswer', 'calendar'):
    need(('Topic("%s"' % t) in hg, 'HelpGuide topic ' + t)
    need(('"id": "%s"' % t) in ui, 'ui.html CAPS topic ' + t)
need('"callanswer", "calendar")' in hg, 'both new topics are in PRIORITY (the older "call" topic must not shadow them)')
r = subprocess.run([sys.executable, os.path.join(H, 'audit_dead_ui.py')], capture_output=True, text=True)
need(r.returncode == 0, 'audit_dead_ui.py: 0 real gaps')
r = subprocess.run([sys.executable, os.path.join(H, 'test_help_contract.py')], capture_output=True, text=True)
need(r.returncode == 0, 'help contract (Kotlin == ui.html topics)')
if bad:
    print('FAILED', len(bad)); sys.exit(1)
print('PASS part16 contract')
