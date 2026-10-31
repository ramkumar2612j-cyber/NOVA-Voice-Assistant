#!/usr/bin/env python3
"""v7.53 Part 13B-1 static contract: AnalysisPipeline runs on ONE background thread (never main), has a 120 s watchdog + cancel (STOP too),
the question is gated BEFORE any network / frame work, frames go as inlineData through NovaService (no new HTTP client), the model answer only through parseVision,
no URL from the model, honest metadata fallback, bridge exists, no path leaks, no new permission. Also the ANALYSE UI wiring (pt13_analyse, SCR, MADAD / CAPS parity, SKILLS, no timer progress, textContent only)."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
J = 'app/src/main/java/com/nova/assistant/'
P = rd(J + 'AnalysisPipeline.kt'); Q = rd(J + 'AnalysisPipelineRules.kt'); M = rd(J + 'MainActivity.kt'); S = rd(J + 'NovaService.kt')
X = rd(J + 'FrameExtractor.kt'); MF = rd('app/src/main/AndroidManifest.xml'); U = rd('app/src/main/assets/ui.html')
bad = []
if 'Executors.newSingleThreadExecutor' not in P or 'pool.execute' not in P: bad.append('no single background thread executor')
if 'WATCHDOG_MS = 120_000L' not in Q or 'main.postDelayed(wd, AnalysisPipelineRules.WATCHDOG_MS)' not in P or 'removeCallbacks' not in P: bad.append('120 s watchdog missing')
if 'fun cancel()' not in P or 'gen.incrementAndGet()' not in P: bad.append('cancel / generation missing')
if S.count('AnalysisPipeline.cancel()') < 2: bad.append('STOP (softStop + HUD onStop) does not cancel the analysis')
if 'runBlocking' in P: bad.append('runBlocking in pipeline')
# gate first: intake() is called in start() BEFORE pool.execute and before any brain.call / FrameExtractor.start
st = P.split('fun start(')[1].split('fun cancel()')[0]
if 'AnalysisPipelineRules.intake(' not in st: bad.append('start() does not gate the question')
if st.index('AnalysisPipelineRules.intake(') > st.index('pool.execute'): bad.append('gate runs after the thread started')
if 'refusal.isNotEmpty()' not in st or 'return refusal' not in st: bad.append('refusal does not stop the run before the network')
if 'gateQuestion' not in Q or 'AnalysisRules.refusalText' not in Q: bad.append('gate not wired in rules')
# vision: inlineData, parseVision, retry once, safety flag, merge, report from AnalysisRules only
if 'inlineData' not in Q or 'image/jpeg' not in Q or 'responseMimeType' not in Q or '"temperature\\":0.1' not in Q.replace('\\"', '\\"'): bad.append('request is not the inlineData / strict JSON pattern')
for need in ('AnalysisRules.parseVision(', 'AnalysisPipelineRules.safetyRefusal(', 'AnalysisRules.mergeVision(', 'AnalysisRules.buildReport(', 'AnalysisRules.metadataReport(', 'AnalysisRules.visionSystemPrompt(', 'AnalysisRules.visionUserPrompt(', 'AnalysisPipelineRules.batches('):
    if need not in P: bad.append('pipeline does not use ' + need)
if 'for (attempt in 0..1)' not in P or 'retryNote' not in P: bad.append('no single retry on an unparseable answer')
if 'AnalysisRules.buildReport(merged, composed, found, sent, isVideo, searchRan, en)' not in P: bad.append('report is not built from the real search / compose outcome (13C)')
# the model never supplies a URL: nothing in pipeline builds a source from model text
if re.search(r'Result\(', P): bad.append('pipeline builds Result objects itself (sources come only from a real search in 13C)')
# no new HTTP client, no key handling in the pipeline
for net in ('HttpURLConnection', 'OkHttp', 'URL(', 'Socket(', 'getKeys', 'getKey('):
    if net in P or net in Q: bad.append('network / key class in pipeline: ' + net)
if 'postFallback(json, AnalysisPipelineRules.CALL_READ_MS' not in S or 'fun analysisBrain()' not in S: bad.append('NovaService.analysisBrain missing / not through postFallback')
if S.count('fun postOnceRaw(') != 1: bad.append('a second HTTP raw call was added in NovaService')
if 'geminiCoolUntil = System.currentTimeMillis() + cd' not in S.split('fun analysisBrain()')[1].split('private fun msg(')[0]: bad.append('analysis failure does not use the Gemini cooldown')
# frames: reuse or start FrameExtractor, poll real counts, do not overwrite its onStage
if 'FrameExtractor.keptFiles(' not in P or 'FrameExtractor.start(' not in P: bad.append('frames not reused / started')
if re.search(r'FrameExtractor\.onStage\s*=', P): bad.append('pipeline overwrites FrameExtractor.onStage')
if 'FRAME_WAIT_MS' not in P: bad.append('no frame wait timeout')
# bridge
for fn in ('analysisStart', 'analysisStatus', 'analysisCancel', 'analysisResult'):
    if 'fun ' + fn + '(' not in M: bad.append('bridge missing: ' + fn)
br = M.split('fun analysisStart(')[1].split('fun clearPhoto')[0]
if 'absolutePath' in br or 'File(' in br: bad.append('a path leaks through the analysis bridge')
if 'MediaIntake.isValidId' not in P: bad.append('id not validated')
# results: RAM only, no file write
if 'FileOutputStream' in P or 'writeText' in P or 'SharedPreferences' in P: bad.append('analysis result written to disk')
# add-only FrameExtractor helpers
for fn in ('fun keptFiles(', 'fun keptSecs(', 'lastDurationMs'):
    if fn not in X: bad.append('FrameExtractor helper missing: ' + fn)
# stages used
for stg in ('INTAKE', 'FRAMES', 'VISION', 'DONE'):
    if 'AnalysisStage.' + stg not in P: bad.append('stage not emitted: ' + stg)
# permissions unchanged
perms = re.findall(r'uses-permission android:name="([^"]+)"', MF)
if len(perms) not in (30, 31, 35, 36): bad.append('permission count is %d (was 30)' % len(perms))
# UI wiring (13B-1)
import json
for fn in ('analysisStart', 'analysisStatus', 'analysisCancel', 'analysisResultText'):
    if 'Android.' + fn not in U: bad.append('ui.html never calls ' + fn)
if '<script id="pt13_analyse">' not in U or 'pt13_analyse' not in U.split('var SCR=')[1].split(']')[0]: bad.append('ui block / SCR entry missing')
blk = U.split('<script id="pt13_analyse">')[1].split('</script>')[0]
if 'setInterval' in blk: bad.append('timer-driven progress in pt13_analyse')
if 'innerHTML' in blk: bad.append('innerHTML in pt13_analyse (names / report must use textContent)')
if 'percent' not in blk or 's.percent' not in blk: bad.append('bar not computed from the real percent')
if 'ANALYSE' not in blk or 'm13_stop' not in blk or 'ROKO' not in blk: bad.append('ANALYSE / ROKO missing in ui')
if not U.rstrip().endswith('</body></html>'): bad.append('ui.html must end with </body></html>')
caps = json.loads(re.search(r'var CAPS=(\[.*?\]);\nfunction J', U, re.S).group(1))
H = rd(J + 'HelpGuide.kt')
c = [x for x in caps if x['id'] == 'analysis']; k = re.search(r'Topic\("analysis", "([^"]*)", "[^"]*", listOf\([^)]*\), "([^"]*)"', H)
if not c or not k or c[0]['t'] != k.group(1) or c[0]['ex'] != k.group(2): bad.append('analysis topic differs Kotlin vs CAPS')
if "t:'Photo / video ANALYSE karna'" not in U: bad.append('SKILLS entry missing')
if 'Analyse dabane par hi' not in U: bad.append('consent line (frames go to the AI only on ANALYSE) missing in the media card')
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS analysis pipeline contract (static wiring + UI wiring; Kotlin compile + JUnit were run separately; real phone NOT TESTED)')
