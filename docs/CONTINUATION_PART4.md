# Continuation notes after Part 4 (v6.73) - build gate + regression sweep
Base for the next part: the latest NOVA-Voice-Assistant.zip. Read docs/CONTINUATION_PART1/2/3.md, docs/TEST_REPORT_PART4.md and docs/FEATURE_REGRESSION_v6.73.md. Never restart from an older zip.

## Done in Part 4
- Build tools audited: JDK 21 + Gradle 8.14.3 exist, **no Android SDK and Google/Maven repos are blocked (403)** -> `gradle assembleDebug testDebugUnitTest` = **NOT RUN**.
- Substitute: `tools/local_compile_check.sh` (real kotlinc 1.9.24 + AOSP android-34 jar + stubs). Main 115 files: 0 errors. JUnit: **571/571 PASS** (Parts 1-3 tests compiled and run for the first time). Python contract scripts 43/43 PASS (`tools/run_all_checks.sh`).
- 1 stale test fixed (`LogicTest`, "call of duty kholo" now opens the app, never a call). 1 stale comment fixed (`WavPcm.kt`). No source bug found.
- Regression sweep: `docs/FEATURE_REGRESSION_v6.73.md` (40 features: Logic PASS 36, NOT TESTED 4, FAIL 0; device 0/40).
- Docs: `docs/BUILD.md` rewritten (GitHub Actions + local Gradle + sandbox check), `docs/config_templates/` (placeholders only), `docs/TEST_REPORT_PART4.md`, `.gitignore`, CI now also runs the contract checks, `versionName` 6.73 (versionCode unchanged).
- No feature removed. Speaker recognition / voice lock NOT added (and none exists).

## Decisions
- Stubs for third-party jars live only in `tools/local_stubs/` and are never part of the APK build. `LlamaBrain.kt` is replaced by a stub only in the sandbox harness; the real file is untouched.
- The stale test was changed because behaviour had intentionally changed earlier (generic "<app> kholo" rule); the safety intent of the test is kept.
- `python3 -m unittest discover tools` is NOT valid for this repo; use `tools/run_all_checks.sh`.

## Pending (in this order)
1. **Run GitHub Actions** ("NOVA Android CI") on this ZIP. Expect possible errors only where stubs hid real APIs: PorcupineWake, OfflineJobQueue/NovaCloudQueueWorker, LlamaBrain, MainActivity (androidx.fragment), Vosk usage. Fix from the red log, then re-run `testDebugUnitTest`.
2. Install `nova-apk` and run `docs/PHONE_CHECKLIST.md` plus the checks below, then SETTINGS > SELF TEST and send the result.
3. Part 1-3 phone checks: (a) NEW CHAT keeps memory (YAAD) but empties the model context; (b) open an old chat from HISTORY and ask about its content; (c) turn the primary AI key off, ask a follow-up, the backup AI must continue the same thread; (d) say "nova setup check"; (e) harmless agent job, kill the service mid-job, expect the interruption notification and "resume last task" only if safe.
4. Open risks carried over: free providers with small context may answer 400 for a 12k-char chat (lower `FULL_FREE_CHARS`); plan/contract not persisted (re-plan on resume); offline model sees only 2 turns.

## Next part
Part 5 (voice + Android control audit, 150+ command corpus) is written in `docs/ROADMAP_PARTS_4_TO_10.md`. Do it after CI is green, so new tests compile on the real toolchain.
