# NOVA review progress (static source review + pure-Kotlin rules harness; Android build unavailable)

Evidence level for Android features: WRITTEN + STATICALLY REVIEWED unless a later section says otherwise. The local pure-Kotlin harness does not make the app CI-GREEN. Project is NOT CI-GREEN and NOT PHONE-TESTED.

## Part 1 of 6 - build files, manifest, themes, pure-rule files + tests  (DONE)
Read: build.gradle.kts (root + app), settings, gradle.properties, AndroidManifest.xml, themes (values + values-v31), strings,
nova_file_paths.xml, voice_interaction.xml.
- Every class named in the manifest exists (MainActivity, FaceGateActivity, PatternSetupActivity, VideoRecordService, NovaService,
  InstallReceiver, BootReceiver, NovaAccessibilityService, NovaNotificationService, NovaInteractionService, NovaSessionService,
  NovaRecognitionStub). FileProvider paths match the folders the code writes to (Music/rec, Movies/rec).
- Permissions match the foreground-service types (microphone; camera|microphone) for targetSdk 34. minSdk 26 so java.time is fine.
- BootReceiver correctly does NOT start the microphone service at boot (Android 14 rule); it shows a tap-to-start notification.
Pure-rule files read line by line and every test walked by hand (several also re-run through a Python port of the same logic):
BackgroundVideoRules, ContextMemory, RecordingVoiceRules, RoutineRules, TeachRules, RecordRules, SilentRules, VoiceFaceRules
(VoiceTune, LockGate, FaceGateState), SpeakerRules, PhoneActionRules, ErrLog, PowerGuideRules, ListenGuard, Logic timing constants,
SafetyInvariantsTest (Part 6B/7/11 additions), Control.blocked / ScreenSession.isProtected.
Result: no compile problem and no failing test found in these files.

### REAL BUG FOUND AND FIXED (would not have shown up in CI)
TeachRules.isIntent treated ANY sentence containing " is " or " means " as "teach NOVA an alias". So "what is the weather",
"battery is low", "pyar ka matlab kya hai" were swallowed in NovaService.handle() before the AI / command parser ever saw them,
and a junk alias (e.g. "what" = "the weather") was saved.
Fix (TeachRules.kt): only "X = Y" and "X ka matlab Y" count by themselves; "X is Y" / "X means Y" count only after a teach word
(yaad rakho / remember / teach); any sentence with a question word (kya, what, why, how ...) or ending in "?" is never teaching.
Tests added (existing ones untouched): TeachRulesTest.ordinaryQuestionsAreNeverTreatedAsTeaching, explicitTeachingSentencesStillWork.

### Notes (not bugs, for later parts)
- Part 6B video: voice STOP is not possible while video records (MediaRecorder owns the mic); STOP = notification or Settings.
  Video start needs NOVA visible (Android 14); otherwise status "not_visible".
- "yaad rakho X = Y" is taken by the Part 2 personal-memory parser first (by design), so it is saved as a memory, not an alias.
- BootReceiver notification id 2 is also used by VideoRecordService; harmless.
- Bridge method clearErrors is not called from ui.html (harmless).

## Part 2 of 6 - NovaService.kt lines 1-1700 (DONE - static review)
Read the lifecycle/TTS/wake and command-capture paths, voice enrollment/tuning and lock gate, audio-loop state transitions, one-turn processing, and handle() routes through line 1700. Traced the audio buffers into `ListenGuard.trimTail`, `voiceAllows`, Groq Whisper / Gemini transcription, and local-vs-AI routing.
- Defect found and fixed: the Groq branch called Whisper for any non-empty PCM clip, while `MIN_HEAR_BYTES` documents a 25,600-byte (~0.8 s) minimum and the Gemini branch already checks it. `GroqStt.transcribe` accepts clips as small as 3,200 bytes, so the service could upload clips shorter than the stated limit. Added the same `pcm.size >= MIN_HEAR_BYTES` gate to the Groq route.
- Added a source-contract assertion to `tools/test_voice_contract.py` for the shared minimum. Re-ran all 17 `tools/test_*.py` scripts directly: **17/17 passed**. (`unittest discover` is not a valid runner for these files because they are executable scripts that call `sys.exit` at module level; direct execution gives the correct results.)
- Independent static passes: Android/API/lifecycle; listening/audio path; safety/privacy and confirmation routing. No Android build possible here: Kotlin 1.9 compiler exists, but Gradle, Android SDK/android.jar, and JUnit jars are not installed. Static review is not CI-GREEN and nothing is PHONE-TESTED.
- Review note: the audio endpoint variable is permanently false in this loop, so the inline comment saying Vosk endpointing ends capture early is stale; actual capture ends through silence/fast-end/max-duration rules. I left this alone because the configured silence rules may be intentional for compound commands; this should be confirmed on-device rather than changing endpoint behaviour blindly.


## Part 3 of 6 - NovaService.kt lines 1700-end (DONE - static review)
Read the remaining `handle` fallback and AI tool path, free-provider phone-action parser integration, confirmation flow (`askFirst` / `processAnswer`), local tools and call/message routes, task agent checks, recording/video commands, Gemini request construction and bounded key/model failover, and the screen-session lifecycle / live-screen checks.
- **Safety bug found and fixed:** the `ScreenSession.Intent.REPLY` branch asked for a spoken yes, typed a draft, then automatically tapped a Send label. That contradicted `ScreenSession.kt`'s explicit contract that a screen session can only talk/type and never send. Removed the Send-button tap and delay; the spoken prompt now says the owner presses Send manually, and NOVA reports the draft was typed without claiming it was sent.
- Added `ScreenSessionTest.replyConfirmationOnlyOffersTypingNotSending` and `tools/test_screen_reply_safety_contract.py`; updated the stale expectation in `tools/test_screen_session_contract.py` so it now forbids automatic Send. This makes the safety contract stronger rather than relaxing it. Manually walked the new Kotlin assertions against the exact Hindi/English prompt strings and the service reply branch.
- Re-ran every `tools/test_*.py` script directly: **18/18 passed**. This includes the corrected screen-session contract, the new no-send source guard, and the Groq minimum guard.
- Other routes statically checked: tool errors return failure text rather than claiming success; Gemini requests use bounded calls/deadline and do not log API keys or response bodies; risky calls / WhatsApp sends go through pending spoken confirmation; the task agent validates parsed actions and screen protections before execution. No Android compile or live phone verification was possible.
- Note for phone verification: the screen session needs Android 11+ to verify secure-screen screenshots. Protected apps/screens are meant to stay local/refused; the owner must press Send manually after a reply draft is typed.

## Part 4 of 6 - Android files + end-to-end wiring (DONE - static review)
Read/reviewed: MainActivity (including native JavaScript bridge and permission/key handlers), FaceGate, RecordSession, VideoRecordService, RecordingManager, NovaAccessibilityService, FreeHttp, FreeProviders, GroqStt, TeachStore, BrainStore, and the recording/screen/provider routes in NovaService. This is source review only; not an Android build.

Fixes made in this part:
- Groq text-chat fallback now uses the current documented `openai/gpt-oss-120b` and `openai/gpt-oss-20b`; Groq docs say the old `llama-3.3-70b-versatile` and `llama-3.1-8b-instant` IDs shut down on 2026-08-16. Contract regression guard added.
- `openPublished()` now checks parsed HTTPS URI, real `*.github.io` hostname, no user-info, and only default/443 port; a URL that merely contains `.github.io/` can no longer pass.
- Agent screen snapshots now exclude both text and `contentDescription` from editable controls (only hint + empty/filled state is exported). Service documentation now correctly describes explicit screen sessions and confirmed agent tasks.
- Video service start/stop/destroy now serialize access to shared camera/recorder state, no longer silently substitutes a different camera facing, and removes tiny non-recording temp files.
- FREE AI UI no longer implies the keyless Pollinations text call will work; it explicitly says that anonymous text request may fail, and the fallback list includes Qwen, Z.ai, and offline fallback.

Limits left honest and unresolved: `RecordSession.stop()` joins the encoder worker for up to 15 seconds. Under an unusually backed-up or blocked encoder, a current segment may not be finalized by that join deadline. This cannot be responsibly verified in the sandbox; a long recording/STOP test on the actual phone is still required. Legacy Camera/MediaRecorder and screen-off capture are OEM-dependent. FaceGate, voice lock thresholds, and recording lifecycle have not been tuned on the owner’s actual phone.

## Part 5 of 6 - ui.html buttons, IDs, and bridge calls (DONE - static review)
- Read the whole `ui.html` including settings, wake/chat, 3D canvas, update center, image/site UI, self-test, provider/recordings manager, teach aliases, routines, and feature center.
- All 11 inline JavaScript blocks pass `node --check`. All 108 literal `Android.method()` calls matched a corresponding `@JavascriptInterface` method (123 bridge methods are exposed; several are intentionally called through helper/dynamic paths or not used by this page).
- The 66 static button IDs were checked for code references/wiring. Every direct literal DOM selector resolves; only `mode` and `tf` are absent and both references are explicitly null-guarded/optional. No static button with missing wiring was found in this pass.
- This is JS syntax/source-contract verification only; it does not execute WebView callbacks on Android or prove a tap works on-device.

## Additional cross-check evidence
- All 19 `tools/test_*.py` scripts pass when run directly: **19/19**. `tools/validate_skillpack.py`: **193 entries, 1157 variants**.
- Lightweight Kotlin/KTS lexer-balance check passed for 119 files. This checks balanced delimiters only and is NOT a Kotlin compile.
- Focused independent static passes covered compile/API-risk, voice/audio path, confirmation/safety, provider endpoint/model freshness, privacy boundaries, native bridge/DOM IDs, and recording lifecycle. No separate AI reviewer or Android device was available in this sandbox; do not claim multiple external AIs reviewed it.

## Part 6 of 6 - final cross-check and repack (DONE - static review)
- Cross-check against the original upload: all **210 original ZIP paths are present**; no original project file is missing. Final change set is limited to 14 existing files and 3 new review/test/checklist files.
- Final available checks: **19/19** Python contract scripts passed when executed directly; `tools/validate_skillpack.py` reports **193 entries, 1157 variants**; all **11/11** inline JavaScript blocks pass `node --check`; ZIP integrity is checked on the repacked output.
- The checks above are source/contract validation, not an Android build. This environment has no Gradle or Android SDK/android.jar. A Kotlin delimiter check is not a Kotlin compile. No physical phone and no separate external AI reviewer were available, so neither external multi-AI review nor runtime behavior is claimed.
- Deliver one complete `NOVA_v15_assistant.zip`, with the full project at the archive root, plus `PROGRESS.md` and `PHONE_CHECKLIST_AFTER_CI_GREEN.md` included in the project.
- Project evidence remains for all Parts 1-11: **WRITTEN + STATICALLY REVIEWED; NOT CI-GREEN, NOT PHONE-TESTED**.
- Do not run the phone checklist until GitHub Actions shows a green “Build and Test APK” run after “Unpack zip”.


## Part 7 — Autonomous-agent Slice A: durable task lifecycle (DONE IN SOURCE; CI PENDING)
The owner clarified that NOVA should become a JARVIS-like agent that plans, acts, observes, fixes and verifies tasks. Following the ordered handoff plan, this part completes the first foundation slice rather than pretending the entire software-engineering agent is already built.

Files added:
- `app/src/main/java/com/nova/assistant/AgentTaskRules.kt`: pure Kotlin state machine, bounded history/retry/runtime definitions, task-summary redaction and narrow status-command matching.
- `app/src/main/java/com/nova/assistant/AgentTaskLedger.kt`: private SharedPreferences ledger with synchronous commits, backup value, task history capped at 20 records, step counts, transitions and process/service restart recovery. It does not save audio or screen content; only a short redacted task label and coarse result/status are persisted.
- `app/src/test/java/com/nova/assistant/AgentTaskRulesTest.kt`: transition, waiting/confirmation, interruption, retry deadline, terminal state, summary-redaction and command-routing tests.
- `tools/test_agent_task_ledger_contract.py`: source contract for start-before-worker, stop/cancel wiring, recovery-without-replay, status query, redaction and truthful completion status.

Wiring changes in `NovaService.kt`:
- Persist agent task before launching the worker; track planning/running/confirmation/verifying/terminal states and count completed steps.
- Spoken `ruko`/stop persists CANCELLED. Service stop cancels an active task. On later service startup, stale active tasks become INTERRUPTED and are not automatically replayed (to avoid a duplicate tap/send).
- `agent status`, `task status`, `kaam ka status`, `pichla kaam` and `agent history` report the latest stored task.
- When the model returns `done`, NOVA says it has reported completion and asks the owner to verify the screen; it is recorded as `REPORTED_COMPLETE`, not verified `SUCCEEDED`. There is still no independent end-to-end success verifier for arbitrary UI tasks.
- Persisted task labels/messages are redacted and capped; per-step ledger messages do not include the text typed into target apps or any captured screen excerpt.

Validation in the chat sandbox:
- Pure Kotlin rule harness compiled with local `kotlinc` and passed 18 assertions (the harness uses a tiny Logic stub, so this is only for `AgentTaskRules.kt`, not app compilation).
- The new Kotlin unit test class compiled and its 7 test methods passed against local lightweight test/Android/JSON stubs. This checks syntax/rule behavior only; stubs are not a real Android runtime.
- Static source contract: 28 assertions passed (see `tools/test_agent_task_ledger_contract.py`).
- The first broader Python-script run found one failure in `test_autosetup_contract.py` because adding lifecycle code pushed `AutoSetup.stop()` outside that old test's first-600-character scan. The service stop call was moved to the beginning of `onDestroy()`—the stop behavior is preserved and that test now passes.
- Final full rerun after the code adjustment: **20/20 `tools/test_*.py` scripts passed**; skillpack validator reports **193 entries / 1,157 variants**; all **11/11** inline JavaScript blocks pass Node syntax check. Pure Kotlin harness was re-run after redaction changes: **18 assertions pass**, and the 7 method tests pass using stubs. These still are not Gradle/Android CI results.

Limitations remaining:
- The new task engine does not yet persist/resume an executable workflow safely after a process death. It intentionally stops and marks the task INTERRUPTED.
- `RETRYING`/retry-budget rules exist and are unit-tested, but the existing UI agent does not yet implement automated code repair/rebuild retries.
- The agent is still a screen/UI operator. It does not yet contain a multi-file source workspace, general project editor, arbitrary project GitHub commit workflow, GitHub Actions run/log/artifact polling, or source-code auto-repair loop.
- Task ledger rules/source checks are not Gradle/Android build results. Require a real green GitHub Actions run and owner phone test.

Evidence for Part 7: WRITTEN + STATICALLY REVIEWED; local pure-rule checks passed; NOT CI-GREEN; NOT PHONE-TESTED.

## Part 8 — Real compile check (kotlinc 1.9.24 + android-34 android.jar) — 2026-10-11
For the first time the main sources were compiled with a REAL Kotlin compiler (not delimiter checks). Stubs were used only for vosk, androidx.core FileProvider, androidx.fragment, androidx.biometric and junit (tools/local_stubs). Rerun with `tools/local_compile_check.sh`.
Real compile errors found and fixed (these would have failed GitHub CI):
- `RecordingManager.export()` used `return false` inside an expression-bodied function -> changed to a block body.
- `TeachRules.parse()` declared `split` as a nullable Pair and then used `split.first` -> declared non-null (`?: return null` already guards it).
Result after fixes: main = 0 errors (53 harmless warnings); unit tests = 434 run, 434 pass (run from the app/ directory); 20/20 Python contract scripts; skillpack 193 entries / 1,157 variants; 11/11 JS blocks; all XML parses; no missing @style/@drawable/@string/@xml references; every manifest class exists.
Still NOT proven: Gradle/AGP packaging, real androidx/vosk jars (stubs only), android.jar is an AOSP build (a hidden API would compile here but not in the SDK), lint, runtime on a phone. Status: still NOT CI-GREEN, NOT PHONE-TESTED.

## Part 8 — Real compile check (kotlinc 1.9.24 + android-34 android.jar) — 2026-10-11
For the first time the main sources were compiled with a REAL Kotlin compiler (not delimiter checks). Stubs were used only for vosk, androidx.core FileProvider, androidx.fragment, androidx.biometric and junit (tools/local_stubs). Rerun with `tools/local_compile_check.sh`.
Real compile errors found and fixed (these would have failed GitHub CI):
- `RecordingManager.export()` used `return false` inside an expression-bodied function -> changed to a block body.
- `TeachRules.parse()` declared `split` as a nullable Pair and then used `split.first` -> declared non-null (`?: return null` already guards it).
Result after fixes: main = 0 errors (53 harmless warnings); unit tests = 434 run, 434 pass (run from the app/ directory); 20/20 Python contract scripts; skillpack 193 entries / 1,157 variants; 11/11 JS blocks; all XML parses; no missing @style/@drawable/@string/@xml references; every manifest class exists.
Still NOT proven: Gradle/AGP packaging, real androidx/vosk jars (stubs only), android.jar is an AOSP build (a hidden API would compile here but not in the SDK), lint, runtime on a phone. Status: still NOT CI-GREEN, NOT PHONE-TESTED.

## v6.39-v6.41 + v6.42 check
- v6.39-6.41: no auto-start, owner-only voice, locked-phone voice unlock, battery alerts + SOS, schedule days/prio/note, morning greeting + wake alarm (NOT compiled on Android, NOT phone-tested).
- v6.42: kotlinc 1.9.24 se 55 android-free main files + 55 test classes compile kiye (shim: MessageMode, ApiUsage). 521 JUnit tests PASS (BatteryRules, WakeRules, ScheduledDays shamil). 
- Fix: ChatText JSON regex (quote par overrun), WhatsAppCommandRules recipient casing.
- Wake alarm: sirf 06:00-12:00, OFF tak baar baar (20 min give-up hata diya); DISMISS = 1 min baad phir.
- v6.43: docs reorganized (README, CHANGELOG, docs/).
- NOT RUN: Gradle/Android compile (NovaService, MainActivity Bridge, android-dependent files), 7 android-dependent tests, phone test.

## v6.83 (Parts 9 and 10)
- Part 9 done in source (bridge caps, ErrLog redaction, config panel check, secret scan, audit). Part 10: docs/FINAL_TEST_REPORT.md, docs/REAL_DEVICE_CHECKLIST_v6.83.md, README/BUILD updated, versionName 6.83.
- RUN: compile + 641 JUnit, 47 contracts, 19 + 25 headless UI checks (sandbox, stubs). NOT RUN: Gradle/APK, phone.
