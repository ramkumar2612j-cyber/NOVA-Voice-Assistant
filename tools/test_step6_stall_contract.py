#!/usr/bin/env python3
"""v7.146 Step 6: the main-thread watchdog only RECORDS, is started once in NovaApp, is read-only in the voice route, and has JUnit tests."""
import os, re, sys
here = os.path.dirname(os.path.abspath(__file__))
J = os.path.join(here, '..', 'app/src/main/java/com/nova/assistant/')
def rd(f): return open(os.path.join(J, f), encoding='utf-8').read()
wd, sr, fv, app = rd('MainStallWatchdog.kt'), rd('StallRules.kt'), rd('FeatureVoice.kt'), rd('CrashNote.kt')
test = open(os.path.join(here, '..', 'app/src/test/java/com/nova/assistant/StallRulesTest.kt'), encoding='utf-8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)
code = re.sub(r'/\*.*?\*/', '', wd, flags=re.S)
for w in ['killProcess', 'exitProcess', 'System.exit', 'interrupt()', 'startActivity', 'startService', 'Process.', 'sendBroadcast', 'putString("lang']:
    need(w not in code, 'watchdog must only record: found ' + w)
need('isDaemon = true' in wd, 'watchdog thread must be a daemon')
need('compareAndSet(false, true)' in wd, 'watchdog must start only once')
need(wd.count('catch (e: Throwable)') >= 3, 'watchdog must swallow its own errors')
need('MainStallWatchdog.start(this)' in app and 'class NovaApp' in app, 'NovaApp must start the watchdog')
need('"mainstall"' in fv and 'MainStallWatchdog.read(ctx)' in fv, 'FeatureVoice mainstall route missing')
need('ErrLog.add("hang"' in wd, 'ErrLog line missing')
need('com.nova.assistant' in sr and 'fun reply(' in sr and 'fun isStall(' in sr, 'StallRules api missing')
for t in ['stallStartsAtThreshold', 'frozenPhoneIsNotAStall', 'ringKeepsOnlyTheNewestTen', 'replyIsHonestWhenEmptyAndCountsWhenNot']:
    need(t in test, 'JUnit missing: ' + t)
if bad:
    print('FAIL'); [print(' -', b) for b in bad]; sys.exit(1)
print('PASS step6 stall watchdog contract')
