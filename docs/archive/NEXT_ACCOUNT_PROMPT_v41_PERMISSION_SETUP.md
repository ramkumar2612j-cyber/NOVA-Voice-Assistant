# NOVA v41 — Guided permission setup after app load

## Owner workflow
- Owner uses Android phone + Chrome/GitHub web only; provide short Hinglish instructions and one action at a time.
- Keep the ZIP name exactly `NOVA_v15_assistant.zip` and retain existing project files.
- Never claim DONE/WORKING/VERIFIED without evidence. Distinguish source tests, CI build, and real phone testing.

## What changed in this part
1. App version bumped to `6.20` / default versionCode `9`.
2. After the intro finishes, the Update Center opens on first launch, when a new version is detected, or while the guided feature permissions remain missing.
3. Update Center's `AGLA SETUP` path guides the owner in order through microphone, notifications, accessibility, overlay, notification access, contacts, write-settings, and battery settings.
4. The setup screen explains that Android special permissions still need the owner's own manual switch. The app does not silently grant permissions or open permission dialogs without an owner tap on an action inside the setup center.
5. Retained the v39 protection: `agentSnapshot()` fails closed for protected packages, and Self Test expects protected Android Settings text to remain blocked.
6. Updated the UI contract test to cover auto-opening the setup UI, guided permissions, and the no-auto-grant rule.

## Evidence from this environment
- `tests ran here`: all 24 `tools/test_*.py` scripts passed. `test_hindi_contract.py` was run without `-I`; the other 23 scripts were run with `-I`.
- `tests ran here`: `node --check` passed for all 14 inline JavaScript blocks; one non-JavaScript block was skipped.
- No XML files were changed, so no XML lint was required.
- `Kotlin not compiled`: no installed Gradle executable or Gradle wrapper is available in this workspace. The full Android build must run in GitHub Actions.
- `not phone tested`: install the new APK, then confirm the setup center opens after the intro and can guide each permission. Re-run SELF TEST and paste the report.
- `tests ran here`: final ZIP integrity check passed (`No errors detected`); size is 1.75 MB, below the 25 MB limit. The archive contains the updated sources and v41 handover; generated caches/build folders are excluded.

## Safety boundaries retained
- Android requires owner confirmation for runtime and special permissions; an app cannot silently turn Accessibility, notification access, overlay, or modify-system-settings on.
- Protected banking/payment/UPI/password/OTP/system screens remain blocked from automated reading/control/cloud forwarding. Ordinary unprotected screens can use accessibility only where Android exposes their screen tree.
- No API keys or notification message contents are logged or added to the handover.

## Next step
Upload the updated `NOVA_v15_assistant.zip` to the repository root through GitHub web, commit it, then run the `Build NOVA from zip` workflow. Download the `nova-apk` artifact from that exact green run and test the newly installed APK; do not reuse an older APK.

## Owner's one action
Open `https://github.com/skfashion142/NOVA-Voice-Assistant/upload/main`, upload `NOVA_v15_assistant.zip`, and commit the change. Then run `https://github.com/skfashion142/NOVA-Voice-Assistant/actions/workflows/build.yml`.
