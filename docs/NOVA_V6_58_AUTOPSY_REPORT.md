# NOVA v6.58 — Autopsy Audit Report

Date: 2026-10-13  
Input: `NOVA-Voice-Assistant-v6.57-AUDITED.zip`  
Updated version: 6.58 / versionCode 17

## Scope and evidence boundaries

The ZIP was extracted and its project inventory, manifest, Gradle configuration, core routing files, safety rules, selected unit tests, build notes, changelog, and handoff documents were inspected. The archive contains source and tests but no built APK, Vosk model assets, or Porcupine `.ppn` file. No Android device is attached to this execution environment. Gradle and ADB are not installed/usable here.

## Findings

1. **Call request — FIX APPLIED, runtime NOT TESTED.** Before this patch, `Logic.classify()` had no deterministic local grammar for a contact call. A phrase like “Papa ko call karo” could fall through to cloud/model handling, which may refuse when no usable key/provider exists. Added a narrow explicit-call grammar and routed it through the existing `callTool` path. The parser does not call by itself. Contact permission, contact resolution, and Android dialer/CALL_PHONE handling remain in place. Calls now always require explicit spoken confirmation, even if ordinary confirmations are configured to be skipped.
2. **Lead routing — PRESERVED.** `LeadRules.isLeadAcquisitionRequest()` is called before `SiteIntent.parse()` in `NovaService`; the v6.57 test for “mere shop ke liye leads lao” is preserved. This feature currently guides the owner to public listing capture; it does not independently crawl the web for new leads.
3. **Wake-word false activations — NOT TESTED on device.** Source contains strict wake-word rules/tests and a v6.55 tightening of sentence position, repeated partial hits, audio threshold, and cooldown. Source tests do not establish microphone behavior on a real phone. Porcupine is optional and requires the owner's key and a `nova.ppn` model, neither included.
4. **Popup / overlay activation — NOT TESTED on device.** Manifest declares overlay permission and the service includes wake-card behavior. ZIP inspection alone cannot establish the actual trigger sequence or Android OEM background/overlay behavior.
5. **Gemini API — LIVE TEST NOT TESTED.** Source includes encrypted key storage via Android Keystore/AES-GCM and Gemini HTTP request paths. No owner API key was available for a live request; no key was copied into this report. The model ID/key validity/quota/network/API enablement cannot be confirmed without a sanitized live HTTP status from the device.
6. **Build/release readiness — BLOCKED in this environment.** No Gradle wrapper is present and Gradle/ADB executables are unavailable. `tools/local_compile_check.sh` documents external compiler/Android SDK download prerequisites. No APK build or installation was possible here.
7. **Offline voice model — NOT PRESENT in ZIP.** `app/src/main/assets/model-en/` is absent from the archive. Offline Vosk wake/command recognition cannot be declared operational from this source-only ZIP.
8. **API-key/secret scan — no confirmed live credential found.** A heuristic pattern matched a test file; it was not treated as a confirmed secret. The report does not reproduce matched strings. Re-run the repository's official secret scanner in CI before release.

## Changes made

- Added deterministic, bounded local call-intent parsing for “Papa ko call karo”, “call Papa”, and “phone Papa”.
- Routed that parsed intent through the existing `callTool` function; the parser itself never initiates a call.
- Enforced explicit spoken confirmation for calls even when the optional “skip ordinary confirmations” setting is enabled; WhatsApp already had a mandatory confirmation gate.
- Added Kotlin regression assertions for expected phrases and negative/ambiguous cases.
- Bumped `versionName` from 6.57 to 6.58 and default `versionCode` from 16 to 17.
- Preserved the v6.57 lead-routing code and its regression test.

## Test results

- Repository Python contract scripts: **34 PASS / 0 FAIL**.
- Inline JavaScript syntax: **16 PASS / 0 FAIL** after correctly excluding the embedded GLSL shader block (`type="x-shader/x-fragment"`), which is not JavaScript.
- Kotlin unit tests: **NOT TESTED** (Gradle/Kotlin toolchain unavailable here).
- Gradle `testDebugUnitTest`: **NOT TESTED**.
- Gradle `assembleDebug`: **NOT TESTED**.
- Gemini live API: **NOT TESTED** (no key available).
- Real-phone tests (wake word, false wake, popup, call, WhatsApp, background services): **NOT TESTED**.
- API key exposure in report: **PASS** (no key values included).

## Feature status matrix

| Feature | Status | Evidence / limitation |
|---|---|---|
| Lead-vs-website intent | PASS (contract/source) | Existing v6.57 regression preserved; Python site/lead contracts pass. Device behavior not tested. |
| Explicit contact-call intent parsing | PASS (source change only) | Kotlin regression test added, but not executed; phone flow NOT TESTED. |
| Call confirmation and permission gate | PASS (source change only) | `callTool` now always asks spoken yes; phone permission/dialer behavior NOT TESTED. |
| Wake-word grammar | PASS (unit-test source only) | Existing tests inspect rule behavior; runtime microphone behavior NOT TESTED. |
| False-wake resistance | NOT TESTED | Requires real audio/device test. |
| Floating popup/overlay | NOT TESTED | Requires Android overlay permission and device interaction. |
| Gemini key storage | PASS (static inspection only) | AES-GCM + Android Keystore code found; no runtime cryptographic test here. |
| Gemini live request | NOT TESTED | No API key/live HTTP status available. |
| Vosk offline recognition | FAIL (package completeness) | Required model files absent from ZIP. |
| Porcupine wake engine | NOT TESTED | Optional dependency; owner key and `.ppn` absent. |
| WhatsApp message confirmation | NOT TESTED | Source includes confirmation path; no phone execution. |
| SMS draft/send safety | NOT TESTED | Source opens SMS UI and expects user to press Send; device test unavailable. |
| Background/boot services | NOT TESTED | Manifest/source inspected only; no device lifecycle test. |
| Gradle build/APK | NOT TESTED | Gradle unavailable; no APK created. |
| Existing UI/architecture preservation | PASS (change-scope inspection) | Only local intent parser, one existing local dispatch, one test, version/changelog/report changed. Runtime visual regression NOT TESTED. |

## Recommended next steps (no manual coding needed)

1. Import this ZIP into GitHub or Android Studio and run the existing CI build using JDK 17 / Android SDK 34.
2. If CI fails, fix compile/dependency issues before installing.
3. Add the official Vosk model asset through the project's documented build workflow; do not commit API keys.
4. On the phone, run `docs/PHONE_CHECKLIST.md`, recording each result as PASS/FAIL/NOT TESTED.
5. In NOVA Settings, enter the owner's Gemini key privately and run the built-in API test. Record only HTTP status and sanitized error category.
6. Test “Papa ko call karo” with a contact saved as Papa. Verify that NOVA asks for confirmation before placing a call. Do not use a real recipient during initial tests.
