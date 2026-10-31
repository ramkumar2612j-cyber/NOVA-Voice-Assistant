"""v7.154 contract: JARVIS UPGRADE (deep voice / barge-in / audio focus / provider check / GLM-everywhere)
is fully wired - rules + Android voice engine + focus + NovaService speak chain + mic-loop barge-in +
command routing + Logic hook + settings UI + MADAD topics + JUnit tests - and the LEGAL LINE holds:
the offline voice is the open-source Piper 'alan' model (no movie audio, no cloned person), and nothing
here can attack anything. All features are ADD-ONLY: every v7.153 behaviour stays when they are OFF."""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
def rd(p): return open(os.path.join(ROOT, p), encoding='utf-8').read()

rules = rd('app/src/main/java/com/nova/assistant/JarvisDeepRules.kt')
voice = rd('app/src/main/java/com/nova/assistant/JarvisDeepVoice.kt')
focus = rd('app/src/main/java/com/nova/assistant/SpeakFocus.kt')
tarbz = rd('app/src/main/java/com/nova/assistant/TarBz.kt')
svc = rd('app/src/main/java/com/nova/assistant/NovaService.kt')
logic = rd('app/src/main/java/com/nova/assistant/Logic.kt')
ext = rd('app/src/main/java/com/nova/assistant/ExtCfg.kt')
ma = rd('app/src/main/java/com/nova/assistant/MainActivity.kt')
ui = rd('app/src/main/assets/ui.html')
helpk = rd('app/src/main/java/com/nova/assistant/HelpGuide.kt')
fstat = rd('app/src/main/java/com/nova/assistant/FeatureStatus.kt')
gradle = rd('app/build.gradle.kts')
settings = rd('settings.gradle.kts')
manifest = rd('app/src/main/AndroidManifest.xml')
test = rd('app/src/test/java/com/nova/assistant/JarvisDeepRulesTest.kt')
test2 = rd('app/src/test/java/com/nova/assistant/TarBzTest.kt')

bad = []
def need(cond, msg):
    if not cond: bad.append(msg)

# 1. Pure rules: kinds + guards, and the LEGAL voice source (open model, no clone, no movie audio)
need('object JarvisDeepRules' in rules, 'JarvisDeepRules.kt missing')
for k in ['K_DEEP_ON','K_DEEP_OFF','K_DEEP_DL','K_DEEP_STATUS','K_BARGE_ON','K_BARGE_OFF','K_FOCUS_ON','K_FOCUS_OFF','K_PROVCHECK','K_GLM_ON','K_GLM_OFF']:
    need(k in rules, 'JarvisDeepRules missing kind ' + k)
need('fun use(' in rules and 'fun latinOk(' in rules, 'deep-voice routing guard missing')
need('fun echoSafe(' in rules and 'fun hit(' in rules and 'fun minConf(' in rules, 'barge-in guards missing')
need('fun stopGrammar(' in rules, 'stop-word grammar missing')
need('fun clean(' in rules, 'text cleaner missing')
need('Piper' in rules and 'alan' in rules, 'rules must name the legal open voice source (Piper alan)')
for forbidden in ['clone', 'movie voice', 'copyright']:
    need(forbidden not in rules.lower().replace('no copyrighted', '').replace('no cloned', ''), 'rules mention ' + forbidden + ' as if used')

# 2. The deep-voice engine: verified model constants, safe extract, streaming + interruptible playback, honest fallback
need('sherpa-onnx' in voice or 'com.k2fsa.sherpa.onnx' in voice, 'JarvisDeepVoice must use sherpa-onnx')
need('vits-piper-en_GB-alan-medium-int8' in voice, 'the voice must be Piper alan (deep male, int8)')
need(voice.count('SHA256') >= 1 and len(re.findall(r'[0-9a-f]{64}', voice)) >= 1, 'model SHA-256 pin missing')
need('TarBz.extract' in voice, 'the engine must extract via the safe TarBz')
need('generateWithCallback' in voice, 'playback must be streaming (callback), not wait-for-all')
need('FAILED' in voice and 'stopFlag' in voice, 'interruption + honest FAILED fallback missing')
need('ErrLog' in voice, 'errors must be logged masked')

# 3. Audio focus: may-duck only, released on utterance end, never kills the user's audio
need('AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK' in focus, 'focus must be transient may-duck (never pause the user\'s music)')
need('setWillPauseWhenDucked(false)' in focus, 'focus must not pause ducked audio')
need('abandonAudioFocusRequest' in focus, 'focus release missing')
need('stopSpeaking' in focus, 'losing focus must stop NOVA speech via the existing SafeStop semantics')

# 4. Safe extractor rules
need('MAX_ENTRIES' in tarbz and 'MAX_TOTAL_BYTES' in tarbz, 'extractor limits missing')
need('".."' in tarbz, 'path traversal must be refused')
need('Symlinks' in tarbz or 'symlink' in tarbz.lower() or 'isLink' in tarbz, 'symlink entries must be refused')

# 5. NovaService speak chain: deep voice FIRST (before system TTS), old chain intact, focus around every utterance
need('startDeepVoice(spoken, confirm)' in svc, 'speak() must offer the deep voice first')
need(svc.find('if (JarvisDeepRules.use(spoken') < svc.find('if (JarvisSpeaker.eligible(this, cfg, spoken)'), 'deep voice must run before the server Jarvis')
need('if (ExtCfg.afocus(this)) SpeakFocus.acquire(this)' in svc, 'audio focus must wrap every utterance')
need('SpeakFocus.abandon()' in svc, 'utterance end must release the focus')
for old in ['startJarvis(t, spoken, id)', 'startJarvisLocal(t, spoken, id)', 'startUltron(t, spoken, id)', 't.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, id)']:
    need(old in svc, 'OLD voice path removed: ' + old)   # zero regression: the v7.153 chain must still exist
need('curSpeakText' in svc, 'the barge-in self-echo guard needs the text NOVA is saying')
need('JarvisDeepVoice.stop()' in svc and 'stopSpeaking()' in svc, 'STOP must also kill the deep voice')
need('JarvisDeepVoice.release()' in svc, 'service death must free the engine')
need('in JarvisDeepRules.KINDS -> jarvisUpgradeReply(c)' in svc, 'runCommand routing missing')
need('AutoSetup.checkProviders(app)' in svc, 'provider check must reuse the existing real probe')

# 6. Barge-in in the mic loop: only while ttsActive + opt-in, grammar recognizer, guards, reset
need('ExtCfg.bargeIn(this)' in svc, 'barge-in must be opt-in (default OFF)')
need('JarvisDeepRules.stopGrammar()' in svc, 'the loop must use the stop-word grammar recognizer')
need('JarvisDeepRules.hit(heardTxt, curSpeakText' in svc, 'the self-echo guard must gate every hit')
need('barge?.reset()' in svc, 'wasBlocked reset must clear the barge recognizer too')
need(svc.find('if (ttsActive && ExtCfg.bargeIn(this))') < svc.find('val busyListenOk = BusyListenRules.mayListen('), 'barge-in must run before the busy-listen drop')

# 7. Logic hook: early (before the volume/awaaz block that would eat "awaaz se rokna"), negations never match
need('JarvisDeepRules.parse(text)?.let { return it }' in logic, 'Logic.classify hook missing')
need(logic.find('JarvisDeepRules.parse') < logic.find('if (has("volume", "awaaz", "awaz"))'), 'hook must sit before the volume/awaaz block')
need('negated' in rules, 'negated orders must never match')

# 8. Settings: 4 new ExtCfg keys, deep voice + barge-in default OFF (opt-in), audio focus default ON
for k in ['jarvisDeep', 'bargeIn', 'afocus', 'glmAll']:
    need('fun set' + k[0].upper() + k[1:] in ext, 'ExtCfg setter missing: ' + k)
for const, key in [('JARVIS_DEEP', 'jarvis_deep_voice'), ('BARGE_IN', 'barge_in_stop'), ('AFOCUS', 'audio_focus_on'), ('GLM_ALL', 'glm_everywhere')]:
    need('const val ' + const + ' = "' + key + '"' in ext, 'ExtCfg key missing: ' + key)
    need('getBoolean(' + const + ',' in ext.replace(' ', '') or ('getBoolean(' + const + ', ') in ext, 'ExtCfg read missing: ' + key)
# deep voice + barge-in + glm default OFF; afocus default ON
need(re.search(r'getBoolean\(JARVIS_DEEP, false\)', ext), 'deep voice must default OFF')
need(re.search(r'getBoolean\(BARGE_IN, false\)', ext), 'barge-in must default OFF')
need(re.search(r'getBoolean\(GLM_ALL, false\)', ext), 'glmAll must default OFF')
need(re.search(r'getBoolean\(AFOCUS, true\)', ext), 'audio focus must default ON')

# 9. UI wiring: 4 checkboxes + collect + fill + MADAD topics (HelpGuide and CAPS in the same order)
for cid in ['s_jdeep', 's_barge', 's_afocus', 's_glmall']:
    need('id="' + cid + '"' in ui, 'ui.html checkbox missing: ' + cid)
need('jarvisDeep:$(' in ui and 'bargeIn:$(' in ui and 'afocus:$(' in ui and 'glmAll:$(' in ui, 'collect() must send the 4 new switches')
need("S.jarvisDeep" in ui and 'S.bargeIn' in ui, 'fillPanel must read the new switches back')
need('deep_note' in ui, 'the deep-voice model status line must exist')
for t in ['"jdeep"', '"barge"', '"afocus"', '"provcheck"', '"glmall"']:
    need(t in helpk, 'HelpGuide topic missing: ' + t)
    need(t in ui, 'MADAD (CAPS) topic missing: ' + t)
kt = re.findall(r'Topic\("([a-z]+)", "([^"]*)", "[^"]*", listOf\([^)]*\), "([^"]*)"', helpk)
m = re.search(r'var CAPS=(\[.*?\]);\nfunction J', ui, re.S)
import json
caps = json.loads(m.group(1)) if m else []
need([(a, c) for a, b, c in kt] == [(c['id'], c['ex']) for c in caps], 'HelpGuide topics and ui.html CAPS differ (ids/examples)')
need([(a, b) for a, b, c in kt] == [(c['id'], c['t']) for c in caps], 'HelpGuide and CAPS titles differ')
for r in ['jdeep', 'barge', 'afocus', 'glmall']:
    need('row(a, "' + r + '"' in fstat, 'FeatureStatus row missing: ' + r)

# 10. MainActivity bridge: settings read + save round-trip
for k in ['jarvisDeep', 'bargeIn', 'afocus', 'glmAll', 'deepVoiceReady']:
    need('"' + k + '"' in ma, 'MainActivity settings JSON missing: ' + k)
for s in ['setJarvisDeep', 'setBargeIn', 'setAfocus', 'setGlmAll']:
    need(s in ma, 'MainActivity must save: ' + s)

# 11. Gradle: sherpa-onnx via JitPack + commons-compress + version 7.154 + NO new permissions
need('com.k2fsa.sherpa.onnx:sherpa-onnx-android:1.12.14' in gradle, 'sherpa-onnx dependency missing')
need('com.github.k2-fsa.sherpa-onnx:v1.12.14' not in gradle, 'old broken JitPack coordinate (no module name) must not come back')
need('org.apache.commons:commons-compress' in gradle, 'commons-compress (safe tar.bz2) missing')
need('jitpack.io' in settings, 'settings.gradle.kts needs the JitPack repository')
need('versionName = "7.154"' in gradle, 'versionName must be 7.154')
n_perm = len(re.findall(r'<uses-permission', manifest))
need(n_perm in (30, 31, 35, 36), 'v7.154 must add NO new permissions (found %d)' % n_perm)

# 12. GLM-everywhere: pure provider chain flag, default OFF, forwarded through run + runHedged
free = rd('app/src/main/java/com/nova/assistant/FreeProviders.kt')
need('glmFirst: Boolean = false' in free, 'ProviderChain.run must take glmFirst (default OFF)')
need(free.count('rich || brain || glmFirst') >= 3, 'run + runHedged + fallback must all honour glmFirst')
need('glmFirst = ExtCfg.glmAll(this)' in svc, 'NovaService must pass the owner opt-in to the chain')

# 13. JUnit tests cover the rules + extractor (names pinned so future edits keep the guarantees)
for tname in ['useRequiresOnAndReady', 'useRejectsDevanagariAndLongText', 'latinOkMatchesEnglishAndHinglishButNotHindi',
              'cleanRemovesEmojiKeepsPunctuation', 'stopWordsMatchAsWholeWordsOnly', 'echoSafeBlocksSelfStop',
              'hitNeedsConfidenceAndLoudness', 'minConfParsesVoskWordConfidences', 'stopGrammarOnlyContainsStopWords',
              'deepVoiceCommandsParse', 'bargeAndFocusCommandsParse', 'providerAndGlmCommandsParse',
              'normalSentencesNeverMatch', 'negatedOrdersNeverMatch', 'repliesAreHonest']:
    need(tname in test, 'JarvisDeepRulesTest missing ' + tname)
for tname in ['normalArchiveExtracts', 'pathTraversalIsRefused', 'symlinkEntriesAreRefused',
              'entryAndSizeLimitsHold', 'missingAndEmptyArchivesFailCleanly', 'truncatedArchiveFailsWithoutPartialEscape']:
    need(tname in test2, 'TarBzTest missing ' + tname)

# 14. The old WiFi legal line still holds everywhere new (nothing here can attack anything)
allnew = rules + voice + focus + tarbz
for w in ['aircrack', 'reaver', 'wifite', 'bruteforce', 'brute force', 'wordlist attack', 'wps attack', 'handshake', 'password guess', 'crack']:
    need(w not in allnew.lower(), 'forbidden attack word in new code: ' + w)

if bad:
    print('FAIL v7154 jarvis upgrade contract'); [print(' -', b) for b in bad]; sys.exit(1)
print('PASS v7154 jarvis upgrade contract (deep voice + barge-in + focus + provider check + glm, all add-only)')
