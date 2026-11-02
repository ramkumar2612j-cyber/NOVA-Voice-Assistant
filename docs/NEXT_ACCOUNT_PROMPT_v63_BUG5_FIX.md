# NOVA v6.59 — Continuation Prompt for Next AI

You are continuing the user's existing Android project NOVA Voice Assistant. Current source package: `NOVA-Voice-Assistant-v6.59-BUG5-FIX.zip`.

## Hard constraints
- Preserve architecture, UI, features, security and minimum-change/zero-regression approach.
- Do not remove/rewrite features without permission.
- Preserve v6.57 lead-routing fix and regression test. They were checked byte-for-byte against v6.57 in this package.
- Never expose API keys in logs, reports, commits or ZIPs.
- Calls and WhatsApp messages must require appropriate confirmation.
- Give easy Hindi/Hinglish steps; user does not code manually.
- Report PASS/FAIL/NOT TESTED honestly. Do not call a feature working from source inspection alone.

## Bug 5 fix in v6.59
Root cause: `NovaService.loadModel()` expects `app/src/main/assets/model-en` and runtime requires `am/final.mdl`, `graph/HCLr.fst`, and `graph/Gr.fst`, but the source ZIP omitted the large Vosk model and prior CI workflow never downloaded it.

Changes:
- Added `tools/prepare_vosk_model.sh` to download the official Vosk `vosk-model-small-en-us-0.15.zip` over HTTPS and validate `am/final.mdl`, `graph/HCLr.fst`, `graph/Gr.fst`, and `graph/words.txt` before staging assets.
- Updated `.github/workflows/build.yml` to run that script before `testDebugUnitTest assembleDebug`.
- Updated `docs/BUILD.md` and added `docs/NOVA_V6_59_BUG5_AUDIT.md`.
- Version 6.59, default versionCode 18.
- Optional Porcupine was not changed. It still requires the owner's Picovoice AccessKey and custom Android `nova.ppn`; existing Vosk fallback remains.

## Evidence so far
- `bash -n tools/prepare_vosk_model.sh`: PASS.
- New Vosk packaging contract tests: 4/4 PASS.
- Standalone `tools/test_*.py` contract scripts: 35/35 PASS.
- `LeadRules.kt` and `LeadQueueRulesTest.kt` SHA-256 match v6.57 exactly.
- Live model download: NOT TESTED (network/DNS unavailable).
- Gradle/Kotlin build, APK build, live API and real-phone tests: NOT TESTED.
- This is a source ZIP, not a verified APK. Do not claim runtime PASS until CI downloads the model, Gradle tests/build pass, and a real phone test succeeds.

## Next steps
1. In a network-enabled GitHub Actions/Android build environment, run `./tools/prepare_vosk_model.sh` and verify it downloads and validates the real model.
2. Run `gradle --no-daemon testDebugUnitTest assembleDebug` using JDK 17, Android SDK 34 and Gradle 8.7.
3. Verify the generated APK contains the `model-en` assets and installs.
4. On a real Android phone, test wake word, false wakes, popup, English/Hinglish command recognition, call confirmation, WhatsApp confirmation and background/boot lifecycle. Never place a real call/message without explicit user confirmation.
5. Run Gemini live API test using the owner's key inside NOVA Settings; do not ask the user to paste it into chat.
6. Update the audit report with separate Gradle/unit/API-live/real-phone results. Keep all unknown results NOT TESTED.
