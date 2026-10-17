# NOVA - NEXT ACCOUNT PROMPT (v7.15 = PART 1 of 10). Paste FIRST + attach the part zip.
Rules unchanged (NEXT_ACCOUNT_PROMPT_v7_13_ALL_CODE_DONE.md + v7_14.md): owner NOT a programmer, Android phone only, Hinglish, SHORT, ONE step at a time, add-only (never delete a feature), PASS / FAIL / NOT TESTED with evidence, never touch .github/workflows/build.yml, do NOT push until owner says so. Owner goal: EVERY feature of the zip visible in the app UI AND known to NOVA as a skill (voice + manual), real working logic, modern animated UI, 10 parts, zip + this prompt after each part.

## PART 1 DONE (static only)
ui.html (add-only) now wires the 13 bridge functions the wiring audit listed:
- Settings > wb_box: wait slider (getWaitSec/setWaitSec, 2-10 s), "SAB 5 BROWSER TEST" (testBrowser x5, result via onBrowserTest), "YAADDASHT COPY" (getMemoryText + copyText), site media count (siteMediaCount), getHomeCity / getLocalFirst / getWebBrowsers re-sync on panel open.
- Settings > st_box: collapsed "EXTRA JAANCH": POORI JAANCH ABHI (selfTest), MERI CHATS (listChats), FREE IMAGE TEST (selfTestFree), GEMINI IMAGE TEST (selfTestImage), GALTIYAN SAAF (clearErrors). Image tests ask HAAN/NAHI on the phone first (existing Kotlin dialogs). Placed in <details> because owner earlier wanted tests to run automatically (those still do).

## EVIDENCE
RUN: tools/test_wiring_audit_contract.py = OK (192 UI calls, 211 bridge funs). tools/test_ui_contract.py = ALL PASSED. node --check on the new script = OK. tools/run_all_checks.sh = 78 PASS, 14 FAIL (was 77 / 15).
NOT RUN: Kotlin compile, JUnit, Gradle, APK, UI screenshot, phone. NOT TESTED: every new button on a phone.

## OPEN FOR PART 2 (priority)
1. SAFETY: tools/test_payment_reply_contract.py FAILS: notification service does not call PaymentReplyRules; code/OTP messages are not skipped with a note; money talk has no fixed line and may reach the AI. Verify in NovaNotificationService / AutoReply code whether this is real (likely real: OTP/payment must never be read out or sent to AI). Fix add-only + test.
2. Other FAILs (tools/run_all_checks.sh): autopsy_part2..9, test_no_feature_loss, test_part4_part5, test_site_media, test_social_reply, test_auto_media. Several are guards for the OTHER line (t_orb, hub_b, listenLocked...): ask owner once; real ones = fix.
3. PhotoEditIntent "professional banao" -> ENHANCE null; 3 stale JUnit tests.
4. Ask owner: CI branch main or wip13 (still unanswered).
5. Parts 3-10 plan: Part5 points 3-7 (HUD speak, gallery+photo editor, SiteGen media), skills list vs UI match, modern animations / small-large screens, final zip + build steps.
