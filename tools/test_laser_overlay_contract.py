#!/usr/bin/env python3
"""v7.66 Part 14B-4 static contract: native full-screen laser overlay of the movie scan. Not touch-through, STOP button, hidden for every screenshot (no laser in frames),
real stages only, simple mode, no network / disk / face. Compile + JUnit + phone NOT RUN here."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'); J = os.path.join(R, 'app/src/main/java/com/nova/assistant')
rd = lambda n: open(os.path.join(J, n), encoding='utf-8').read()
L = rd('LaserOverlay.kt'); S = rd('NovaService.kt'); bad = []
need = lambda c, m: bad.append(m) if not c else None
need('TYPE_APPLICATION_OVERLAY' in L and 'canDrawOverlays' in L, 'overlay window / permission')
need('FLAG_NOT_TOUCHABLE or' not in L and 'FLAG_NOT_TOUCHABLE)' not in L and 'return true' in L, 'overlay must not be touch-through')
need('MATCH_PARENT' in L and 'FLAG_NOT_FOCUSABLE' in L, 'full screen + not focusable')
need('FLAG_SECURE' not in L, 'FLAG_SECURE would blank the frames: use hideForShot')
need('fun hideForShot' in L and 'fun showAfterShot' in L and 'View.INVISIBLE' in L, 'hide for shot missing')
need(re.search(r'try \{ l\?\.hideForShot\(\) \}.*?NovaAccessibilityService\.movieShot\(\).*?showAfterShot', S, re.S) is not None, 'service must hide the overlay around movieShot')
need('LaserMath.STAGES' in L and 'MovieGuessPipeline.ST_SEARCH' in L, 'stages must be the real pipeline stages')
need('laser.setStage(movieStage)' in S.replace('l.setStage(movieStage)', 'laser.setStage(movieStage)'), 'tick must read movieStage')
need('movieScanCancel' in S[S.index('private val laserTick'):S.index('private fun startLaser')], 'tick must stop on cancel')
need('l.onStop = {' in S and 'movieScanCancel = true' in S[S.index('l.onStop = {'):S.index('private fun stopLaser')], 'STOP must cancel the scan')
need('overlayNudge()' in S[S.index('private fun startLaser'):S.index('private fun stopLaser')], 'permission asked once via overlayNudge')
need('stopLaser()' in S[S.index('movieScanRunning = false; movieStage'):][:200], 'laser must close when the scan ends')
need('try { stopLaser() }' in S, 'onDestroy must close the laser')
need('isSimple' in L and 'ANIMATOR_DURATION_SCALE' in L and 'if (!simple) postInvalidateOnAnimation()' in L, 'simple mode')
for bad_word in ('HttpURLConnection', 'FileOutputStream', 'getBitmap', 'FaceDetector', 'openFileOutput'):
    need(bad_word not in L, 'overlay must not use ' + bad_word)
need('NovaHud(' in S and 'class NovaHud' in rd('NovaHud.kt'), 'old HUD untouched')
if bad: print('FAIL laser overlay:', bad); sys.exit(1)
print('PASS laser overlay contract (static). Compile / JUnit LaserMathTest / phone: NOT RUN')
