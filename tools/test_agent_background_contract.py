#!/usr/bin/env python3
"""v6.18d: structural check (NOT a Kotlin run) that agent jobs keep the screen awake and refuse politely on a locked phone."""
import os, sys
here = os.path.dirname(os.path.abspath(__file__))
ns = open(os.path.join(here, '..', 'app/src/main/java/com/nova/assistant/NovaService.kt'), encoding='utf-8').read()
man = open(os.path.join(here, '..', 'app/src/main/AndroidManifest.xml'), encoding='utf-8').read()
bad = 0
def check(name, ok):
    global bad
    print(('PASS ' if ok else 'FAIL ') + name)
    if not ok: bad += 1
check('WAKE_LOCK permission declared', 'android.permission.WAKE_LOCK' in man)
check('wake lock taken before the worker thread starts', ns.index('agentWakeOn()\n        Thread({') > 0)
check('wake lock released in the finally block', 'finally {' in ns and 'agentTaskId = null\n                agentDeadlineAtMs = 0L\n                agentWakeOff()' in ns)
check('wake lock has a timeout backstop', 'wl.acquire(AgentRules.MAX_MS + 30000L)' in ns)
check('locked phone is refused, never bypassed', 'km.isKeyguardLocked) return tr(' in ns and 'agentBegin' in ns)
i = ns.index('private fun agentBegin'); j = ns.index('private fun agentStart')
check('lock check happens inside agentBegin before the job starts', 'isKeyguardLocked' in ns[i:j])
if bad: print(bad, 'FAILED'); sys.exit(1)
print('ALL PASSED (structural only; Kotlin not run)')
