# NOVA AUTOPSY - last part R5 of 5. Paste this FIRST + attach NOVA-Voice-Assistant.zip

## Owner and rules
Owner is NOT a programmer, uses an Android phone only. Reply in Hinglish, SHORT, ONE step at a time. Say PASS / FAIL / NOT TESTED, never "done" without evidence. NEVER remove a feature. Zip name must be exactly NOVA-Voice-Assistant.zip. After EVERY part give the zip (compulsory) AND the next-account prompt with FULL details (this file is the template). Do all the work yourself, do not hand tasks back to the owner.

## Owner-required UI - must stay exactly like this (a test guards it: tools/test_autopsy_part7_contract.py)
1. HOME page: bottom tab bar HOME / SKILLS / LIMITS / CHAT / SETUP; HOME is the first tab.
2. ACTIVATE button in the middle of the home screen (id btn): text ACTIVATE when off, STOP when on, plus CHUP next to it.
3. Settings (SETUP) page bottom: SAVE (id s_save) works as DONE: saves, closes the panel after 0.9 s and plays the assemble animation (tfAssemble); BAND KARO closes without saving.
4. 3D live orb on home: WebGL2 canvas #orb3d (falls back to the old 2D orb only if WebGL2 fails / phone is slow). It must keep running live (breathing, listening / thinking colours, intro warp tunnel).
If any change touches ui.html, run the test and keep all four.

## Plan and status
R1 connectors/web/learning DONE (docs/AUTOPSY_PART4.md); R2 open web/download/teach/photo DONE (docs/AUTOPSY_PART5.md); R3 video + recordings DONE (docs/AUTOPSY_PART6.md); R4 routines / scheduled tasks / settings DONE (docs/AUTOPSY_PART7.md: 4 fixes - false "saved" when no alarm, repeat job dying on write failure, alarms lost on APK update / time change, stale one-shot fired a day late); R5 self test + final report + final zip = THIS JOB.

## Job R5
1. Run ./tools/run_all_checks.sh; expect all PASS except test_vosk_model_packaging (needs .github/workflows, not in zip). Run the headless UI checks in tools/ (ui_chat_check.py, ui_feature_status_check.py, ui_shots.py) if chromium exists; if not, say NOT RUN.
2. Re-read the 4 R4 edits once for Kotlin syntax (ScheduledTaskStore.kt, ScheduledTaskReceiver.kt, BootReceiver.kt, MainActivity.kt onCreate) because kotlinc is NOT available in the sandbox; try tools/local_compile_check.sh if a Kotlin compiler exists.
3. Write docs/AUTOPSY_FINAL_REPORT.md: every fix of R1-R5 in one line each, what is RUN vs NOT RUN, open items (below), and the phone test order.
4. Update README status line, CHANGELOG, versionName only if needed, keep the label "v6.99 . build N".
5. Fix only proven bugs, one check per fix in tools/test_autopsy_part8_contract.py.
6. Give the final zip + a final next-account prompt for the GitHub Actions build / phone test stage.

## Open items carried over
- Kotlin compile, JUnit, Gradle / GitHub Actions build: NOT RUN. Real phone: NOT TESTED.
- WhatsApp READ (notification path) not traced; YouTube play_music only opens search results.
- WEATHER / SEND_MESSAGE scheduled jobs run only while NOVA is ACTIVE (else a notification); onScheduledTask returns silently if the service is not alive.
- Voice stop of video (wake mic is paused during video by design); screen-off camera on real phones.
- Build on GitHub Actions: upload `tools` BEFORE `app`, read the test-report artifact; phone tests one at a time, owner sends only red lines.
