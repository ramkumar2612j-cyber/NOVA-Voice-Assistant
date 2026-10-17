# NOVA Part 3 Status — source changes + static checks

Base preserved: `NOVA-WIP-v75-PART3.zip` source snapshot (derived from Part 2 skills ZIP). Existing package, Kotlin architecture, API/fallback routes, and existing screens were retained; no feature was intentionally removed.

## Implemented in source
- Added a distinct STOP action on the floating HUD. It cancels the agent cancellation token, clears pending yes/no and dictated-message confirmation state without accepting it, stops TTS, then stops the service so its mic/listening lifecycle is released. Restarting NOVA requires the normal ACTIVATE action. A pending call/WhatsApp action is not confirmed by STOP.
- Added persistent SILENT ON/OFF state through the existing SharedPreferences `Cfg.silentMode` setting, accessible from the Home screen, Settings panel, and floating HUD.
- Existing silent reply behavior remains: ordinary speech is suppressed while message text is emitted to chat/HUD; safety confirmation behavior remains on the existing confirmation path. CHUP, `quietReplies`, and chat-speak settings were not removed or merged.
- HUD HAAN/NAHI controls remain distinct from STOP; stray taps still do not dismiss the pending question.
- Added `tools/test_part3_stop_silent_contract.py`.

## Verification
- PASS: `tools/test_part3_stop_silent_contract.py` (8/8 static contract checks).
- PASS: `tools/test_hud_controls.py` (10/10 static checks).
- PASS: `tools/test_help_contract.py` (31 Kotlin help topics match `ui.html` CAPS).
- Full `bash tools/run_all_checks.sh`: initial checks passed through the displayed `test_part5_final_integration.py`, but the command exceeded the environment time limit before a final summary. Therefore the complete suite is **NOT VERIFIED**.
- Kotlin compile, Gradle build, JUnit, APK installation, actual phone microphone/TTS, and real call/WhatsApp flows: **NOT TESTED**. This package must not be described as build-passing until CI/device tests finish.

## Known limitation
The STOP action stops NOVA's service to ensure the microphone/listening loop releases. The user must press ACTIVATE to start listening again. This is intentional and safer than leaving an uncertain background capture active.
