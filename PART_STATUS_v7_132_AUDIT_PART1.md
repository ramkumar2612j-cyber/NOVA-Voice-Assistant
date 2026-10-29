# v7.132 AUDIT PART 1 of 3 (UI wiring + static check). No app code changed.
Source: NOVA-v7_131-REPLYFIX.zip. Sandbox had NO kotlinc / gradle / SDK / internet, so nothing Kotlin was compiled or run.

## PASS (proved by script, not by phone)
- Bridge <-> UI: 275 @JavascriptInterface methods, 0 unused; every Android.x / A('x') call in ui.html has a Kotlin method (0 missing).
- tools/audit_dead_ui.py: 0 dead controls, 61 help topics all present, 0 retired-rule leftovers.
- Kotlin types never referenced: only NovaApp, NovaInteractionService, NovaRecognitionStub; all 3 ARE declared in AndroidManifest (fine).
- Manifest: every declared component has a class, every Service/Receiver/Activity class is declared; every permission used in code is declared (31).
- tools/run_all_checks.sh: 162 PASS, 1 FAIL.

## FAIL (1)
- tools/test_part4_fx_render.py (headless star field): "stars did not spring back" + "lowfx star count 23 -> 23". Cause read from ui.html: the page's own performance governor puts the lowfx class back in the slow sandbox browser, so the test already sees 23 stars (LOWN) and slower physics. Looks like a sandbox / test-expectation problem, NOT a proven app bug. NOT confirmed on a phone.

## FOUND, NOT CHANGED (owner decision)
- app/build.gradle.kts versionName is "7.114" while the code is v7.131; the app / reports / update check show 7.114. Several old guards pin "7.114" (test_part7_brain_learned_contract.py) so bumping it needs those guards updated together. Left alone (add-only rule).
- v7.131 reply guard (NovaNotificationService line ~158): newestIsOwn uses ownerSpokeLast(lines) without the sent.recent exception that ownLast has. Intended (stops answering NOVA's own echo) but a real owner line followed at once by a person's message could skip one reply. Needs phone test.

## NOT TESTED
Kotlin compile, JUnit, APK build, every voice / accessibility / WhatsApp / MT5 / VoiSpark feature on a phone.

## Next: PART 2 = feature-by-feature static test of the voice command paths (NovaService) + Python ports; PART 3 = fix what Part 2 finds, version note, final zip.
