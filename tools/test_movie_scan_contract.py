#!/usr/bin/env python3
"""v7.63 Part 14B-1 static contract: the "kon si movie hai" sentence is understood by pure rules, wired BEFORE the 13D analysis route, local only (no AI, no persona),
no network / no capture yet (that is 14B-2), people / actor questions are never matched, old routes still there (add-only)."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
J = 'app/src/main/java/com/nova/assistant/'
M = rd(J + 'MovieScanRules.kt'); S = rd(J + 'NovaService.kt'); T = rd('app/src/test/java/com/nova/assistant/MovieScanRulesTest.kt'); PR = rd(J + 'PersonaRules.kt'); G = rd('app/build.gradle.kts')
bad = []
for w in ('import android', 'HttpURLConnection', 'URL(', 'OkHttp', 'takeScreenshot', 'MediaProjection'):
    if w in M: bad.append('MovieScanRules must be pure (14B-1 has no capture / network): ' + w)
if 'MovieScanRules.parse(text) == MovieScanRules.Ask.MOVIE' not in S: bad.append('route not wired in NovaService')
else:
    if S.index('MovieScanRules.parse(text)') > S.index('AnalysisVoiceRules.parse(text)'): bad.append('movie route must run BEFORE the 13D analysis route')
    blk = S.split('MovieScanRules.parse(text) == MovieScanRules.Ask.MOVIE')[1].split('val askA = AnalysisVoiceRules.parse(text)')[0]
    if 'lastTurnLocal = true' not in blk or 'lastKind = "media"' not in blk: bad.append('movie route must be local and keep lastKind media (no persona)')
    if 'notYetText(' not in blk and 'startMovieScan()' not in blk: bad.append('route must answer with the honest not-yet text (14B-1) or startMovieScan (14B-2)')
    if re.search(r'Brain|askAi|callGemini|AnalysisPipeline', blk): bad.append('movie route must not call AI / pipeline here (14B-3)')
if 'AnalysisVoiceRules.parse(text)' not in S or 'MediaIntake.voiceIntent(text)' not in S: bad.append('old routes removed (add-only rule)')
if re.search(r'"media"', PR): bad.append('PersonaRules allow list contains media')
for k in ('"actor"', '"hero"', '"cast"', '"who"', 'SHARED', 'NOT_ASK'):
    if k not in M: bad.append('people / shared / play guard missing: ' + k)
if 'Ask.NONE' not in M or 'MAX_WORDS' not in M: bad.append('parse shape changed')
if 'notYetText' not in M or '14B' not in M.split('fun notYetText')[1][:400]: bad.append('not-yet text must say Part 14B')
if T.count('@Test') < 6: bad.append('MovieScanRulesTest has fewer than 6 tests')
if 'REFUSE_FACE_ID' not in T: bad.append('test must keep the face gate asserts')
mf = rd('app/src/main/AndroidManifest.xml')
if 'MovieScan' in mf: bad.append('no manifest change expected in 14B-1')
if bad:
    print('FAIL'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS movie scan 14B-1 contract (static; Kotlin compile + JUnit see local_compile_check; phone NOT TESTED)')
