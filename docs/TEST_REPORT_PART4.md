# Consolidated test report - Part 4 (v6.73), 2026-10-14

## 1. Build tools found in the sandbox
| Tool | Result |
|---|---|
| JDK | OpenJDK 21 present (project targets 17; Kotlin `-jvm-target 17` used) |
| Gradle | 8.14.3 present, but **no Android SDK**, no `ANDROID_HOME`, no `gradlew` in the ZIP |
| Maven Google / Maven Central / Gradle plugin portal / dl.google.com | **blocked (HTTP 403 from the egress proxy)** -> AGP 8.5.2, androidx, vosk, work-runtime, Porcupine, llamacpp-kotlin, kotlinx-coroutines cannot be downloaded |
| kotlinc 1.9.24 | downloaded from github.com releases (works) |
| android-34 android.jar | AOSP build (Reginer/aosp-android-jar) via `git clone` (raw.githubusercontent.com is blocked) |
| JUnit 4.13.2 + hamcrest 1.3 | real jars found in `/opt/gradle-8.14.3/lib` |

**Gradle build (`assembleDebug`, `testDebugUnitTest`): NOT RUN - reason: Android Gradle Plugin and the Android SDK are unreachable from this sandbox.** The substitute is the kotlinc/JUnit harness below.

## 2. What was actually run
| Check | Command | Result |
|---|---|---|
| Compile `app/src/main` (115 files) with real kotlinc 1.9.24 | `tools/local_compile_check.sh` | **PASS, 0 errors** (warnings only) - with stubs for vosk, androidx.{fragment,biometric,work,core FileProvider, NotificationCompat}, Porcupine, a stand-in `R`, and a stand-in `LlamaBrain` |
| Compile `app/src/test` (66 test files) | same | **PASS** |
| JUnit | `JUnitCore`, real JUnit 4.13.2 | first run 571 tests, **1 FAIL**; after the test fix **571/571 PASS** |
| JUnit without android.jar on the runtime classpath (emulates Gradle's plain-JVM unit tests, where android classes are not usable) | same test classes | **571/571 PASS** -> no unit test depends on Android/`org.json` at run time |
| Python contract scripts | `tools/run_all_checks.sh` | **43/43 PASS** (an earlier "hindi" failure was caused by my own `python -I` flag, not by the project) |
| Skill packs | `tools/validate_skillpack.py` | PASS: 193 entries / 1,157 variants |
| ui.html JavaScript | `node --check` per block | 17/17 JS blocks PASS (18th `<script>` is a GLSL shader) |
| UI <-> Bridge | script | 122 distinct `Android.*` calls, **0 missing** `@JavascriptInterface` methods |
| XML well-formedness | python xml.dom.minidom, all `app/src/main/**/*.xml` | PASS |
| Secret scan (Google / Groq / OpenRouter / GitHub token patterns, private keys) over the whole tree | grep | none found |
| New tests of Parts 1-3 | ChatSessionRulesTest 9, FallbackContextTest 15, ConfigReportTest 7, FreeProvidersTurnsTest 3, AgentProgressTest 9 | all PASS (first time they were ever compiled and run) |

Counts: 571 JUnit tests in 66 test files; earlier docs said 434 (Part 8) / 521 (v6.42) - the number grew with new features.

## 3. Compile errors / fixes in this part
- **Real source compile errors found: 0.** The first compile attempt showed 88 `unresolved reference` errors; all were missing third-party libraries (`androidx.work`, `NotificationCompat`, Porcupine, llamacpp-kotlin, coroutines, generated `R`) - not bugs. They were handled by harness stubs (`tools/local_stubs/extra/`), nothing in `app/src/main` was changed for them.
- JUnit FAIL fixed (test, not code): `LogicTest.papaCallRoutesLocallyWithoutCloudKey` expected `Logic.classify("call of duty kholo") == null`. The generic rule "`<app> kholo`" (Logic.kt, open-app rule) now correctly answers `open_app "call of duty"`. The intent of the test - this phrase must never dial anyone - is kept: the assertion is now `Cmd("open_app","call of duty")`, which is not a call. Behaviour was changed earlier on purpose (open any installed app); only the stale expectation was updated.
- Comment fixed: `WavPcm.kt` header still said "voice-lock self test" (that feature was removed in v6.51). Comment only.
- `docs/BUILD.md` told people to run `python3 -m unittest discover tools`, which gives 31 false errors (the scripts call `sys.exit`). Replaced by `tools/run_all_checks.sh`, and the CI workflow now runs it.
- Files changed in this part: `app/src/test/.../LogicTest.kt` (1 assertion), `app/src/main/.../WavPcm.kt` (comment), `app/build.gradle.kts` (versionName 6.73), `.github/workflows/build.yml` (+1 step), `tools/local_compile_check.sh` (extra stubs, real JUnit), `tools/local_stubs/extra/*` (new), `tools/run_all_checks.sh` (new), `.gitignore` (new), `docs/*` (BUILD.md rewritten; FEATURE_REGRESSION_v6.73.md, TEST_REPORT_PART4.md, CONTINUATION_PART4.md, config_templates/ new), README.md, CHANGELOG.md.

## 4. What this does NOT prove (do not call these "working")
1. **Gradle / AGP build and APK packaging**: NOT RUN. Resource merging, manifest merging, R8/lint, real androidx / Vosk / Porcupine / WorkManager / llamacpp APIs may still produce errors that stubs hid. The most likely places: `PorcupineWake`, `OfflineJobQueue` / `NovaCloudQueueWorker`, `LlamaBrain` (not compiled at all here), `MainActivity` (androidx.fragment), the Vosk `Recognizer`/`Model` usage in `NovaService`.
2. **Real-device behaviour** of every feature: NOT TESTED (see section 5).
3. The AOSP android.jar can accept a hidden API that the official SDK rejects.
4. Real provider calls (Gemini, Groq, OpenRouter, GLM, image providers), real Accessibility actions, real WhatsApp / call / alarm hand-offs: NOT TESTED.

## 5. Real-device verification status
No physical phone, no emulator, no APK in this sandbox. Therefore **every item below is NOT TESTED**; the checklist to run after CI is green is `docs/PHONE_CHECKLIST.md` (+ the new Part 1-3 items in CONTINUATION_PART4.md).
| Area | Device status |
|---|---|
| Install + permissions (mic, notifications, contacts, brightness, overlay, Accessibility, notification access, battery optimisation) | NOT TESTED |
| Wake word (Vosk / Porcupine), false wakes, TTS Hindi/English | NOT TESTED |
| Local commands, calls (dialer only / CALL_PHONE), WhatsApp, alarms, timers | NOT TESTED |
| Agent loop on real apps, resume after restart, interruption notification | NOT TESTED |
| New chat / history / delete, backup-AI continuity, "setup check" | NOT TESTED |
| Auto reply, battery SOS, recordings, in-app update, SELF TEST screen | NOT TESTED |

## 6. Feature-by-feature result
See [FEATURE_REGRESSION_v6.73.md](FEATURE_REGRESSION_v6.73.md): 40 features, Logic PASS 36 / NOT TESTED 4 / FAIL 0, Device 0/40 tested. No feature was removed; speaker recognition / voice lock were not added.
