# NOVA Voice Assistant v6.59 — Bug 5 follow-up audit

## Scope
Fix the missing Vosk offline English speech model packaging path with minimum changes. Preserve architecture, user features, safety, v6.57 lead-routing fix/regression test, and optional Porcupine fallback.

## Root cause
`NovaService.loadModel()` copies `model-en` from Android assets and rejects the model unless `am/final.mdl`, `graph/HCLr.fst`, and `graph/Gr.fst` exist. The source ZIP intentionally omitted the large Vosk model, but the GitHub Actions workflow did not prepare it before the build. As a result, CI/local builds could produce an APK with no usable built-in Vosk model.

## Change made
- Added `tools/prepare_vosk_model.sh`.
- Script downloads the official `vosk-model-small-en-us-0.15.zip` over HTTPS, extracts to a temporary directory, checks the four required files (`am/final.mdl`, `graph/HCLr.fst`, `graph/Gr.fst`, `graph/words.txt`), stages the model, and only then replaces `app/src/main/assets/model-en`.
- Updated `.github/workflows/build.yml` to run the preparation step before Gradle unit tests and `assembleDebug`.
- Updated `docs/BUILD.md` with local build steps and limitations.
- Bumped app version from 6.58 to 6.59 and default versionCode from 17 to 18.
- Porcupine remains optional and unchanged; it still needs owner-provided Picovoice AccessKey and custom Android `nova.ppn`. Existing Vosk fallback is preserved.
- Verified SHA-256 equality against v6.57 for `LeadRules.kt` and `LeadQueueRulesTest.kt`; the v6.57 lead routing fix and regression test are unchanged.

## Test results
- Shell syntax (`bash -n tools/prepare_vosk_model.sh`): PASS.
- New packaging contract tests: 4/4 PASS.
- Standalone `tools/test_*.py` scripts: 35/35 PASS.
- Download of the real Vosk model: NOT TESTED (network/DNS unavailable in this environment).
- Gradle unit tests / Kotlin compilation: NOT TESTED (Gradle executable/wrapper not available here).
- APK build: NOT TESTED.
- Real Android phone: NOT TESTED.
- Gemini live API: NOT TESTED; unrelated to this fix.

## Important distinction
The large third-party Vosk model is **not embedded in this source ZIP**. It is fetched and validated during CI/local build. The APK will contain the model only if that preparation step succeeds. Therefore this fix repairs the reproducible build path but is not yet a verified APK or real-device PASS.

## Status
- Bug 5 (model packaging/build pipeline): PASS — source/configuration contract level.
- Actual model download and inclusion in an APK: NOT TESTED.
- Porcupine provisioning: NOT TESTED; owner credentials/model are intentionally not included.
