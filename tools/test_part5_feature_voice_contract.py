#!/usr/bin/env python3
"""Part 5: the spoken feature-status route is READ-ONLY, is hooked into NovaService, has JUnit tests, and FeatureStatus has the new rows."""
import os, re, sys
here = os.path.dirname(os.path.abspath(__file__))
J = os.path.join(here, '..', 'app/src/main/java/com/nova/assistant/')
def rd(f): return open(os.path.join(J, f), encoding='utf-8').read()
fv, svc, fs = rd('FeatureVoice.kt'), rd('NovaService.kt'), rd('FeatureStatus.kt')
test = open(os.path.join(here, '..', 'app/src/test/java/com/nova/assistant/FeatureVoiceTest.kt'), encoding='utf-8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)
code = re.sub(r'/\*.*?\*/', '', fv, flags=re.S)
for w in ['startActivity', 'ACTION_CALL', 'sendTextMessage', 'putBoolean', 'edit()', '.setAlerts', '.setLockGuard', 'setAutoLearn', 'setSilentMode', 'Thread(', 'removeSkill']:
    need(w not in code, 'FeatureVoice must stay read-only: found ' + w)
need('fun key(' in fv and 'fun reply(' in fv and 'fun read(' in fv, 'FeatureVoice api missing')
need('ORDER_WORDS' in fv and 'STATUS_CUE' in fv, 'order/status guards missing')
need('FeatureVoice.key(text)' in svc and 'FeatureVoice.read(this, fv)' in svc, 'NovaService hook missing')
need(svc.index('FeatureVoice.key(text)') < svc.index('SourceMap.isAsk(text)'), 'hook must come before SourceMap')
need(svc.index('HelpGuide.isAsk(text)') < svc.index('FeatureVoice.key(text)'), 'HelpGuide route must stay first')
for k in ['"autoreply"', '"lockguard"', '"batteryalert"', '"offlinepack"', '"routines"', '"apilimits"']:
    need(k in fv, 'missing key ' + k)
for k in ['lockguard', 'autoreply', 'batteryalert', 'offlinepack', 'apilimits', 'routines']:
    need('"%s"' % k in fs, 'FeatureStatus row missing: ' + k)
for t in ['statusQuestionsAreRecognised', 'ordersAreNeverCaptured', 'plainCommandsAndChatAreNotCaptured', 'repliesAreHonestAboutState']:
    need(t in test, 'JUnit missing: ' + t)
if bad:
    print('FAIL'); [print(' -', b) for b in bad]; sys.exit(1)
print('PASS part5 feature voice contract')
