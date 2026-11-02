# FINAL TEST REPORT - NOVA v6.84
Status words: **PASS** = ran and passed, **FAIL** = ran and failed, **NOT TESTED** = nobody ran it. Nothing below is "release verified".

## 1. What was really run (sandbox, Linux, no phone)
| Check | Command | Result |
|---|---|---|
| Kotlin compile of app/src/main + app/src/test against stubs (androidx, Vosk, WorkManager; real android-34 android.jar; LlamaBrain.kt replaced by a stub) | `bash /ABS/tools/local_compile_check.sh /workdir` | PASS (compile OK) |
| JUnit 4.13.2 | same script | PASS 646 / 646 |
| Python contract scripts + skill-pack validator | `bash tools/run_all_checks.sh` | PASS 48 / 48 |
| Headless Chromium chat UI (fake Android bridge) | `python3 tools/ui_chat_check.py <out>` | PASS 19 / 19 |
| Headless Chromium feature-status panel (fake bridge) | `python3 tools/ui_feature_status_check.py <out>` | PASS 27 / 27 |
| Bridge String-parameter caps (negative control on v6.81 file: 38 uncapped) | `tools/test_bridge_caps_contract.py` (inside run_all_checks) | PASS 55 params, 0 uncapped |
| Secret scan | grep AIza / gsk_ / sk- / ghp_ / github_pat_ / AKIA / xox / hf_ / private keys / keystores | PASS: only fake test fixtures |

## 2. NOT run (cannot be run in the sandbox)
- Real Gradle build / APK / R8 / manifest merge: Maven, Google and Gradle hosts are blocked (HTTP 403). Needs GitHub Actions or `./gradlew testDebugUnitTest assembleDebug` on the owner's side. The Gradle wrapper was generated offline and never executed.
- Real libraries behind the stubs: androidx, WorkManager, Vosk, Porcupine, llama.cpp.
- Everything on a phone: wake word, mic, TTS, Accessibility, calls/SMS/WhatsApp, camera, notifications, WebView recovery, app update install.
- Network features against live services (Gemini, Groq, OpenRouter, GLM, image providers, GitHub Pages publish): written from public docs, never called.

## 3. Regression table (JUnit counts are real, from running every test class one by one; total 646)
| Area | Test classes | JUnit tests | Result in sandbox |
|---|---|---|---|
| Voice listening / speech | 9 | 71 | PASS (logic only) |
| Wake word | 3 | 18 | PASS (logic only) |
| Phone control / command understanding | 10 | 68 | PASS (logic only) |
| Calls / SMS / WhatsApp / music | 6 | 33 | PASS (logic only) |
| Timers / alarms / scheduled tasks | 2 | 11 | PASS (logic only) |
| Driving mode / battery | 2 | 24 | PASS (logic only) |
| Chat sessions (Part 8) | 4 | 40 | PASS (logic only) |
| AI fallback chain / providers / usage | 7 | 115 | PASS (logic only) |
| Memory / learning / offline brain / routines | 12 | 114 | PASS (logic only) |
| Agent / task ledger | 4 | 45 | PASS (logic only) |
| Recording (audio / video) | 3 | 19 | PASS (logic only) |
| Image / site generation | 2 | 12 | PASS (logic only) |
| Updates (feed, SHA-256, APK rules) | 1 | 8 | PASS (logic only) |
| Safety, logging, config, setup, crash note | 7 | 48 | PASS (logic only) |
| HUD / UI math | 4 | 15 | PASS (logic only) |
| Automatic first-open setup chain (v6.84) | 1 | 5 | PASS (logic only) |
| **Total** | 77 | **646** | |

Meaning of "PASS (logic only)": the pure Kotlin rules of that area pass against stubs. Each area is still **NOT TESTED on a phone** (see docs/REAL_DEVICE_CHECKLIST_v6.83.md).
Areas with no JUnit but with Python contract / headless UI checks: chat-session UI (19 headless checks), feature-status panel (25 headless checks), JS-bridge caps (contract), manifest/runtime rules (test_android_runtime_contract.py, 8 static checks), scheduled-task and agent wiring (contract scripts).

## 4. Feature verdicts (verification ladder)
| Feature | Highest level reached | Notes |
|---|---|---|
| Voice listening / wake / TTS | AUTOMATED TEST VERIFIED (logic) | Real mic, Vosk, Porcupine, TTS not run |
| Phone control (Accessibility) | AUTOMATED TEST VERIFIED (logic) | Needs a phone; brand pop-up blocks can stop app opening (v6.78 note) |
| Calls / SMS / WhatsApp | AUTOMATED TEST VERIFIED (logic) | Never sent a real message here |
| Timers / alarms / scheduled tasks | AUTOMATED TEST VERIFIED (logic) | AlarmManager behaviour on device unknown |
| Driving mode | AUTOMATED TEST VERIFIED (logic) | |
| Chat sessions + Part 8 (search, pin, rename, export, migration) | AUTOMATED TEST VERIFIED (logic + headless UI) | Export file box, migration of real old chats, WebView recovery NOT TESTED on a phone |
| AI fallback chain | AUTOMATED TEST VERIFIED (logic, mock provider server) | No live provider call |
| Memory / learning / routines | AUTOMATED TEST VERIFIED (logic) | |
| Agent / task ledger | AUTOMATED TEST VERIFIED (logic) | |
| Recording audio/video | AUTOMATED TEST VERIFIED (logic) | Camera/MediaRecorder never run |
| Image / site generation + GitHub publish | STATICALLY VERIFIED + logic tests | GitHub endpoints never called |
| Updates (feed, SHA-256, signature rule) | AUTOMATED TEST VERIFIED (rules) | Real PackageInstaller flow NOT TESTED |
| Part 9 security (bridge caps, ErrLog redaction, config panel) | AUTOMATED TEST VERIFIED + headless UI | See docs/AUDIT_PART9.md |
| Automatic first-open setup (permission dialog + Settings chain, re-runs while something is missing, bounded) | AUTOMATIC TEST VERIFIED (rules) + STATICALLY VERIFIED (wiring contract) | Real Android dialogs / Settings pages NOT TESTED on a phone; Accessibility, notification access, overlay etc. can only be OPENED, Android never lets an app switch them on |
| Whole app (APK) | ARCHITECTURE/STATIC only | No APK has been built by this process |

## 5. Remaining limitations (nothing hidden)
1. No real build: first red CI log is possible (androidx/Vosk/llama APIs only checked against stubs). Send the log and fix.
2. No device test of anything; the checklist is the real acceptance test.
3. Phone brands (Xiaomi/Vivo/Oppo) may block background pop-ups; NOVA cannot switch that for the owner.
4. Skill/model updates trust the feed owner (SHA-256 comes from the same feed); APK updates are protected by the signing-key rule. Use only your own feed.
5. GitHub publish creates a PUBLIC repo; prefer a fine-grained token.
6. Log lines of tool results can still contain a contact or app name (numbers, e-mail and quoted text are masked).
7. Feature-status "key" buttons are only checked to open their key box without granting anything (weak check).
8. Known unsolved: English question vs Hinglish fact matching in memory (v6.79 note); permissions list kept as the owner requested (QUERY_ALL_PACKAGES, REQUEST_INSTALL_PACKAGES, SYSTEM_ALERT_WINDOW, USE_FULL_SCREEN_INTENT, WRITE_SETTINGS).
