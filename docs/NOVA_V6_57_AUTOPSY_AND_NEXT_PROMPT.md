# NOVA v6.57 — audit handoff

## What was actually changed

1. Added `LeadRules.isLeadAcquisitionRequest()` for explicit lead/customer discovery phrases.
2. Added a dedicated route before website generation in `NovaService`. It no longer silently builds a website for an explicit lead request; it explains the existing public-listing capture workflow and states that independent web-wide lead discovery is not implemented in this build.
3. Added a JVM test covering “mere shop ke liye leads lao”, “business leads dhundo”, and ensuring website requests do not match the lead route.
4. Version bumped to 6.57 / versionCode 16.

## Verification results

- All 34 `tools/test_*.py` contract checks passed after the patch.
- Full Android Gradle build was **not run**: this archive has no Gradle wrapper, and the sandbox did not have Gradle installed. The repository's `tools/local_compile_check.sh` also could not complete because its compiler/Android SDK download prerequisites were unavailable. Do not claim Kotlin compilation or APK build passed.
- No Gemini API key was present for a live request. The key was not copied into the ZIP or any report. The currently configured text model `gemini-3.5-flash` is listed in Google's official Gemini API model documentation, so the source model name alone does not prove the key is broken. Check key validity, API enablement, quota, network, HTTP status, and returned error on the phone.
- The provided screenshot is not enough to prove the exact popup trigger. Wake false positives, a running screen session, conversation mode, or overlay/UI state need on-device event-log testing. No claim is made that this issue is fixed.
- A physical call to “Papa” cannot be tested from a ZIP. It requires a resolvable contact/number and Android's supported dialer flow; the user should confirm before an actual call. The app should not silently place calls.

## Manual acceptance tests (must be performed on a phone)

1. Fresh install, deny overlay permission: app must not repeatedly open Android Settings or show repeated popups.
2. Say ordinary sentences without “Nova” for 5 minutes: no assistant activation/overlay should occur. Save the wake diagnostics and error log if it does.
3. Say “Nova, Papa ko call karo”: NOVA should resolve the contact or ask which Papa/number, then show a confirmation/dialer step; it must not claim a call was made unless the phone reports it.
4. Say “mere shop ke liye leads lao”: it must not create a website. It should explain the public-listing capture flow.
5. Open a public business listing in Google Maps and say “Nova, lead capture”: verify the candidate details before saving.
6. Enter a Gemini API key in the app's own Settings and run its built-in key/API test. Record exact HTTP status and sanitized error; never paste the key into chat or logs.
7. Test text chat, voice recognition, TTS, image generation, website generation, lead capture/drafts, WhatsApp confirmation, alarms, background behavior, battery optimization, and permissions individually. Mark each PASS/FAIL/NOT TESTED rather than calling the entire app working.

## Prompt for the next AI account

You are continuing maintenance of my existing Android project NOVA, archive `NOVA-Voice-Assistant-v6.57.zip`. I do not know coding, so explain results in simple Hindi/Hinglish and make changes yourself where possible.

STRICT RULES:
- Preserve the existing project, architecture, features, permissions/security boundaries, and UI. Minimum change, zero regression. Do not rewrite or remove features.
- First inspect the entire ZIP and this handoff document. Make an evidence-based audit before editing. Do not assume previous test claims are true.
- Main reported failures: (1) “Papa ko call karo” gets refused instead of offering the correct safe call flow; (2) asking for leads creates a website; (3) floating popup/assistant activation happens during ordinary conversation without saying the wake word; (4) Gemini API key does not work; (5) other features may be UI-only or broken.
- v6.57 already adds a lead-intent guard. Preserve it unless a test proves it is wrong.
- Run every repository test and a real Android/Kotlin build if tools are available. Clearly separate source/static tests, unit tests, APK build, API live tests, and real-device tests. Never call a test passed if it was not run.
- Investigate popup activation using `Logic.heardWake`, `NovaService`, `NovaHud`, `ScreenSession`, `ExtCfg.convo`, foreground service lifecycle, and logs. Do not simply disable all voice functionality. Add deterministic regression tests for ordinary speech not triggering a wake.
- Investigate Gemini by tracing key storage -> key retrieval -> HTTP request -> model fallback -> error display. Never print, commit, or include API keys in logs/ZIP. Do not claim a key is fixed without a live API test using the owner's key on their device.
- For calls, use Android-supported contact/dialer APIs and explicit user confirmation. Never silently call/message anyone or claim completion without a result.
- Add tests for each fix, bump version safely, update changelog and audit report, then export the complete updated project ZIP. Preserve all unrelated files.
- Final output must include: exact changed files, test counts/results, what is still unverified, how to test on Android, updated ZIP, and a new next-account prompt. Do not ask me to do coding manually.
