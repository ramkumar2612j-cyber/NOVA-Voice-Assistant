# NOVA - NEXT ACCOUNT PROMPT 7 (after the independent review, Part 1 of 6 done; read fully before doing anything)

You are taking over the NOVA Android voice assistant project. The owner is non-technical and works from a phone. Read every section. Follow section 1 exactly.

## 0. STATE WHEN THIS PROMPT IS GIVEN
- All 11 parts of the owner's list (and the 6A/6B/6C split of Part 6) now have CODE in the zip (NOVA_v15_assistant.zip). Evidence level of ALL of it: WRITTEN + STATICALLY REVIEWED. NOTHING from Parts 1-11 is CI-GREEN or PHONE-TESTED. The last real GitHub build (v36b) compiled and ran 329 unit tests with 1 failure (UnderstandTest, fixed afterwards but never rebuilt). Everything written since (about 90 new tests, Parts 1-11, FaceGate, RecordSession, VideoRecordService, RecordingManager, PhoneActionRules, ContextMemory, TeachRules/TeachStore, RoutineRules, ListenGuard...) has NEVER been compiled. The chat sandbox has Java and Python but NO Kotlin compiler, NO Gradle, NO Android SDK and NO network.
- The owner asked for an independent review of the whole zip in 6 chat parts so that the first GitHub build has as few surprises as possible. Plan and progress:
  * REVIEW PART 1 - DONE: build files (gradle, manifest, themes, xml), every pure-rule file (BackgroundVideoRules, ContextMemory, RecordingVoiceRules, RoutineRules, TeachRules, RecordRules, SilentRules, VoiceFaceRules, SpeakerRules, PhoneActionRules, ErrLog, PowerGuideRules, ListenGuard) and their tests were read line by line; every new test was walked by hand (RecordRules, SilentRules also through a Python port: 0 mismatches). No compile problem found in those files. Python contract tests: 17/17 pass (run in the sandbox).
  * REVIEW PART 2 - TODO: NovaService.kt lines 1-1700 (about 200 KB file in total): mic loop, recording feed hook, face/voice gate, handle() routing.
  * REVIEW PART 3 - TODO: NovaService.kt lines 1700-end: runTool, agent, video/recording routes, notifications, onDestroy.
  * REVIEW PART 4 - TODO: MainActivity, FaceGate, RecordSession (already read once: no problem found), VideoRecordService, RecordingManager, NovaAccessibilityService, FreeProviders, TeachStore, BrainStore and the other Android files.
  * REVIEW PART 5 - TODO: ui.html (160 KB). Already checked: every Android.xxx call in ui.html has a Kotlin @JavascriptInterface method (some are called through the helper functions safe() / parse() / call(), so a plain grep for "Android.name(" misses them). Still to read: wrong element ids, buttons that call nothing.
  * REVIEW PART 6 - TODO: final cross-check of the whole project, ONE repacked zip NOVA_v15_assistant.zip, and a short Hinglish phone check list. A file PROGRESS.md inside the zip records what was read and fixed; update it after every review part.
- REAL BUG FOUND AND FIXED IN REVIEW PART 1 (keep this lesson: bugs like it are invisible to CI): TeachRules.isIntent treated any sentence containing " is " or " means " as "teach NOVA an alias", so ordinary questions ("what is the weather", "battery is low", "pyar ka matlab kya hai") were swallowed in NovaService.handle() before the AI or the command parser saw them, and a junk alias was saved. Fixed: only "X = Y" and "X ka matlab Y" count alone; "X is Y" / "X means Y" count only after a teach word (yaad rakho / remember / teach); sentences with a question word or ending in "?" never count. Two regression tests were added to TeachRulesTest (old tests untouched).
- Things found in review that are honest limits, not bugs (tell the owner): (a) Part 6B video: voice STOP is impossible while video records because MediaRecorder owns the microphone and the wake-word loop is paused; STOP is the notification or the Settings button. (b) Video can only be started while NOVA is visible (Android 14 refuses camera foreground services from the background); otherwise the status says "not_visible" and nothing is recorded. (c) "yaad rakho X = Y" is taken by the Part 2 personal-memory parser first, so it is saved as a memory, not as an alias. (d) BootReceiver notification id 2 is also used by VideoRecordService (harmless). (e) bridge method clearErrors is not called from ui.html (harmless). (f) The first-build suspects are still: androidx dependency resolution (biometric 1.1.0, fragment 1.6.2), FaceGate.kt, RecordSession.kt (MediaCodec / MediaMuxer), VideoRecordService.kt (legacy Camera + MediaRecorder), NovaService recording and video blocks, ui.html.
- FIRST JOB for the next account: continue the review at REVIEW PART 2 (read NovaService.kt for real, do not skim), update PROGRESS.md, then continue to Part 6 and deliver the zip. After that the owner uploads the zip, runs "Unpack zip", then "Build and Test APK" (ONE build for everything); fix whatever the build says until CI-GREEN; then the owner does the phone check lists (see section 12).
- Owner's demand (applies to everything): "no mistakes at all; have the work analysed by several AIs/reviewers; NOVA must hear clearly what I say". Before delivering run independent review passes and a hand walk-through of every new test. Say plainly what could not be checked.

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

## 4. THE 11 PARTS (owner agreed this order; code for all of them exists, see section 8 for the evidence level)
1. CAMERA. "mere mobile ka camera kholo aur front camera se video on karo" in one sentence. camera tool has mode (photo|video) and facing (front|back); video opens the MediaStore video camera; front sends CAMERA_FACING / LENS_FACING_FRONT / USE_FRONT_CAMERA extras (best effort). NOVA cannot press the record button; it tells the owner to tap it. Understand.classify returns null for camera + video/front/selfie/record words so the AI chooses the arguments.
2. SELF-DIAGNOSIS. ErrLog (last 30 failures, memory only, keys masked) in HealthCheck and SelfTest; Settings buttons AKHRI GALTIYAN and BATTERY / AUTOSTART; PowerGuide opens the brand's autostart screen with a standard-Android fallback. The health report lists which AI keys are SET (never the key): verified present in HealthCheck.kt.
3. SILENT MODE ("chup rehna"): SilentRules. A waiting yes/no is always spoken; failures show the real text; "Kaam complete ho gaya" only for torch/volume/brightness.
4. NEVER WAKE BY ITSELF / NEVER INTERRUPT: ListenGuard (noise estimate, joiner-aware fast end, trailing-silence trim, neutral Whisper hint with echo / no_speech filter, no proactive speech while people talk).
5. VOICE + FACE: VoiceTune (10-sentence self-tuning), LockGate + FaceGateState + FaceGateActivity (BiometricPrompt as second factor on a locked phone; owner chose "both voice AND face"). Android gives apps no face-unlock data.
6. BACKGROUND RECORDING: 6A audio (RecordRules, RecordSession, the single mic loop feeds it, .m4a in 30-minute parts, MediaStore Music/NOVA), 6B video (BackgroundVideoRules, VideoRecordService camera|microphone foreground service, file-growth check before saying ON, MediaStore export), 6C manager (RecordingManager, RecordingVoiceRules: list / play / share / delete with confirm dialog, count and play-last by voice, auto-delete option default OFF, crash-file recovery).
7. EVERY FREE AI CAN CONTROL THE PHONE + QUOTA METER: PhoneActionRules (strict allow-listed JSON, never executes; goes through the same runTool / confirmation path), provider health and cooldown in Settings.
8. CONTEXT MEMORY: ContextMemory (RAM only, 120 s, risky kinds never repeated).
9. TEACH ONCE: TeachRules, TeachStore (alias table, list/delete, export/import with a strict schema).
10. ROUTINES: RoutineRules + BrainStore (Brain.SAFE_KINDS only, max 8 steps, Settings add/edit/delete).
11. SEE THE SCREEN: ScreenSession / NovaAccessibilityService; protected apps and FLAG_SECURE screens stay blocked (Control.blocked, ScreenSession.isProtected).

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
In Hinglish, 3 to 5 short lines: which review part you are on, what you found, and the single next step. Nothing else.

## 8. STATUS TABLE OF ALL 11 PARTS (evidence levels: WRITTEN / CI-GREEN / PHONE-TESTED)
| Part | Name | Code | Reviewed by reading | CI-GREEN | PHONE-TESTED |
|---|---|---|---|---|---|
| 1 | Camera | WRITTEN | partly (rules + tests) | NO | NO |
| 2 | Self-diagnosis | WRITTEN | rules + tests read (ErrLog, PowerGuideRules) | NO | NO |
| 3 | Silent mode | WRITTEN | rules + tests walked, 0 mismatches | NO | NO |
| 4 | Never wake / never interrupt | WRITTEN | rules + tests walked | NO | NO |
| 5 | Voice tuning + face gate | WRITTEN | rules + tests walked; FaceGate.kt not yet read | NO | NO |
| 6A | Audio recording | WRITTEN | rules walked; RecordSession.kt read, no problem found; NovaService part still to read | NO | NO |
| 6B | Video recording | WRITTEN | rules walked; VideoRecordService only skimmed | NO | NO |
| 6C | Recordings manager | WRITTEN | voice rules walked; RecordingManager not yet read | NO | NO |
| 7 | Free AI phone actions | WRITTEN | PhoneActionRules + tests walked | NO | NO |
| 8 | Context memory | WRITTEN | ContextMemory + tests walked | NO | NO |
| 9 | Teach once | WRITTEN | rules read, 1 real bug fixed | NO | NO |
| 10 | Routines | WRITTEN | RoutineRules + tests walked | NO | NO |
| 11 | See the screen | WRITTEN | safety test + Control.blocked checked; ScreenSession / accessibility still to read | NO | NO |
Also still wanted by the owner from earlier chats and each to be shown as really working: Hindi command model, knowledge pack, personal memory, free AI provider chain with automatic fallback (no asking the user); one complete zip at the end.

## 10. REPO AND PROJECT MAP (read from the real zip at the time of prompt 6; files added since: BackgroundVideoRules, VideoRecordService, RecordingManager, RecordingVoiceRules, PhoneActionRules, ContextMemory, TeachRules, TeachStore, RoutineRules, ListenGuard, FaceGate, plus PROGRESS.md and NOVA_PARTS_6A_TO_11_AUDIT.md)
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

## 12. PHONE CHECK LISTS FOR THE OWNER (Hinglish; give them one at a time, only after a green build)
- Part 3 silent: (a) "nova torch jala do, chup rehna": torch on, no voice, card "Kaam complete ho gaya". (b) "nova torch band karo" (no silent phrase): speaks normally. (c) a call request with "chup rehna": it still ASKS yes/no aloud.
- Part 5: Settings > AWAAZ SAVE then AWAAZ TEST (10 sentences): chat shows "Naya limit". FACE CHECK TEST: phone's face/fingerprint screen appears, "pass hua" on success. Lock-gate ON, lock the phone, "nova torch jala do": face screen, pass, torch on. Cancel: nothing happens. Another person: nothing happens. If the screen never appears, check "Display over other apps".
- Part 6A audio: ACTIVATE, Settings > RECORDING SHURU: notification with STOP + timer. Screen off 2 minutes, status time grows. "nova recording band karo": saved, file in Music > NOVA plays. Notification STOP also stops and saves. Run 10+ minutes and check for gaps.
- Part 6B video: start while NOVA is visible; status says ON only after the file grows; lock screen 2 minutes; stop via notification or Settings; play the file; report the exact phone and Android version if capture stops with the screen off.
- Part 6C: recordings list: play, share, delete asks a confirm dialog; auto-delete is OFF by default; "kitni recordings hain", "last recording sunao".
- Part 7-11: harmless action first ("torch"); calls and messages still ask a spoken yes; "isko band karo" after a safe action; teach "mummy = Sunita" then delete it in Settings and check that "what is the weather" is NOT swallowed as a teach sentence; a 2-step safe routine; a banking app: NOVA must refuse to read or tap it.
