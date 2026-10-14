# NOVA v39 — Screen safety and self-test correction

## Owner workflow
- Phone-only (Chrome + GitHub web), non-technical. Reply in Hinglish, keep instructions to one action at a time, give clickable links.
- Never claim DONE/WORKING/VERIFIED without evidence. Current evidence below is static/source-contract testing only unless explicitly stated.
- Required ZIP filename stays `NOVA_v15_assistant.zip`. Original project files retained.

## What changed in this part
1. `NovaAccessibilityService.agentSnapshot()` now checks `ScreenSession.isProtected(...)` before collecting any visible nodes. It returns an empty list and clears cached node references when the current package is protected. This closes a gap where the task-agent snapshot path did not apply the same screen policy as `readScreen()`.
2. `SelfTest.kt` no longer expects Android Settings text to be readable. It reports PASS when the active package is protected and the returned text list is empty; FAIL if text is exposed; WARN if the screen/window was not suitable to verify.
3. Added structural regression checks to `tools/test_screen_session_contract.py` and `tools/test_feature_check_contract.py`.
4. Bumped app version to `6.19` / default versionCode `8`.

## Safety boundary
- The banking/payment/UPI/password/OTP/system-settings protection was NOT removed. This was deliberately retained because protected screens must not be automated or sent to cloud services. Opening an app and ordinary, unprotected screen understanding can still be supported within Android's accessibility limits.
- No API key values or notification text were added to logs/tests.

## Evidence and remaining work
- Source files were read before the targeted edits.
- `tests ran here`: all 24 `tools/test_*.py` contract scripts passed (the Hindi contract was run without `-I`; the other 23 were run with `-I`).
- `tests ran here`: `node --check` passed for all 14 inline JavaScript blocks; the shader block was correctly skipped as non-JavaScript.
- No XML files were changed. `xmllint` is not installed in this environment.
- `Kotlin not compiled`: no Gradle executable or Gradle wrapper is present in this archive/runtime. The full Android build must run in GitHub Actions.
- `not phone tested`: owner must install the new APK and re-run Settings > SELF TEST, then manually test screen reading on a normal app such as Chrome.
- Next step: package the complete ZIP, run available static/contract tests, then upload it to the repository and run the `Build NOVA from zip` workflow. If the workflow is red, repair only the reported lines.

## Owner's next action
Open the GitHub upload page, upload `NOVA_v15_assistant.zip`, commit it, then run `Build NOVA from zip` in Actions. Do not reuse the old APK; this source has not yet been compiled by CI.
