# Test report - Part 5 (v6.75), voice + Android control audit

Honest rule: every line is RUN / NOT RUN / PASS / FAIL / NOT TESTED.

## 1. What could be run in this sandbox
| Check | Result |
|---|---|
| kotlinc / Gradle / Android SDK | **NOT RUN**: none installed and the sandbox has no internet (Part 4 used a different sandbox that could download kotlinc) |
| Kotlin compile of the v6.74 + v6.75 changes | **NOT RUN** (the v6.74 files `VoiceDiagnosis.kt` and `UnsupportedRules.kt` have never been compiled) |
| JUnit (new: `CommandCorpusTest` 7 tests, `VoiceDiagnosisTest` 5 tests; old: 571 passed in Part 4) | **NOT RUN** |
| `tools/test_command_corpus_contract.py` (181 corpus rows vs the Python port + 4 source checks on the Kotlin files) | **PASS** |
| `./tools/run_all_checks.sh` (all Python contract scripts + skill-pack validator) | **PASS**: 44 scripts, 0 failed (43 from Part 4 + the new one) |
| Real phone | **NOT TESTED** |

A "PASS (Python port)" means: `tools/classify_mirror.py`, a hand port of the local command pipeline whose word lists and regexes are read out of the Kotlin source, agrees with the corpus. It is NOT a run of the Kotlin code. The real proof is `CommandCorpusTest` on GitHub Actions.

## 2. The 4 classification bugs (found with the port in v6.74, fixed in v6.75)
| # | Sentence | Before | After (port) | Kotlin JUnit |
|---|---|---|---|---|
| a | "volume kam mat karo", "torch on mat karo", "brightness kam mat karo" | ran the positive command | no local command (goes to the AI) | NOT RUN |
| b | "instagram open karo", "chrome khol do", "chrome ko kholo" | not recognised | `open_app` instagram / chrome / chrome | NOT RUN |
| b2 | "is store open" | none | none (must stay none) | NOT RUN |
| c | "gaana bajao tum hi ho" | media play (title lost) | none (goes to the AI); "gaana bajao" is still media play | NOT RUN |
| d | "cancel alarm", "timer cancel karo" | cloud | `unsupported` + honest reply | NOT RUN |
| d2 | "alarm band karo" | wake_off | wake_off (unchanged on purpose) | NOT RUN |

## 3. Command corpus (181 phrases) by family
Each row = how many phrases of that family are in `CommandCorpusTest.kt`. Logic column = the Python port agrees; Kotlin column = NOT RUN here; Device = NOT TESTED (no phone).
| Family | Phrases | Logic | Kotlin JUnit | Device |
|---|---|---|---|---|
| Alarm / timer | 7 | PASS (Python port) | NOT RUN | NOT TESTED |
| Brightness | 9 | PASS (Python port) | NOT RUN | NOT TESTED |
| Call | 5 | PASS (Python port) | NOT RUN | NOT TESTED |
| Media | 13 | PASS (Python port) | NOT RUN | NOT TESTED |
| Modes, memory, quiet, stop, updates, unlock, wake alarm | 15 | PASS (Python port) | NOT RUN | NOT TESTED |
| Navigation / lock | 17 | PASS (Python port) | NOT RUN | NOT TESTED |
| Not allowed by Android (power, reset, cancel alarm/timer) | 10 | PASS (Python port) | NOT RUN | NOT TESTED |
| Open an app | 17 | PASS (Python port) | NOT RUN | NOT TESTED |
| Screen control | 12 | PASS (Python port) | NOT RUN | NOT TESTED |
| Sent to the AI on purpose (n rows) | 15 | PASS (Python port) | NOT RUN | NOT TESTED |
| Settings pages / radio toggles | 24 | PASS (Python port) | NOT RUN | NOT TESTED |
| Time / date / battery | 10 | PASS (Python port) | NOT RUN | NOT TESTED |
| Torch | 11 | PASS (Python port) | NOT RUN | NOT TESTED |
| Volume | 16 | PASS (Python port) | NOT RUN | NOT TESTED |

Not covered by this corpus (and not claimed): speech recognition accuracy (Vosk / cloud STT on real voices), TTS voice quality, the Accessibility service actually tapping or scrolling, Settings pages opening on each phone brand, battery behaviour over hours.

## 4. Android limits NOVA now says out loud (nothing is attempted, nothing is bypassed)
| Request | NOVA does | Why |
|---|---|---|
| power off / restart / reboot | says to use the power button | no public API for third-party apps |
| factory reset / wipe / format | refuses, points to Settings > System > Reset options | destructive, and not allowed for apps |
| cancel alarm / cancel timer | says to use the Clock app | NOVA keeps no handle to an alarm it set (same rule as `Timers.parse`) |
| Wi-Fi, Bluetooth, mobile data, hotspot, airplane mode, location, do not disturb on/off | opens the right Settings page and says the switch is the owner's | Android does not let an app flip these |
| unlock | says unlocking was removed | no voice / pattern / face unlock exists in NOVA |
| banking / payment / secure screens | blocked by the existing safety rules (`SafetyInvariantsTest`, unchanged) | security |

## 5. Wake false-trigger / miss audit (item 4 of the Part 5 list)
Result: **no code change needed.** The rules already exist and have JUnit classes (not run here): `Logic.heardWake` (wake word must open the sentence), `WAKE_CONFIRM_HITS = 4` partial results (a final result counts at once), `WAKE_COOLDOWN_MS = 3000`, `Logic.isFalseWake` (silence or noise goes back to sleep quietly), `WakeStrictTest` 7 / `WakeAdaptTest` 4 / `ListenGuardTest` 17 / `WakeRulesTest` 7 `@Test` methods. How often the wake word misses or false-triggers on a real phone in a real room: **NOT TESTED**.

## 6. Remaining bugs / risks
- Nothing from v6.74 or v6.75 has been compiled. A typo in the new Kotlin would show up as a red build, not as a wrong answer.
- If a corpus row fails on CI, check whether the port or the Kotlin is wrong before editing the test.
- "open karo" alone is still read as an app called "karo" (old behaviour, not touched).
- "open instagram karo" is still read as an app called "instagram karo" (old behaviour, not touched); only the "<app> open karo" order was added.

## 6. Hand compile-review (added at close of Part 5)
No kotlinc here, so this is a READ-THROUGH, not a compile. Checked: `VoiceDiagnosis.kt`, `UnsupportedRules.kt`, the `Logic.classify` hooks (`negated`, `OPEN_TAIL_RE`, `UnsupportedRules` calls), `NovaService` (companion fields `startFailure` / `lastFatal` / `ttsInitCode`, `startForeground` catch, `initTts`, imports `Manifest` / `PackageManager` / `ServiceInfo`), `FeatureStatus` (import `Build`, `hindi_voice=` token matches `testState()`), `ConfigReport` (new default params), `MainActivity.openTtsSettings` and the `ui.html` `tts` fix key, `VoiceDiagnosisTest` expectations vs the texts.
Result: **no syntax, symbol, import or test-expectation problem found.** Still NOT a substitute for GitHub Actions: compile = NOT RUN, JUnit = NOT RUN, phone = NOT TESTED.
