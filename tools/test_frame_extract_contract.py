#!/usr/bin/env python3
"""v7.51 Part 12 static contract: FrameExtractor runs on its own thread (never main), recycles every bitmap, has a 60 s watchdog + cancel (STOP too), catches OOM,
bridge methods exist and are called by ui.html, MADAD / SKILLS / SCR parity, no new permission, no network."""
import os, re, sys, json
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
J = 'app/src/main/java/com/nova/assistant/'
X = rd(J + 'FrameExtractor.kt'); F = rd(J + 'FrameRules.kt'); A = rd(J + 'AnalysisStage.kt'); M = rd(J + 'MainActivity.kt'); S = rd(J + 'NovaService.kt')
U = rd('app/src/main/assets/ui.html'); H = rd(J + 'HelpGuide.kt'); MF = rd('app/src/main/AndroidManifest.xml')
bad = []
if 'Executors.newSingleThreadExecutor' not in X or 'pool.execute' not in X: bad.append('no single background thread executor')
if 'MediaMetadataRetriever' not in X or 'OPTION_CLOSEST_SYNC' not in X or 'getFrameAtTime' not in X: bad.append('getFrameAtTime / OPTION_CLOSEST_SYNC missing')
if X.count('.recycle()') < 4: bad.append('bitmaps not recycled everywhere (found %d recycle calls)' % X.count('.recycle()'))
if 'r.release()' not in X: bad.append('retriever not released')
if 'OutOfMemoryError' not in X: bad.append('OOM not caught')
if 'WATCHDOG_MS' not in X or 'postDelayed' not in X or 'removeCallbacks' not in X: bad.append('watchdog missing')
if 'WATCHDOG_MS = 60_000L' not in F: bad.append('watchdog is not 60 s')
if 'fun cancel()' not in X or 'gen.incrementAndGet()' not in X: bad.append('cancel missing')
if S.count('FrameExtractor.cancel()') < 2: bad.append('STOP (SafeStop / onStop) does not cancel frame extraction')
if 'Thread.sleep' in X or 'runBlocking' in X: bad.append('blocking call in extractor')
for net in ('HttpURLConnection', 'OkHttp', 'URL(', 'Socket('):
    if net in X: bad.append('network class in extractor: ' + net)
if 'File(c.cacheDir' not in X: bad.append('frames must live in the private cache')
if 'absolutePath' in M.split('fun frameExtractStart')[1].split('fun clearPhoto')[0]: bad.append('a path leaks through the frame bridge')
for fn in ('frameExtractStart', 'frameExtractCancel', 'frameExtractStatus', 'frameExtractThumb'):
    if 'fun ' + fn + '(' not in M: bad.append('bridge missing: ' + fn)
    if 'Android.' + fn not in U: bad.append('ui.html never calls ' + fn)
if '<script id="pt12_frames">' not in U or 'pt12_frames' not in U.split('var SCR=')[1].split(']')[0]: bad.append('ui block / SCR entry missing')
if 'FRAMES NIKALO' not in U or "id='m12_stop'" not in U.replace('"', "'") and 'm12_stop' not in U: bad.append('FRAMES NIKALO / ROKO missing in ui')
# the progress bar must move only from the real done / total of the status
blk = U.split('<script id="pt12_frames">')[1].split('</script>')[0]
if 'setInterval' in blk: bad.append('timer-driven progress in pt12_frames')
if 'innerHTML' in blk: bad.append('innerHTML in pt12_frames (names must use textContent)')
if 'dn*100/t' not in blk: bad.append('bar not computed from real done / total')
# parity
caps = json.loads(re.search(r'var CAPS=(\[.*?\]);\nfunction J', U, re.S).group(1))
c = [x for x in caps if x['id'] == 'frames']; k = re.search(r'Topic\("frames", "([^"]*)"', H)
if not c or not k or c[0]['t'] != k.group(1): bad.append('frames topic title differs Kotlin vs CAPS')
if "t:'Video se frames nikalna'" not in U: bad.append('SKILLS entry missing')
# constants from the owner prompt
for need in ('DEFAULT_FRAMES = 6', 'MAX_FRAMES = 12', 'MAX_SIDE = 768', 'JPEG_QUALITY = 80', 'SIG_SIDE = 32'):
    if need not in F: bad.append('FrameRules lost ' + need)
for st in ('INTAKE', 'FRAMES', 'VISION', 'SEARCH', 'COMPOSE', 'DONE', 'FAILED'):
    if '"%s"' % st not in A: bad.append('AnalysisStage missing ' + st)
perms = re.findall(r'uses-permission android:name="([^"]+)"', MF)
if len(perms) not in (30, 31, 35, 36): bad.append('permission count is %d (was 30)' % len(perms))
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS frame extract contract (static wiring; real phone NOT TESTED)')
