#!/usr/bin/env python3
"""v6.24: structural check (Kotlin NOT run) that the crash black box and the accessibility hardening are wired."""
import os, sys
here = os.path.dirname(os.path.abspath(__file__))
b = os.path.join(here, '..', 'app/src/main/')
def r(p): return open(b + p, encoding='utf-8').read()
man = r('AndroidManifest.xml'); acc = r('java/com/nova/assistant/NovaAccessibilityService.kt')
st = r('java/com/nova/assistant/SelfTest.kt'); cn = r('java/com/nova/assistant/CrashNote.kt')
bad = 0
def check(n, ok):
    global bad
    print(('PASS ' if ok else 'FAIL ') + n)
    if not ok: bad += 1
check('manifest uses the NovaApp class', 'android:name=".NovaApp"' in man)
check('NovaApp installs the crash note', 'class NovaApp : Application()' in cn and 'CrashNote.install(this)' in cn)
check('crash handler chains to the normal handler', 'prev.uncaughtException(t, e)' in cn)
check('crash text is masked through ErrLog.clean', 'ErrLog.clean(e.message' in cn)
check('no network in CrashNote', 'http' not in cn.lower() and 'URL(' not in cn)
check('service records connected/unbound/destroyed', all(('CrashNote.event(applicationContext, "%s")' % w) in acc for w in ('connected', 'unbound', 'destroyed')))
check('events handler cannot crash the app', 'try { handleEvent(e) } catch (x: Exception)' in acc)
check('scan and tick are guarded', 'try { scanBody() } catch (e: Exception)' in acc and 'ErrLog.add("accessibility", "tick "' in acc)
check('report shows crash and events', 'CrashNote.report(act)' in st)
if bad: print(bad, 'FAILED'); sys.exit(1)
print('ALL PASSED (structural only; Kotlin not run)')
