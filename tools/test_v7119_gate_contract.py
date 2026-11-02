#!/usr/bin/env python3
"""v7.119 Part A: wake gate (v7.117) lives in WakeGateRules and NovaService uses it; chat content-visibility (v7.118) is present.
Static only: does NOT compile Kotlin and does NOT prove phone behaviour."""
import re, sys, pathlib
R = pathlib.Path(__file__).resolve().parent.parent
J = R/'app/src/main/java/com/nova/assistant'
ok = True
def chk(c, m):
    global ok
    print(('PASS ' if c else 'FAIL ') + m)
    ok = ok and c
rules = (J/'WakeGateRules.kt').read_text(encoding='utf-8')
svc = (J/'NovaService.kt').read_text(encoding='utf-8')
ui = (R/'app/src/main/assets/ui.html').read_text(encoding='utf-8')
test = (R/'app/src/test/java/com/nova/assistant/WakeGateRulesTest.kt').read_text(encoding='utf-8')
chk('const val HOLD_MS = 2000L' in rules and 'MIN_RMS = 300.0' in rules and 'NOISE_MULT = 1.8' in rules, 'constants equal the v7.117 values (2 s, 300, 1.8)')
for fn in ('voice', 'isOpen', 'extend', 'replayCount', 'closing'):
    chk('WakeGateRules.%s(' % fn in svc, 'NovaService calls WakeGateRules.%s' % fn)
chk('maxOf(300.0, noise * 1.8)' not in svc, 'no old inline gate threshold left in NovaService')
chk('wakeGateUntil' in svc and 'wakeGateWasOpen' in svc, 'gate state vars still declared')
chk(len(re.findall(r'@Test', test)) >= 8, 'WakeGateRulesTest has >= 8 tests')
chk(re.search(r'#clog \.msg\{[^}]*content-visibility:auto[^}]*contain-intrinsic-size', ui) is not None, 'chat messages use content-visibility:auto with an intrinsic size')
# the gate must sit in the NOT-capturing branch only, and the recognizer must still be fed when the gate is open
chk('val done = if (gateOpen) wk.acceptWaveForm(buf, n) else false' in svc, 'recognizer is fed only when the gate is open')
sys.exit(0 if ok else 1)
