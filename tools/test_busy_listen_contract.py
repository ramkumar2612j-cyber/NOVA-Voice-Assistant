#!/usr/bin/env python3
"""v7.73 voice-while-busy contract (static; NOT a compile, NOT a phone test).
1 BusyListenRules + ExtCfg.busyListen exist  2 mic loop keeps listening only via BusyListenRules.mayListen (speaking / lock / waiting-for-answer still dropped)
3 busy voice goes to cmdQ with voice=true, STOP works  4 Lock Guard branch untouched (locked phone = nothing is listened to)  5 JUnit file exists  6 version 7.73"""
import re
import os, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
A = 'app/src/main/java/com/nova/assistant/'
bad = []
def need(c, m):
    print(('PASS ' if c else 'FAIL ') + m)
    if not c: bad.append(m)
ns, br, ec = rd(A + 'NovaService.kt'), rd(A + 'BusyListenRules.kt'), rd(A + 'ExtCfg.kt')
need('fun mayListen(' in br and '!ttsActive' in br and '!lockQuiet' in br and '!waitingForAnswer' in br, 'mayListen refuses speaking / locked / waiting for an answer')
need('fun busyListen(' in ec and '"busy_listen", true' in ec, 'ExtCfg.busyListen default ON')
need('BusyListenRules.mayListen(' in ns and 'if (busy || ttsActive) {\n                    if (!busyListenOk) {' in ns, 'mic loop uses mayListen, speaking still dropped')
need('BusyListenRules.decide(' in ns and 'cmdQ.addAll(items, { isLocalCmd(it) }, true)' in ns, 'busy voice -> cmdQ voice=true')
need('BusyListenRules.Action.STOP -> { softStop()' in ns, 'busy voice STOP works')
need('if (lockQuiet()) {           // v6.97-wip9 LOCK GUARD' in ns, 'Lock Guard branch still there (owner said NO to locked-phone wake)')
i = ns.find('if (lockQuiet()) {           // v6.97-wip9 LOCK GUARD'); j = ns.find('BusyListenRules.mayListen(')
need(0 < j < i, 'busy-listen check comes BEFORE the lock guard drop, and the lock guard still drops after it')
need(os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/BusyListenRulesTest.kt')), 'JUnit file exists')
need('versionName = "7.73"' in rd('app/build.gradle.kts') or 'versionName = "7.74"' in rd('app/build.gradle.kts') or re.search(r'versionName = "7\.(7[5-9]|[89][0-9])"', rd('app/build.gradle.kts')), 'versionName 7.73 or later')
if bad: print('FAILED', len(bad)); sys.exit(1)
print('PASS busy listen contract')
