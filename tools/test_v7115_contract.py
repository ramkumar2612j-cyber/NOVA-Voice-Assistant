#!/usr/bin/env python3
"""v7.115 wiring guard: TV / room-talk wake guard, silent false wake + one-time setup help, offline website fallback."""
import os, re, sys
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(R, 'app/src/main/java/com/nova/assistant/')
T = os.path.join(R, 'app/src/test/java/com/nova/assistant/')
rd = lambda p: open(p, encoding='utf-8').read()
ns, sg = rd(J + 'NovaService.kt'), rd(J + 'SiteGen.kt')
so = rd(J + 'SiteOffline.kt')
bad = []
def need(c, m):
    if not c: bad.append(m)
need('WakeGuardRules.allow(txt, wakeWord,' in ns and 'WakeGuardRules.activeMisses(wakeMiss, wakeMissAt' in ns, 'wake loop must call WakeGuardRules.allow with the false-wake count')
need(ns.index('Logic.wakeVerified(txt, wakeWord)') < ns.index('WakeGuardRules.allow(txt, wakeWord,') < ns.index('Logic.wakeConfirmed(wakeHits, done)'), 'guard must sit between wakeVerified and wakeConfirmed')
need('HearFailRules.decide(' in ns and 'HearFailRules.Action.SLEEP' in ns and 'HearFailRules.Action.SETUP_HELP' in ns, 'junk branch must use HearFailRules')
need('if (!junk) junkStreak = 0' in ns, 'junk streak must reset on a readable capture')
need('offlineOut(subject, fullRequest)' in sg and 'SiteOffline.build(subject, fullRequest)' in sg, 'SiteGen must fall back to SiteOffline')
need('bestHtml.length < SiteQuality.MIN_LEN' in sg, 'stub AI page must be replaced')
need('isEdit) return Out(false, "", failMsg())' in sg, 'an EDIT must still fail honestly (never replace the owner page with a template)')
need('$' not in so.split('TEMPLATE = """')[1], 'template must have no $ (Kotlin string templates)')
for f in ('WakeGuardRulesTest.kt', 'HearFailRulesTest.kt', 'SiteOfflineTest.kt'):
    need(os.path.exists(T + f), f + ' missing')
need('hiVoiceVal' in ns and 'nowMs - hiVoiceAt in 0L..30_000L' in ns, 'testState must cache the TTS language check')
# the old guards stay (add-only)
need('Logic.wakeVerified' in ns and 'Logic.wakeConfirmed' in ns and 'WakeAdapt.scale' in ns, 'old wake guards must stay')
if bad:
    print('FAIL v7115 contract'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS v7115 contract')
