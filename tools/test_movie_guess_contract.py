#!/usr/bin/env python3
"""v7.65 Part 14B-3 static contract: movie guess (vision AI + web search -> name + confidence + sources). Pure rules + pipeline (no Android / disk), title only from visible evidence,
NEVER a person from a face, HIGH only with >= 2 sources, no-search = LOW, search sends only short title queries, frames cleared, private screen refused, old parts still there (add-only)."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
J = 'app/src/main/java/com/nova/assistant/'
Ru = rd(J + 'MovieGuessRules.kt'); Pi = rd(J + 'MovieGuessPipeline.kt'); S = rd(J + 'NovaService.kt'); G = rd('app/build.gradle.kts')
T1 = rd('app/src/test/java/com/nova/assistant/MovieGuessRulesTest.kt'); T2 = rd('app/src/test/java/com/nova/assistant/MovieGuessPipelineTest.kt')
nocom = lambda s: re.sub(r'/\*.*?\*/', '', s, flags=re.S)
bad = []
for name, src in (('MovieGuessRules', Ru), ('MovieGuessPipeline', Pi)):
    for w in ('import android', 'HttpURLConnection', 'URL(', 'OkHttp', 'File(', 'FileOutputStream', 'writeBytes', 'MediaStore', 'takeScreenshot'):
        if w in src: bad.append(name + ' must be pure / memory only: ' + w)
# face rule
if 'NEVER identify a real person' not in Ru or 'Never give an actor' not in Ru: bad.append('system prompt must forbid naming a person from a face / actor names')
if 'identityClaim' not in Ru: bad.append('person filter (AnalysisRules.identityClaim) not used on title / evidence')
if 'untrusted' not in Ru: bad.append('screen text must be marked untrusted')
# confidence rule
m = re.search(r'hits >= 2 && written -> Level\.HIGH', Ru)
if not m: bad.append('HIGH needs >= 2 sources AND written evidence')
if 'else -> Level.LOW' not in Ru or 'if (searched) level(c, results) else Level.LOW' not in Ru: bad.append('no search / no source must be LOW')
if 'verify nahi hua' not in Ru: bad.append('LOW text must say verify nahi hua')
# pipeline
if Pi.index('ST_SEARCH, 55') < Pi.index('parseVision'): bad.append('search must come after the vision answer')
if 'b64.clear()' not in Pi or 'finally' not in Pi: bad.append('base64 copies must be cleared in finally')
sr = Pi.split('AnalysisSearchRules.runSearch(')[1].split('found = so.results')[0] if 'AnalysisSearchRules.runSearch(' in Pi else ''
if re.search(r'b64|frames|screenText|text\b', sr.replace('stage(ST_SEARCH', '')): bad.append('search must send only the short title queries (no picture, no screen text)')
if 'v.safety != "ok"' not in Pi or '"REFUSED"' not in Pi: bad.append('private screen must be refused before search')
if 'tryFreeAfter' not in Pi or 'callFree' not in Pi: bad.append('free vision fallback missing')
for k in ('"NO_AI"', '"LIMIT"', '"UNREADABLE"', '"CANCELLED"'):
    if k not in Pi: bad.append('pipeline outcome missing ' + k)
# service wiring
if 'startMovieScan' not in S: bad.append('startMovieScan missing')
else:
    blk = S.split('private fun startMovieScan')[1].split('private fun analyseByVoice')[0]
    if 'MovieGuessPipeline.run(' not in blk: bad.append('guess pipeline not wired')
    if blk.index('movieProtected() != false') > blk.index('MovieGuessPipeline.run('): bad.append('protected screen must be re-checked before anything leaves the phone')
    if 'movieFrames = emptyList()   // frames are gone' not in blk: bad.append('frames must be cleared right after the guess')
    if 'deliverMovie(' not in blk or 'private fun deliverMovie' not in S: bad.append('deliverMovie missing')
    if re.search(r'FileOutputStream|writeBytes|\.save\(', blk): bad.append('frames must never be saved')
if re.search(r'"media"', rd(J + 'PersonaRules.kt')): bad.append('PersonaRules allow list contains media')
# add-only: old symbols still there
for k in ('object MovieScanRules', 'fun notYetText', 'object MovieFrameRules', 'fun statusText', 'object MovieScanCapture'):
    if k not in rd(J + 'MovieScanRules.kt') + rd(J + 'MovieFrameRules.kt') + rd(J + 'MovieScanCapture.kt'): bad.append('old symbol removed: ' + k)
for k in ('AnalysisVoiceRules.parse(text)', 'MediaIntake.voiceIntent(text)', 'fun deliverAnalysis', 'fun analysisBrain'):
    if k not in S: bad.append('old route removed (add-only): ' + k)
if T1.count('@Test') < 10: bad.append('MovieGuessRulesTest has fewer than 10 tests')
if T2.count('@Test') < 10: bad.append('MovieGuessPipelineTest has fewer than 10 tests')
if 'REFUSED' not in T2 or 'offlineSearchGivesLowAndNoSources' not in T2: bad.append('pipeline tests lost key cases')
if bad:
    print('FAIL'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS movie guess 14B-3 contract (static; Kotlin compile + JUnit see local_compile_check; phone NOT TESTED)')
