# NOVA v6.58 — continuation prompt for the next AI account

I am continuing my existing Android project NOVA Voice Assistant. I do not know coding; explain in simple Hindi/Hinglish and make edits yourself. Never ask me to write code manually.

## Current source package
Use `NOVA-Voice-Assistant-v6.58-AUTOPSY.zip` as the current source of truth. It was based on my supplied `NOVA-Voice-Assistant-v6.57-AUDITED.zip`.

## Hard rules
- Preserve existing architecture, UI, features, security, and all working behavior. Minimal changes only; zero regression.
- Do not remove or rewrite features without explicit permission.
- Preserve the v6.57 lead-routing fix and its regression tests.
- Never expose API keys in logs, reports, prompts, screenshots, or ZIPs.
- Never place a call or send SMS/WhatsApp without the existing required confirmation. For the first phone test, use a harmless test contact/number.
- Every feature must be marked PASS, FAIL, or NOT TESTED based on evidence. Source inspection alone is not proof that runtime works.
- Report Gradle build, Kotlin unit tests, live API tests, and real-phone tests separately.

## v6.58 changes already made
- Added a narrow local command grammar for “Papa ko call karo”, “call Papa”, and “phone Papa” so intent parsing does not depend solely on Gemini/provider availability.
- Routed this command to the existing `callTool` safety/permission/confirmation path.
- Tightened safety so calls always require explicit spoken confirmation, even when the optional skip-ordinary-confirmations setting is enabled. WhatsApp already required confirmation.
- Added Kotlin regression tests, but they have NOT yet been executed because Gradle/Kotlin tooling was unavailable in the previous environment.
- Bumped app version to 6.58 / default versionCode 17.
- Kept the v6.57 lead-routing guard and test intact.
- Read `docs/NOVA_V6_58_AUTOPSY_REPORT.md` and `CHANGELOG.md` for the audit findings.

## Known unverified items
1. Kotlin compile and unit tests have not run.
2. `assembleDebug` has not run; the archive has no Gradle wrapper and the prior environment did not have usable Gradle/ADB.
3. The ZIP lacks Vosk `model-en` assets and a Porcupine `nova.ppn` file.
4. No Gemini live API request was possible because no owner key was provided. Ask me to enter it only privately inside NOVA Settings; never ask me to paste it into chat.
5. Wake-word false positives, popup/overlay, background service lifecycle, calls, WhatsApp, and other phone behavior still require real-device tests.

## Required next actions
1. Inspect the updated ZIP and verify that no unrelated source changes occurred.
2. Run all repository `tools/test_*.py` contract scripts and report counts.
3. Run Gradle `testDebugUnitTest` and `assembleDebug` with JDK 17 / Android SDK 34 in a proper Android build environment; fix only proven errors.
4. Verify the new Kotlin call tests compile and pass.
5. Inspect Gemini key save -> encrypted storage -> retrieval -> request -> sanitized error reporting. Never log key values or full sensitive request/response bodies. Do not claim Gemini fixed until a live request succeeds.
6. Investigate popup activation by tracing wake state, service lifecycle, overlay permission, and screen sessions; add targeted regression tests before modifying behavior.
7. Run `docs/PHONE_CHECKLIST.md` on a real Android phone. Mark each result accurately and record model/Android version, but do not include private data or keys.
8. Keep call and WhatsApp confirmation safeguards. Do not test by contacting a real person without permission.
9. After verified fixes, bump version again only if further source changes are made, update changelog and audit report, and export a complete updated ZIP.
10. End with a simple Hindi/Hinglish summary and a new continuation prompt.

## Output required
- Bug list with cause/evidence and severity.
- Feature matrix with PASS / FAIL / NOT TESTED.
- Separate Gradle build, Kotlin unit test, Python contract test, Gemini live API, and real-phone outcomes.
- Downloadable complete source ZIP, changelog, audit report, and next-account prompt. Never fabricate a sandbox link or claim an artifact exists until it is actually created.
