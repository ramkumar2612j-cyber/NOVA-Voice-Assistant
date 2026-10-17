# NOVA - AUTOPSY FINISHED (R1-R5). Next stage: BUILD + PHONE TEST. Paste this FIRST + attach NOVA-Voice-Assistant.zip

## Owner and rules
Owner is NOT a programmer, Android phone only. Hinglish, SHORT, ONE step at a time, full clickable links. Say PASS / FAIL / NOT TESTED, never "done" without evidence. NEVER remove a feature. Zip name exactly NOVA-Voice-Assistant.zip. After EVERY part give the zip (compulsory) AND the next-account prompt with FULL details (use this file as template). Do all work yourself; do not hand tasks back to the owner. Repo: github.com/devboutique0-wq/NOVA (branch main).

## Owner-required UI - must stay exactly (guards: tools/test_autopsy_part7_contract.py + tools/ui_owner_check.py)
1. HOME page: bottom tabs HOME / SKILLS / LIMITS / CHAT / SETUP, HOME first.
2. ACTIVATE button in the middle of home (id btn): ACTIVATE when off, STOP when on, CHUP beside it.
3. Settings (SETUP) bottom: SAVE (id s_save) = DONE: saves, closes panel after 0.9 s, plays assemble animation (tfAssemble); BAND KARO closes without saving.
4. 3D live orb on home: WebGL2 canvas #orb3d, live (breathing, listen / think colours, intro tunnel); 2D orb only as fallback.
Any ui.html change: run both guards and keep all four.

## Done so far
R1 connectors/web/learning (docs/AUTOPSY_PART4.md); R2 open web/download/teach/photo (PART5); R3 video + recordings (PART6); R4 routines / scheduled tasks / settings (PART7: no false "saved" without alarm, repeat job survives write failure, alarms re-armed after APK update / time change / app open, stale one-shot reported as missed); R5 final report docs/AUTOPSY_FINAL_REPORT.md + live UI check; R6 leftovers (notification listener rebind, honest play_music, dead-service guard) + docs/FEATURE_INVENTORY.md (old / new / evidence A-B-U). Sandbox: all contract scripts PASS except test_vosk_model_packaging (needs .github/workflows, not in zip). Headless UI checks PASS.

## Job now
1. Owner uploads `tools` BEFORE `app` to GitHub, runs Actions "NOVA Android CI", sends ONLY red lines / errors from the Gradle build and test-report artifact (APK is uploaded before unit tests). Fix compile errors one at a time (R4/R6 edits in ScheduledTaskStore.kt, ScheduledTaskReceiver.kt, BootReceiver.kt, MainActivity.kt onCreate, NovaNotificationService.kt (onListenerDisconnected), NovaService.kt (play_music, isAliveNow) are the first suspects: they were never compiled).
2. Then phone tests ONE at a time from docs/AUTOPSY_FINAL_REPORT.md "Phone test order". Owner sends only what failed. Fix proven bugs only, one check per fix in a new tools/test_*_contract.py.
3. Keep the label "v6.99 . build N" visible so the owner can tell the new APK is installed.

## Open items
Kotlin compile / JUnit / Gradle / APK: NOT RUN. Phone: NOT TESTED. Plain YouTube only opens search results (by design); WEATHER / SEND_MESSAGE scheduled jobs run only while NOVA is ACTIVE; voice stop of video (wake mic paused during video by design).

## Status update (this account, final of sandbox stage)
- Re-run ./tools/run_all_checks.sh: 74 PASS, 1 known FAIL (test_vosk_model_packaging: .github/workflows/build.yml lives in the GitHub repo, NOT in this zip - do NOT add or overwrite it).
- Bracket-balance scan of the 6 never-compiled files: ScheduledTaskStore, ScheduledTaskReceiver, BootReceiver, NovaNotificationService = balanced. MainActivity.kt (+5 "{") and NovaService.kt (+3 "{", +18 "(") = INCONCLUSIVE (crude scanner, probably false alarm from string templates / raw strings); only a real kotlinc on GitHub Actions can settle it.
- Kotlin compile / JUnit / APK: NOT RUN. Phone: NOT TESTED. No code was changed in this stage.
- Next: owner uploads tools then app to GitHub, runs "NOVA Android CI", sends only red lines. If MainActivity.kt / NovaService.kt error, look at R4/R6 edits first.
