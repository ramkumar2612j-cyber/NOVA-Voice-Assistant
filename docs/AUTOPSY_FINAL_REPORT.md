# NOVA AUTOPSY - FINAL REPORT (R1-R5, v6.99)
Evidence legend: RUN = executed in the sandbox; NOT RUN = could not be executed here; NOT TESTED = needs the real phone.

## Fixes (one line each; details in docs/AUTOPSY_PART1..7.md)
- R1 connectors / web / learning (AUTOPSY_PART4): silent stops traced and fixed, tests in tools/test_autopsy_part4_contract.py.
- R2 open web / download / teach / photo (AUTOPSY_PART5): fixed, tools/test_autopsy_part5_contract.py.
- R3 video + recordings (AUTOPSY_PART6): audio vs video mic clash, swallowed MediaRecorder.stop(), invisible auto-stops, dead writer reported as clean save.
- R4 routines / scheduled tasks / settings (AUTOPSY_PART7): false "saved" when no alarm; repeat job dying on write failure; alarms lost after APK update / time change; stale one-shot fired a day late.
- R5 (this part): no new app bug found; added a LIVE headless UI check for the four owner-required things (tools/ui_owner_check.py) and a syntax-sanity contract (tools/test_autopsy_part8_contract.py).

- R6 (leftover items): notification listener rebind + isolated helpers + logged errors (WhatsApp read path), honest play_music message, dead-service guard for scheduled jobs. Test: tools/test_autopsy_part9_contract.py. Feature list: docs/FEATURE_INVENTORY.md.

## Owner-required UI (never remove)
HOME tab; ACTIVATE (middle of home, becomes STOP); Settings SAVE = done (saves, closes, assemble animation); 3D live orb (WebGL2 #orb3d).
Evidence: ui_owner_check.py 10/10 PASS in headless Chromium with a fake bridge (RUN). Real phone NOT TESTED.

## What was RUN
- ./tools/run_all_checks.sh: all contract scripts PASS except test_vosk_model_packaging (needs .github/workflows, not in this zip) - known, not an app bug.
- Headless Chromium: ui_chat_check.py PASS, ui_feature_status_check.py ALL PASS, ui_shots.py (360 / 390 widths) 0 JS errors and orb3d true, ui_owner_check.py 10/10 PASS.
## What was NOT RUN
- Kotlin compile (kotlinc download needs internet; sandbox network is off), JUnit, Gradle / AGP, GitHub Actions build, the APK.
- Real phone: alarms after APK update, notification when a one-shot is missed, exact-alarm permission flow, voice commands, video / recording on a locked screen.

## Open items
- (closed in R6) WhatsApp READ path traced; play_music message made honest. Plain YouTube still only opens search results (by design).
- WEATHER / SEND_MESSAGE scheduled jobs run only while NOVA is ACTIVE (else a notification).
- Voice stop of video (wake mic paused during video by design).

## Phone test order (owner sends only red lines)
1. GitHub Actions: upload `tools` BEFORE `app`, build, read the test-report artifact, install the APK; check the label "v6.99 . build N".
2. Open NOVA: HOME visible, orb moves, ACTIVATE -> STOP -> ACTIVATE. 3. SETUP: change something, SAVE: panel closes with animation.
4. Say "roz subah 8 baje weather bolo" then "schedule list". 5. Update the APK once more, open NOVA, "schedule list" must still show the job.
6. One music / call / WhatsApp command, one 10-second video, one audio recording.
