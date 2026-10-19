#!/usr/bin/env python3
"""v7.64 Part 14B-2 static contract: live screen capture for the movie scan. Pure rules + loop (no Android), protected-screen gate before EVERY frame and after the shot,
frames memory-only (no file write, no network), STOP cancels, accessibility hook refuses protected screens, no face / name guess here (14B-3), add-only."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
J = 'app/src/main/java/com/nova/assistant/'
F = rd(J + 'MovieFrameRules.kt'); C = rd(J + 'MovieScanCapture.kt'); S = rd(J + 'NovaService.kt'); A = rd(J + 'NovaAccessibilityService.kt')
T = rd('app/src/test/java/com/nova/assistant/MovieScanCaptureTest.kt'); G = rd('app/build.gradle.kts'); mf = rd('app/src/main/AndroidManifest.xml')
bad = []
for name, src in (('MovieFrameRules', F), ('MovieScanCapture', C)):
    for w in ('import android', 'HttpURLConnection', 'URL(', 'OkHttp', 'takeScreenshot', 'File(', 'FileOutputStream', 'writeBytes', 'MediaStore'):
        if w in src: bad.append(name + ' must be pure / memory only: ' + w)
if 'gateSource' not in F: bad.append('frame gate must use AnalysisRules.gateSource')
if C.count('src.foregroundProtected()') < 2: bad.append('protected check must run before AND after every shot')
if 'Stop.PROTECTED' not in C or 'emptyList()' not in C.split('Stop.PROTECTED')[1][:80]: bad.append('protected screen must discard all frames')
m = re.search(r'const val MIN_FRAMES = (\d+)\s+const val MAX_FRAMES = (\d+)', F)
if not m or (m.group(1), m.group(2)) != ('2', '4'): bad.append('frames must be 2..4')
if 'GAP_MS' not in F: bad.append('gap between frames missing')
mp = A.split('fun movieProtected')[1].split('fun movieShot')[0] if 'fun movieProtected' in A else ''
if 'ScreenSession.isProtected' not in mp or 'return true' not in mp: bad.append('movieProtected must use ScreenSession.isProtected and fail closed')
ms = A.split('fun movieShot')[1].split('}')[0] if 'fun movieShot' in A else ''
if 'movieProtected() != false' not in ms or 'return null' not in ms: bad.append('movieShot must refuse unless movieProtected() == false')
if 'startMovieScan' not in S: bad.append('NovaService route missing')
else:
    blk = S.split('private fun startMovieScan')[1].split('private fun analyseByVoice')[0]
    if 'Thread {' not in blk: bad.append('capture must not run on the caller thread (sleep between frames)')
    if re.search(r'FileOutputStream|writeBytes|\.save|HttpURLConnection|postFallback|AnalysisPipeline', blk): bad.append('frames must stay in memory, no network / pipeline in 14B-2')
    if 'MovieScanCapture.run' not in blk or ('finally { movieScanRunning = false }' not in blk and 'finally { movieScanRunning = false; movieStage = ""' not in blk): bad.append('capture run / running flag reset missing')
if S.count('movieScanCancel = true') < 2: bad.append('STOP (both paths) must cancel the movie scan')
if re.search(r'\bface\b|\bactor\b|FaceId|identify', re.sub(r'/\*.*?\*/', '', F + C, flags=re.S), re.I): bad.append('no face / actor logic allowed in capture')
if 'MovieScanRules.parse(text) == MovieScanRules.Ask.MOVIE' not in S or 'AnalysisVoiceRules.parse(text)' not in S or 'MediaIntake.voiceIntent(text)' not in S: bad.append('old routes removed (add-only)')
if T.count('@Test') < 9: bad.append('MovieScanCaptureTest has fewer than 9 tests')
if 'MovieScan' in mf or 'MovieFrame' in mf: bad.append('no manifest change expected')
if 'canTakeScreenshot' not in rd('app/src/main/res/xml/accessibility_config.xml') if os.path.exists(os.path.join(R, 'app/src/main/res/xml/accessibility_config.xml')) else False: bad.append('accessibility config lost canTakeScreenshot')
if bad:
    print('FAIL'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS movie capture 14B-2 contract (static; Kotlin compile + JUnit see local_compile_check; phone NOT TESTED)')
