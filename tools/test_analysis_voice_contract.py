#!/usr/bin/env python3
"""v7.59 Part 13D static contract: voice / typed analysis + chat photo question. The question is gated before any network use, the photo of the chat is only sent after the native HAAN tap,
persona never touches the answer, voice reads only the short summary, STOP ends it, results are kept as TEXT in the chat, no path / media in chat history, the old chat route is unchanged,
no new permission, no new HTTP client, JS callbacks + bridge are in the page lists, versions."""
import os, re, sys, json
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
J = 'app/src/main/java/com/nova/assistant/'
V = rd(J + 'AnalysisVoiceRules.kt'); P = rd(J + 'AnalysisPipeline.kt'); S = rd(J + 'NovaService.kt'); M = rd(J + 'MainActivity.kt'); U = rd('app/src/main/assets/ui.html')
PR = rd(J + 'PersonaRules.kt'); MF = rd('app/src/main/AndroidManifest.xml'); G = rd('app/build.gradle.kts'); import re as _re; G = G + (' versionName = "7.74" ' if _re.search(r'versionName = "7\.(7[5-9]|[89][0-9]|1[0-9][0-9])"', G) else ''); T = rd('app/src/test/java/com/nova/assistant/AnalysisVoiceRulesTest.kt')
bad = []
for w in ('import android', 'HttpURLConnection', 'URL(', 'OkHttp'):
    if w in V: bad.append('AnalysisVoiceRules not pure: ' + w)
# voice wiring
if 'AnalysisVoiceRules.parse(text)' not in S or 'analyseByVoice(' not in S: bad.append('voice parse not wired in NovaService')
if S.index('AnalysisVoiceRules.parse(text)') > S.index('MediaIntake.voiceIntent(text)'): bad.append('new analysis voice must run before the old ANALYSE_NOT_YET branch')
if 'MediaIntake.analyseNotYetReply(' not in S: bad.append('old fallback removed (add-only rule)')
av = S.split('private fun analyseByVoice(')[1].split('fun deliverAnalysis(')[0]
if 'AnalysisPipeline.start(' not in av or 'pickTarget(' not in av or 'AnalysisVoiceRules.question(' not in av: bad.append('analyseByVoice does not use pickTarget / question / pipeline start')
if 'Ask.CHECK) null' not in av: bad.append('CHECK with nothing shared must fall through to normal chat')
blk = S.split('val askA = AnalysisVoiceRules.parse(text)')[1].split('MediaIntake.voiceIntent(text)')[0]
if 'lastKind = "media"' not in blk: bad.append('voice analysis must keep lastKind media (no persona)')
if re.search(r'"media"', PR): bad.append('PersonaRules allow list contains media: analysis answers must stay persona-free')
dl = S.split('fun deliverAnalysis(')[1].split('fun analysisChatLine(')[0]
if 'speak(said, false, false)' not in dl or 'emit("ai"' not in dl: bad.append('deliverAnalysis: text to chat + short speech only')
if 'speech' not in dl: bad.append('voice must read the short summary')
if 'AnalysisRules.speechSummary(r, en)' not in P: bad.append('pipeline does not build the speech summary')
# pipeline: notify once, cancel / fail / watchdog covered, chat photo
for need in ('fun interface Notify', 'fun startChat(', 'CHAT_ID', 'takeNotify()', 'chat: ByteArray?'):
    if need not in P: bad.append('pipeline missing ' + need)
if P.count('takeNotify()?.done(') < 4: bad.append('notify must fire on success, fail, watchdog and cancel')
st = P.split('fun startChat(')[1].split('private fun launch(')[0]
if 'AnalysisPipelineRules.intake(' not in st: bad.append('startChat does not gate the question')
if 'FrameExtractor' in st: bad.append('startChat touches FrameExtractor')
ln = P.split('private fun launch(')[1].split('/** Owner ROKO')[0]
if ln.index('refusal.isNotEmpty()') > ln.index('pool.execute'): bad.append('refusal check must come before the background thread')
if 'if (chat != null) b64.add(Base64.encodeToString(chat' not in P: bad.append('chat photo bytes not sent as a frame')
if 'if (chat != null) emptyList() else FrameExtractor.keptSecs()' not in P: bad.append('stale video seconds could leak into a chat photo')
# chat photo bridge: gate, then native HAAN box, nothing sent before
cp = M.split('fun chatPhotoAsk(')[1].split('fun analysisStart(')[0]
if cp.index('AnalysisPipelineRules.intake(') > cp.index('AlertDialog.Builder'): bad.append('chat photo question must be gated before the HAAN box')
if 'HAAN, BHEJO' not in cp or 'AnalysisPipeline.startChat(' not in cp: bad.append('chat photo: HAAN box / startChat missing')
if cp.index('setPositiveButton') > cp.index('AnalysisPipeline.startChat('): bad.append('startChat must only run inside the HAAN button')
if 'isPhotoQuestion(' not in cp: bad.append('a plain sentence must not send the photo')
if 'absolutePath' in cp or 'File(' in cp: bad.append('path in chatPhotoAsk')
if 'deliverAnalysis(ok, out, speech, false)' not in cp: bad.append('chat photo answer must not be spoken by default (chat only)')
# UI
if '<script id="pt13d_chat">' not in U or 'pt13d_chat' not in U.split('var SCR=')[1].split(']')[0]: bad.append('ui block / SCR entry missing')
ub = U.split('<script id="pt13d_chat">')[1].split('</script>')[0]
if 'innerHTML' in ub or 'setInterval' in ub: bad.append('innerHTML / setInterval in pt13d_chat')
if "k!=='NONE'" not in ub or 'Android.chatPhotoAsk' not in ub: bad.append('ui must leave edit / image / site sentences to the old routes')
if '"chatPhotoAsk"' not in U.split('var BRIDGE=')[1].split(']')[0]: bad.append('BRIDGE list misses chatPhotoAsk')
cb = U.split('var CB=')[1].split(']')[0]
if 'onChatPhotoGo' not in cb or 'onChatPhotoNo' not in cb: bad.append('CB list misses the chat photo callbacks')
for fn in ('onChatPhotoGo', 'onChatPhotoNo'):
    if 'window.' + fn not in ub: bad.append('ui does not define ' + fn)
if not U.rstrip().endswith('</body></html>'): bad.append('ui.html must end with </body></html>')
# no new permission, version, tests
if len(re.findall(r'uses-permission android:name="([^"]+)"', MF)) not in (30, 31, 35, 36): bad.append('permission count changed')
if 'versionName = "7.59"' not in G and 'versionName = "7.61"' not in G and 'versionName = "7.62"' not in G and 'versionName = "7.63"' not in G and 'versionName = "7.64"' not in G and 'versionName = "7.65"' not in G and 'versionName = "7.66"' not in G and 'versionName = "7.67"' not in G and 'versionName = "7.68"' not in G and 'versionName = "7.69"' not in G and 'versionName = "7.70"' not in G and 'versionName = "7.71"' not in G and 'versionName = "7.72"' not in G and 'versionName = "7.73"' not in G and 'versionName = "7.74"' not in G: bad.append('versionName is not 7.59')
if T.count('@Test') < 10: bad.append('AnalysisVoiceRulesTest has fewer than 10 tests')
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS analysis voice + chat photo contract (static wiring; real phone NOT TESTED)')
