# NOVA - NEXT ACCOUNT PROMPT (after Part 6A; read fully before doing anything)

You are taking over the NOVA Android voice assistant project. The owner is non-technical and works from a phone. Read every section. Follow section 1 exactly.

## 0. STATE WHEN THIS PROMPT IS GIVEN (AFTER-PART-6A prompt)
- Parts 1-5 are WRITTEN (see the AFTER-PART-5 prompt NOVA_NEXT_ACCOUNT_PROMPT_5_AFTER_PART5.md inside the zip for their details) and PART 6A is WRITTEN (below). NOTHING IS CI-GREEN YET: the chat sandbox has Java but NO Kotlin compiler and NO network, so nothing from Parts 3-6A was compiled. Only the pure parse rules of RecordRules were checked with a Python port (34 sentences, 0 mismatches).
- The owner asked to split PART 6 (BACKGROUND RECORDING) into 3 parts and to finish 6A first:
  * 6A = AUDIO recording end to end (WRITTEN, details below).
  * 6B = VIDEO recording with the screen off (camera foreground service, CAMERA permission + foregroundServiceType camera|microphone, MediaRecorder/CameraX, start by voice "background me video recording on karo", STOP by voice / notification / Settings, honest per-phone report: many phones stop camera access when the screen is off or the app is not visible; Android 14+ rules; test and report honestly, never fake). Reuse RecordRules (parse needs a video variant: today "video", "camera", "screen" make parse return null on purpose so the existing camera tool keeps those sentences), RecordSession patterns, the notification with STOP, the wake lock, the 3-hour / free-space limits and the MediaStore export (Movies/NOVA).
  * 6C = RECORDINGS MANAGER + FINAL: Settings list of recordings (name, length, size, date), play, share, delete (with confirm), "kitni recordings hain / last recording sunao" by voice, auto-delete-old option with a chosen limit, recovery of unfinished .m4a parts left by a crash (look in RecordSession.dir), SafetyInvariantsTest additions, consolidated phone check list for 6A+6B+6C, independent reviews of the whole of Part 6.
- PART 6A content (all WRITTEN, not compiled):
  * RecordRules.kt (pure): parse(text) -> Req(START|STOP|STATUS, alsoLock). Understands "background me recording on karo aur mobile off kar do", "recording shuru/band karo", "stop recording", "recording chal rahi hai kya". Returns null for screen/video/camera/call recording, play/delete/share, questions, negations ("band mat karo") and sentences with both start and stop words. Limits: SEGMENT_MS 30 min, MAX_MS 3 h, MIN_START_FREE 150 MB, STOP_FREE 60 MB, AAC 32 kbps. segName, fmtDur, ptsUs, partOf, all messages (Hinglish + English) in text(), whereMedia / whereApp.
  * RecordSession.kt (Android): does NOT open a second microphone. NovaService's one mic loop calls RecordSession.feed(buf, n) for every 100 ms chunk (placed right after the read, before the busy/TTS skip, so the recording has no gaps and the wake word keeps working). A writer thread encodes AAC (MediaCodec) into .m4a (MediaMuxer), a new file every 30 minutes, into the app's Music/rec folder; on stop (Android 10+) each file is copied to MediaStore Music/NOVA (IS_PENDING) and the private copy deleted; Android 9 and older keep the files in the app folder (path is told). Bounded queue (120 s), dropped chunks are counted and reported.
  * NovaService.kt: ACTION_REC_STOP, onStartCommand handles it (notification STOP button, runs stopRecording on a worker thread); baseNotif() / recNotif() (ongoing, chronometer, STOP action; same notification id 1 as the foreground service, so it cannot be hidden); recTick every 10 s (auto-stop at 3 h, low space, writer error, with a spoken + shown message); startRecording() (checks NOVA active, RECORD_AUDIO, free space, PARTIAL_WAKE_LOCK "nova:rec"), stopRecording(why), runRecording(req), lockAfterDelay() (uses the existing globalAct("lock") 2.5 s later; needs Accessibility; failure is shown honestly), recStartUi / recStopUi / recStatusUi. handle(): the recording parse runs right after the personal-memory check (before LocalSkills, agent, Understand) and also on Groq/Gemini-heard text (and Understand.classify is skipped for a recording order so it can never become another command). onDestroy saves the recording first. "rec" is in NO_FOLLOW_KINDS and in Logic.LOUD_KINDS (always spoken, except a "chup rehna" silent turn).
  * MainActivity bridge: recStart, recStop, recStatus. ui.html: new Settings box "BACKGROUND RECORDING" (status line refreshed every 5 s, RECORDING SHURU, RECORDING BAND + SAVE, honest notes). No new permission or manifest change.
  * Tests: RecordRulesTest (11 tests: owner sentence, start/stop/status phrases, never-match list, limits, sizes/parts, file names, durations, messages, LOUD_KINDS + silent rule).
- Honest limits of 6A (tell the owner): speech quality only (16 kHz mono 32 kbps, about 14 MB per hour); NOVA's own spoken replies and beeps are recorded too; recording needs NOVA ACTIVE (switching NOVA off saves and ends it); if the app or phone dies, only the current 30-minute part can be damaged (unfinished part may not play; recovery is planned in 6C); some phone makers kill background apps even with a foreground service (see BATTERY / AUTOSTART); locking the screen by voice needs the Accessibility permission; the notification and Android's mic indicator cannot and must not be hidden; recording other people without their knowledge is illegal in many places.
- Part 6A phone check list (Hinglish for the owner): (a) ACTIVATE karo, Settings > RECORDING SHURU: notification me "NOVA recording chal rahi hai" + STOP button aur timer dikhna chahiye; (b) 1 minute bolo, screen band karo, 2 minute baad screen kholo: status me waqt badha hua dikhe; (c) bolo "nova recording band karo": NOVA bole "Recording band ho gayi aur save ho gayi", Files / Music app me Music > NOVA folder me NOVA_audio_....m4a file mile aur play ho; (d) bolo "nova background me recording on karo aur mobile off kar do": recording shuru ho aur screen lock ho (Accessibility on ho to); lock hone ke baad bolo "nova recording band karo" aur check karo ki awaaz sunai de raha hai ya nahi (phone ka mic screen band me chalta hai ya nahi, ye phone par depend karta hai: batao); (e) notification ka STOP dabao: recording band + save; (f) 10 minute se zyada chalao aur dekho file me poori awaaz hai ya beech me gap.
- FIRST JOB: owner uploads the zip, runs "Unpack zip", then "Build and Test APK" (ONE build for Parts 1-6A). Fix whatever the build says until CI-GREEN. Likely first suspects: androidx dependency resolution, FaceGate.kt, RecordSession.kt (MediaCodec/MediaMuxer, smart casts in Writer.run, synchronized returns), NovaService recording block, ui.html. Then the phone check lists (Parts 2-5 and 6A). Then START PART 6B (video), then 6C.
- Owner's demand (applies to everything): "no mistakes at all; have the work analysed by several AIs/reviewers; NOVA must hear clearly what I say". Before delivering each part run independent review passes and a hand walk-through of every new test. NOTE: 6A has NOT had an independent review pass yet; do one (compile review of RecordSession.kt + the NovaService recording block, audio-path review of the feed hook) before or together with 6B.

## 1. COMPULSORY RULES (owner's orders, apply to EVERY account, copy them into EVERY prompt you write)
1. Everything you deliver must be REAL and WORKING. Nothing half-done, nothing stubbed, no "TODO", no fake success messages, no feature that only looks finished. If something cannot be done on Android, say so plainly and build the closest honest version.
2. Finish each part completely (code + unit tests + a short phone check list for the owner) before starting the next part. Do not skip any part and do not leave anything out.
3. Never claim more than the evidence shows. Use exactly these three levels: WRITTEN (code exists), CI-GREEN (the GitHub build and all unit tests passed), PHONE-TESTED (the owner tried it on the phone and said it works). You cannot compile or run the Android app in the chat sandbox, so a part is never CI-GREEN until the owner shows a green build. (A pure-Kotlin test harness with kotlinc can be made in the sandbox by downloading the compiler from GitHub releases and writing a tiny JUnit shim; it checks pure logic only and is NOT CI-GREEN.)
4. Give the owner a prompt BEFORE you start work, and another prompt after Part 2 is complete (that is the pattern for every account). Every prompt you write must contain these rules and all 11 parts.
5. Owner is non-technical, works only from an Android phone, writes Hinglish: reply in Hinglish, short messages, ONE step at a time, clickable links, files to download instead of long pastes.
6. Never weaken or delete an existing test or safety rule to make a build green (SafetyInvariantsTest guards risky actions: spoken yes, no tap/type in banking/payment apps, etc.).
7. Privacy: no audio saved, no voiceprint or API key leaves the phone, never log or print a key. Recording features must show Android's own notification (cannot and must not be hidden).
8. NEW: independent review before delivery. Use separate reviewers that read only the code (compile errors; logic and safety; for listening features, the audio path). Report their findings honestly, including what they could not check.

## 2. HOW WORK IS DELIVERED (the owner's real method, do not change it)
- Repo: https://github.com/devboutique0-wq/NOVA (Android voice assistant "NOVA", Kotlin, package com.nova.assistant). Built ONLY by GitHub Actions; the owner has no PC.
- You give ONE zip: NOVA_v15_assistant.zip (the whole project). Owner steps:
  1. Upload the zip: https://github.com/devboutique0-wq/NOVA/upload/main -> Commit changes.
  2. Actions -> "Unpack zip" -> Run workflow (https://github.com/devboutique0-wq/NOVA/actions). THIS puts the new code into the repo.
  3. Actions -> "Build and Test APK" -> Run workflow (or let the automatic one finish).
  4. Green -> download the APK from the run's Artifacts and install. Red -> owner searches the log for FAILED and sends a screenshot.
- IMPORTANT LESSON: the build that starts right after the upload commit ("Add files via upload") runs on the OLD code because the zip is not unpacked yet. It can be red for an old reason. Only the build after "Unpack zip" counts. Tell the owner this every time.
- Always unzip the owner's zip first and read the real code before editing. Repack the full project as NOVA_v15_assistant.zip and send the file.

## 3. WHAT THE CODE ALREADY HAS (read from the real zip, verified)
Folder app/src/main/java/com/nova/assistant/ ; UI is one web page app/src/main/assets/ui.html talking to Kotlin through the Android JS bridge in MainActivity.
- Logic.kt: strict command parser (Logic.classify, Logic.Cmd(kind,arg,num)), speakReply / LOUD_KINDS, timing constants. Understand.kt: loose parser for long casual sentences; only safe, reversible commands; anything unclear goes to the AI. Tests: LogicTest, UnderstandTest.
- NovaService.kt (about 3000 lines): the always-on service. Mic loop (startCapture, noise/peak/speaking, Vosk wake word), process / processAnswer / finishTurn, handle(), runCommand, runLocalTool (offline commands), runTool (commands the cloud AI calls), declarations() (tool list for Gemini), systemPrompt(), cameraTool(mode, facing), launch(), openApp(), agent (agentBegin/agentStart/agentRun), pending yes/no (askFirst, Pending).
- AI chain (FreeProviders.kt, FreeHttp.kt, GroqStt.kt, FreeImage.kt): Gemini (owner key; hears audio AND is the only one with phone tools) -> Groq (llama-3.3-70b, llama-3.1-8b; Whisper speech-to-text) -> Qwen via Groq -> GLM (Z.ai) -> OpenRouter free models -> Pollinations -> offline Qwen2.5-0.5B (LocalBrains) -> offline skills. Images: Together FLUX, HuggingFace FLUX, Pollinations. Failover is between DIFFERENT providers only.
- Speaker lock: SpeakerRules.kt + VoiceLock.kt (Vosk x-vector, average of 5 enrollment samples, cosine distance, strictness 0.4 / 0.5 / 0.6, never tuned on a real phone).
- PatternUnlock.kt / PatternSetupActivity.kt: voice-triggered pattern unlock (security sensitive).
- Memory and learning: PersonalMemory(+Store), LearnRules/LearnStore, Brain/BrainStore (skills, triggers), KnowledgeStore + assets/knowledge.json.
- Health: HealthCheck.kt, SelfTest.kt, ErrLog.kt, PowerGuide(+Rules).kt. Silent mode: SilentRules.kt.
- Timers.kt, Driving.kt, ScreenAlert.kt, NovaAccessibilityService.kt, NovaHud.kt, UpdateManager.kt, Updater.kt, GitHubPublish.kt, AutoSetup*.kt.
- Tests: app/src/test/java/com/nova/assistant/ (about 340 tests). CI runs them all; one failing test makes the whole build red.

## 4. THE 11 PARTS (owner agreed this order; finish each fully before the next)
1. CAMERA - WRITTEN. "mere mobile ka camera kholo aur front camera se video on karo" in one sentence. camera tool has mode (photo|video) and facing (front|back); video opens MediaStore video camera; front sends CAMERA_FACING / LENS_FACING_FRONT / USE_FRONT_CAMERA extras (best effort, some camera apps ignore them). NOVA cannot press the record button; it tells the owner to tap it. Understand.classify returns null for camera + video/front/selfie/record words so the AI chooses the arguments.
2. SELF-DIAGNOSIS - WRITTEN. ErrLog (last 30 failures, in memory only, keys masked) in HealthCheck and SelfTest reports; Settings buttons AKHRI GALTIYAN and BATTERY / AUTOSTART; PowerGuide opens the brand's autostart screen (Xiaomi, Oppo/Realme/OnePlus, Vivo/iQOO, Huawei/Honor, Samsung) with a standard-Android fallback. Limits: log is cleared when the app is killed; brand screens are private and may not exist; the app can open screens but cannot switch settings itself. (Still to add if not present: the health report showing which AI keys are set, never the key.)
3. SILENT MODE - WRITTEN (see section 0). Phone check list for the owner:
   a. Say "nova torch jala do, chup rehna": torch on, no voice, card says "Kaam complete ho gaya".
   b. "nova volume kam karo bina bole": volume lower, no voice.
   c. "nova battery kitni hai chup rehna": answer only on the card, no voice.
   d. "nova torch band karo" (without the silent phrase): speaks normally.
   e. Ask NOVA to call someone with "chup rehna": it must still ASK yes/no aloud.
   Limits: if NOVA mishears the phrase it speaks; phrase inside a dictated message text is not cut unless separated by comma/aur/and; Android's own notifications / privacy indicators cannot be hidden.
4. NEVER WAKE BY ITSELF / NEVER INTERRUPT - WRITTEN (see section 0). Original goal: no activation from TV, other people, or background noise; if the owner is talking to someone else NOVA must not cut in. Audit the wake-word path (see WakeStrictTest, ListenTest), add a stricter gate, and a rule that NOVA stays silent unless the wake word plus owner voice check pass. Include the listening-audit items from section 0 that are safe (noise estimate, fast-end timers, skip/beep, trailing-silence trim, Groq prompt).
5. VOICE + FACE - WRITTEN (see section 0). Original goal: Voice: self-tuning enrollment test (owner says about 10 sentences; measure accept/reject; choose the threshold; replace the untuned 0.4/0.5/0.6). Locked phone: NOVA wakes only if BOTH voice AND face verify (owner chose "both", not "either one"); otherwise stay silent. Face: Android gives apps NO access to the phone's face-unlock data. Use androidx.biometric BiometricPrompt (system face/fingerprint screen, shown over the lock screen, asks permission in-app once). On phones where face is only "weak", BiometricPrompt may refuse: then offer fingerprint or device credential as the second factor and tell the owner honestly. A recording of the owner's voice can fool voice-ID, which is why the second factor exists. Add real tests for the decision rules (pure Kotlin).
6. BACKGROUND RECORDING - SPLIT INTO 6A (audio, WRITTEN), 6B (video), 6C (manager + final). Original goal: "background me recording on karo aur mobile off kar do" works with the screen off: foreground service (microphone type; camera type for video) with the mandatory visible notification and Android's privacy indicator. Audio recording is reliable; video with screen off depends on the phone and Android version: test, and report honestly. Save files to the app's own folder / MediaStore, show where. Needs a clear spoken or on-screen stop. No hidden or covert recording.
7. EVERY FREE AI CAN CONTROL THE PHONE + QUOTA METER. Today only Gemini can call phone tools; Groq/Qwen/GLM/OpenRouter only chat. Make each of them return a strict JSON command that is validated and mapped to the SAME safe Logic.Cmd / runTool path (same safety rules, same confirmations). Show in Settings which provider is working, which hit a limit, and when it cools down. Free-tier limits change: read the provider dashboards or docs when quoting numbers, never guess them.
8. CONTEXT MEMORY. Follow-ups: "isko band karo", "thoda aur tez", "wahi dobara". Keep the last few turns (what was just done) so pronouns and "aur" work; expire after a short time; never repeat risky actions from context.
9. TEACH ONCE. Owner corrects NOVA once ("mummy = Sunita", "mera camera = Camera app", "iska matlab ye hai"); NOVA stores it (LearnStore/PersonalMemory), applies it next time, lets the owner see/delete it, and has a backup/restore of learned data (export to a file the owner can keep; restore after reinstall).
10. ROUTINES. "good morning" -> several steps in order (existing Brain skills are the base). Safe kinds only (Brain.SAFE_KINDS), step limit, owner can edit/delete routines.
11. SEE THE SCREEN. "dekh ke batao / screen par ye click karo": use the accessibility screen tree first and, when useful, a screenshot sent to Gemini vision. Android and apps block some screens (banking, payment, passwords, FLAG_SECURE); respect Control.RISKY and the blocked list; say plainly when a screen cannot be read.

## 5. DEFINITION OF DONE FOR EVERY PART (all items, every time)
- Real code wired end to end (not just a file that nothing calls).
- Unit tests for every pure rule; existing tests untouched and still logically passing. Walk through each new test by hand, and run the pure ones in the sandbox harness if possible.
- Independent review passes (compile, logic/safety, and audio path where relevant); report their findings honestly.
- A phone check list of 3 to 6 short steps for the owner, in Hinglish, each with the expected result.
- An honest limits list for that part.
- Updated zip presented as a file; then ONE next step for the owner (upload link first).
- A short summary of evidence level (WRITTEN / CI-GREEN / PHONE-TESTED) for each item.

## 6. ANDROID LIMITS TO TELL THE OWNER STRAIGHT (do not promise around them)
- Apps cannot read the phone's face-unlock data; only BiometricPrompt is possible.
- Apps cannot press the camera shutter/record button without an accessibility tap (screen dependent); recording always shows a notification and a privacy indicator.
- Wi-Fi and Bluetooth cannot be switched by an app; NOVA opens the settings page.
- Some apps/screens block accessibility reading and taps; NOVA must say so.
- Free AI keys have per-minute and per-day limits; sentences and audio go to the provider used, so say so in settings text.
- No speech recogniser is perfect: offline Vosk guesses Roman words; cloud transcripts (Groq Whisper, Gemini) are better. Never promise zero mishearing; reduce it and make risky actions need a spoken yes.

## 7. YOUR FIRST MESSAGE TO THE OWNER
In Hinglish, 3 to 5 short lines: what state you found, what the single next step is (with a clickable link), and nothing else.


## 8. STATUS TABLE OF ALL 11 PARTS (evidence levels: WRITTEN / CI-GREEN / PHONE-TESTED; today NOTHING is CI-GREEN or PHONE-TESTED for Parts 3-6A)
| Part | Name | State |
|---|---|---|
| 1 | Camera (photo/video, front/back) | WRITTEN |
| 2 | Self-diagnosis (ErrLog, battery/autostart guide) | WRITTEN (to add if missing: health report shows which AI keys are SET, never the key) |
| 3 | Silent mode ("chup rehna") | WRITTEN |
| 4 | Never wake by itself / never interrupt (ListenGuard) | WRITTEN |
| 5 | Voice self-tuning + locked-phone face/fingerprint gate | WRITTEN |
| 6A | Background AUDIO recording | WRITTEN (this prompt) |
| 6B | Background VIDEO recording | NOT STARTED |
| 6C | Recordings manager + recovery + Part 6 final review | NOT STARTED |
| 7 | Every free AI controls the phone + quota meter | NOT STARTED |
| 8 | Context memory (follow-ups) | NOT STARTED |
| 9 | Teach once (+ backup/restore) | NOT STARTED |
| 10 | Routines | NOT STARTED |
| 11 | See the screen | NOT STARTED |
Also still wanted by the owner from earlier chats: Hindi command model, knowledge pack, personal memory, free AI provider chain with automatic fallback (no asking the user) each verified as really working; one complete zip at the end.

## 9. FULL DETAIL OF THE PARTS STILL TO DO (acceptance criteria; each part also needs section 5 "definition of done")
### 6B VIDEO RECORDING (screen off)
- Voice: "background me video recording on karo (aur mobile off kar do)", stop by voice / notification STOP / Settings button. Extend RecordRules.parse with a VIDEO variant WITHOUT breaking the existing camera tool (today "video/camera/screen/front" make parse return null so Part 1 handles "camera kholo, video on karo"; decide by words "background", "recording", "record" + "video" and keep "camera kholo" sentences on the Part 1 path; add tests both ways).
- New foreground service (e.g. VideoRecordService) with foregroundServiceType camera|microphone, permissions CAMERA, FOREGROUND_SERVICE_CAMERA (Android 14), reuse notification-with-STOP, PARTIAL_WAKE_LOCK, limits and MediaStore export (Movies/NOVA). Android 11+ blocks camera/mic access for a service started from the background: start it from a visible moment (the app or the notification) and say so; the NOVA mic loop already holds the microphone, so decide honestly (video with its own audio track may need the wake-word loop to pause: say that to the owner, resume after stop).
- Honest report per phone: many phones stop camera access when the screen turns off or the app is not visible; test and show the REAL result in chat ("recording chal rahi hai" only if frames are really arriving: check file size growth). Never fake. Front/back choice. Max length and free-space rules like 6A. A visible notification and Android's camera indicator can never be hidden.
- Tests for the pure parts (parse variants, limits, names). Phone check list (screen on / off, front / back, 5 minute file plays).
### 6C RECORDINGS MANAGER + FINAL FOR PART 6
- Settings list: name, length, size, date for audio (Music/NOVA) and video; play (system player intent), share (ACTION_SEND with the file Uri), delete with an on-screen confirm (never a voice-only delete of many files); voice: "kitni recordings hain", "last recording sunao".
- Optional auto-delete of recordings older than N days (owner sets N, default OFF).
- Recovery of unfinished parts left in RecordSession.dir after a crash (try to finalize / move to Music/NOVA, tell the owner honestly which could not be played).
- SafetyInvariantsTest additions: recording is never started without the visible notification; no recording kind bypasses voice lock / face gate; no audio leaves the phone.
- Independent reviews (compile, logic/safety, audio path) of ALL of Part 6. Consolidated phone check list 6A+6B+6C.
### 7 EVERY FREE AI CAN CONTROL THE PHONE + QUOTA METER
- Today only Gemini calls phone tools (declarations() / runTool in NovaService). Make Groq, Qwen via Groq, GLM (Z.ai), OpenRouter free models return ONE strict JSON command ({"tool":..., "args":{...}}), parse and validate it (pure Kotlin, unit tested with good and hostile samples), and map it to the SAME Logic.Cmd / runTool path: same Control.RISKY rules, same spoken-yes confirmations, same banking/payment blocks. A model's reply can never skip a confirmation or run a tool that is not in the safe list.
- Settings meter: which provider works, which hit a limit, when it cools down (Cooldowns in NovaService, FreeProviders). Free-tier numbers change: read provider dashboards/docs when quoting numbers, never guess. Failover only between DIFFERENT providers; automatic, never ask the owner.
- Tests: JSON parser, validator, tool mapping, cooldown rules. Keys never logged or printed.
### 8 CONTEXT MEMORY (follow-ups)
- Keep the last few turns (what command ran, which app/contact/value) so "isko band karo", "thoda aur tez", "wahi dobara", "aur kam" work. Expire after a short time (e.g. 2 minutes); memory only (not on disk); never repeat a risky action from context (call, send, delete, pay, tap in banking); a follow-up that would be risky asks the spoken yes again. Pure rules + tests (resolution table, expiry, risky-never-repeat). Do not break the Part 3 silent rules or the pending yes/no.
### 9 TEACH ONCE
- Owner corrects NOVA once ("mummy = Sunita", "mera camera = Camera app", "iska matlab ye hai"); stored via LearnStore/PersonalMemory; applied next time; Settings shows and deletes each item; backup/restore of learned data to a file the owner keeps (export, restore after reinstall; validate the file on import, size limit, never import keys or voiceprints). Pure parse/validate rules + tests. Privacy: nothing leaves the phone.
### 10 ROUTINES
- "good morning" runs several steps in order. Base = existing Brain skills (Brain.kt, BrainStore.kt, skillpacks/starter.json, tools/validate_skillpack.py). Only Brain.SAFE_KINDS, step limit (Brain.MAX_STEPS), owner can create / edit / delete / list by voice and in Settings, a step that needs a spoken yes still asks, a failed step is reported honestly (no fake "done"). Tests for step parsing, limits, safe-kind filter.
### 11 SEE THE SCREEN
- "dekh ke batao / screen par ye click karo": accessibility screen tree first (NovaAccessibilityService, ScreenSession.kt, analyzeScreen()) and, when useful, a screenshot to Gemini vision. Respect Control.RISKY and the blocked list: banking, payment, passwords, FLAG_SECURE screens cannot be read or tapped: say so plainly. Screen text lives in memory only. Tests for the rules; honest limits list.

## 10. REPO AND PROJECT MAP (read from the real zip)
- Repo https://github.com/devboutique0-wq/NOVA, package com.nova.assistant, Kotlin, Gradle (compileSdk 34, minSdk 26, arm64-v8a only). Dependencies: vosk-android 0.3.47, jna, kotlinx-coroutines, androidx.biometric 1.1.0, androidx.fragment 1.6.2, junit 4.13.2. CI is GitHub Actions ("Unpack zip", "Build and Test APK"); the workflow files live in the repo (.github), not in the zip. Fixed signing key is passed by CI secrets (in-app APK update needs the same key). versionName 6.17.
- UI: ONE web page app/src/main/assets/ui.html (about 1650 lines) talking to Kotlin through the @JavascriptInterface bridge in MainActivity.kt. Data: assets/knowledge.json, skillpacks/starter.json.
- Python contract tests in tools/ (test_ui_contract.py, test_hud_controls.py, etc.) are old helper checks, NOT part of the Gradle build. Known: tools/test_voice_contract.py has stale expectations ("service uses the knob and shows the match number" fails) from before Parts 4-5; fix them or ignore, do not weaken app tests.
- Unit tests: app/src/test/java/com/nova/assistant/ (37 files, now including VoiceFaceRulesTest and RecordRulesTest). One failing test makes the whole build red.
- Kotlin files (main/java/com/nova/assistant), with the first line of their own doc comment:
- AgentRules.kt (279 lines): TASK AGENT (v28)
- AutoSetup.kt (108 lines): v34 PART 3: ONE silent background runner for everything NOVA can prepare by itself once the owner has pressed "allow": 1
- AutoSetupPlan.kt (68 lines): v34 PART 3: pure state machine of the silent background "auto setup" (no Android classes, unit-tested)
- BootReceiver.kt (49 lines): After a phone restart
- Brain.kt (164 lines): NOVA "Layer 1" brain: memory and shortcuts that run instantly on the phone (no model, no network)
- BrainStore.kt (159 lines): Phone-local storage for the Layer-1 brain (shortcuts, event log)
- Cfg.kt (90 lines): Non-secret settings
- Control.kt (89 lines): Pure rules for voice screen control (tap / type / scroll / swipe)
- Driving.kt (193 lines): Driving mode (pure Kotlin, no Android classes, so every rule is unit-testable)
- ErrLog.kt (60 lines): Part 2: a small in-memory list of the last things that FAILED (a phone command, a Gemini call, the whole AI chain)
- ExtCfg.kt (163 lines): Extra settings that live in their own small store: how long NOVA waits after you stop speaking.
- FaceGate.kt (136 lines): Part 5: the second factor for a LOCKED phone
- FreeHttp.kt (70 lines): Part 1B: the real network call for ProviderChain
- FreeImage.kt (337 lines): FIXFREE2: pure helpers with no Android classes, so they run in plain JVM unit tests.
- FreeProviders.kt (449 lines): Part 1A of the free AI provider chain
- GitHubPublish.kt (77 lines): v16 PUBLISH A SITE: new public repo -> index.html -> GitHub Pages, with the owner's own token (SecureStore slot "github")
- GroqStt.kt (94 lines): Optional Groq Whisper listening
- HealthCheck.kt (124 lines): Self-check
- HindiRoman.kt (192 lines): Turns Hindi (Devanagari) text into the Roman (Hinglish) words Logic.classify, LocalSkills and KnowledgeMatcher know
- ImageGen.kt (228 lines): AI image generate / edit with the user's own Gemini key
- ImageIntent.kt (64 lines): v30 FREE IMAGE BY VOICE: "nova ek billi ki photo banao" / "make a picture of a cat on the moon"
- InstallReceiver.kt (45 lines): Receives Android's answer for a PackageInstaller session
- KnowledgeMatcher.kt (309 lines): Knowledge pack matcher (PART 3)
- KnowledgeStore.kt (52 lines): Loads assets/knowledge.json once and answers from it (PART 3)
- LearnRules.kt (81 lines): v16 LEARN FROM THE INTERNET, pure rules (no Android classes, runs in plain JVM tests)
- LearnStore.kt (97 lines): v16 "KYA SEEKHA": notes NOVA learned from the web
- ListenGuard.kt (86 lines): Part 4: NEVER WAKE BY ITSELF / NEVER INTERRUPT, and the safe listening fixes
- LocalBrain.kt (81 lines): NOVA "Layer 2": an optional offline language model that can turn a phrase NOVA did not understand ("make it dark in here") into ONE command 
- LocalBrains.kt (87 lines): Connects the Layer-2 slot to the model file the updater installed (UpdateManager.activeModelPath)
- LocalChat.kt (174 lines): Rules for the on-device chat model (offline answers) and for choosing between cloud and local
- LocalSkills.kt (320 lines): Layer 0 of the offline brain: deterministic daily-life skills
- Logic.kt (482 lines): Pure logic (no Android classes) so it can be unit-tested on every build
- MainActivity.kt (1134 lines): The page is a local asset only: no file/content access, no navigation away, no network sub-requests.
- ModelAuto.kt (67 lines): v17 model downloads
- ModelCatalog.kt (19 lines): The one built-in offline model NOVA can offer without any feed
- ModelDownload.kt (125 lines): Resumable, verified download for big files (the offline model)
- NovaAccessibilityService.kt (490 lines): What this service does (and nothing else): 1
- NovaAssist.kt (48 lines): Lets the user pick NOVA as the phone's default assistant (Settings > Default digital assistant app)
- NovaHud.kt (665 lines): Pure drawing maths for the NOVA card (no Android window code)
- NovaNotificationService.kt (109 lines): Driving mode: sees notifications ONLY from the allow-listed messaging apps (Driving.APPS) and ONLY while the user has switched driving mode 
- NovaService.kt (3307 lines): NOVA foreground service
- PatternSetupActivity.kt (205 lines): Own screen for NOVA's pattern unlock: save the pattern by tapping dots, ON/OFF, TEST, DELETE
- PatternUnlock.kt (185 lines): NOVA's own pattern unlock
- PersonalMemory.kt (286 lines): Personal memory (PART 2): short facts the owner told NOVA to remember ("yaad rakh mera naam Shiv hai")
- PersonalMemoryStore.kt (43 lines): Where the personal facts live: one private SharedPreferences file "nova_memory" on this phone only
- PowerGuide.kt (49 lines): Part 2: opens the screen where the owner lets NOVA keep running in the background
- PowerGuideRules.kt (54 lines): Part 2: which Android "autostart / background" screen belongs to which phone brand, and what to tap there
- PublicKnowledgeFallback.kt (162 lines): API-key-free public knowledge lookup used only for clear, general-knowledge questions
- RecordRules.kt (185 lines): Part 6A: BACKGROUND AUDIO RECORDING
- RecordSession.kt (313 lines): Part 6A: the audio recorder
- ScreenAlert.kt (111 lines): v33 PART 2: the screen ALERT, made rare and useful
- ScreenSession.kt (170 lines): v33 PART 1: JARVIS screen session ("nova screen dekho" starts a SESSION, not a one-shot)
- SecureStore.kt (174 lines): API key is encrypted with AES-256-GCM
- SelfTest.kt (181 lines): Settings > SELF TEST
- SilentRules.kt (119 lines): Part 3: SILENT TURN
- SiteGen.kt (200 lines): v16 WEBSITE BY VOICE, step 2: write ONE self-contained index.html from the owner's own keys and save it on the phone
- SiteIntent.kt (68 lines): v16 WEBSITE BY VOICE: "nova ek restaurant ki website banao" / "make a website for my gym"
- SpeakerRules.kt (64 lines): v17 "only my voice"
- Timers.kt (188 lines): Offline timer / reminder / alarm understanding
- Understand.kt (186 lines): v32 LOOSE LOCAL UNDERSTANDING
- UpdateManager.kt (485 lines): Discover -> Ask -> Add
- Updater.kt (103 lines): One thing NOVA found in the update feed and may offer to the user.
- VoiceFaceRules.kt (150 lines): Part 5: VOICE + FACE
- VoiceLock.kt (198 lines): v17 "only my voice": storage + speaker model + vector extraction
- VoiceText.kt (43 lines): Conservative, local-only normalisation of recognised speech before NOVA's existing router sees it

## 11. OTHER FILES IN THE ZIP
- AUTOPSY_*.md, CLAUDE_*_PROMPT.md, HANDOVER_v16.md, HANDOFF_STATUS.md, NEXT_ACCOUNT_PROMPT_v*.md, NOVA_NEXT_ACCOUNT_PROMPT_3/4/5_*.md (older prompts and audits; this file supersedes them but the 5_AFTER_PART5 prompt has the full Part 5 details), README_NOVA_V6.md, SETUP_STEPS.md, VOICE_RECOGNITION_SAFE_UPGRADE.md, UI notes.
