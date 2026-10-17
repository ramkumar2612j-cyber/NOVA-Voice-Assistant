# NOVA - NEXT ACCOUNT PROMPT (v7.18 = PART 4 of 10). Paste FIRST + attach the part zip.
Rules unchanged (v7_13, v7_14, v7_15..v7_17 prompts in zip): owner NOT a programmer, Android phone only, Hinglish, SHORT, ONE step at a time, add-only, PASS / FAIL / NOT TESTED with evidence, never touch .github/workflows/build.yml, do NOT push until owner says so. Only branch `main` is wanted; owner deletes wip branches himself on github.com, one step at a time, after confirming main has the latest code.

## PART 4 DONE (static only)
1. launch() records lastLaunchBlocked (true only when the app never came to the front after the ways tried) and has `resend: Boolean = true`: with false it never re-fires the same intent.
2. musicPlay: play-from-search and the Spotify search-link fallback now go through launch(); a phone-blocked background start returns an honest message and tries NO other app (owner rule: strict app routing, ask before switching). Existing verifyPlayback outcomes unchanged.
3. Real CALL (callTool and the chosen-contact call): ACTION_CALL goes through launch(..., resend=false). A blocked start is no longer reported as "call request sent"; a call is NEVER dialled twice by a retry. If not verified it falls to the existing dialer fallback.
4. Image failure: the provider's honest reason (failMsg: quota 429, key 401, region 403 ...) is now appended to the "photo nahi ban payi" text (it was hidden).
5. UI: videoTxt knows status stop_failed.
6. Guards: new tools/test_part4_launch_contract.py PASS; autopsy_part3 now fully PASS; autopsy_part5 PASS.

## EVIDENCE
RUN: tools/run_all_checks.sh = 84 PASS, 11 FAIL (start of this job: 77/15). NOT RUN: Kotlin compile (new edits in NovaService: launch signature, musicPlay, callTool, chosen-contact call, imageBegin = first compile suspects), JUnit, Gradle, APK, phone. NOT TESTED on a phone: real call placing, music play, blocked-start message. RISK: waitFront for a call may report "blocked" although the call started (then only the dialer fallback opens); needs one real phone test.

## REMAINING 11 FAILs (read, NOT run)
- Other-line guards (ask owner once, never delete): autopsy_part2 (listenLocked / wb_ll), autopsy_part6 (expects `val err: String`, tree uses writerError per v7.14), no_feature_loss ids (hub_b, au_open, ws_go, tr_box, al_batt, ar_ai, mem_copy...), part4_part5 (hasCalendarRead, mem_copy, al_batt, ar_ai, callanswer help topic), social_reply, payment_reply (old strings; behaviour covered by test_payment_ai_guard_contract), auto_media (t_orb, hub_b).
- Probably stale: autopsy_part7 (scheduleAlarmState shape), autopsy_part8 (ALARM_*, postNote, MainActivity brace check), autopsy_part9 (listener helper isolation: check per-helper try/catch in onNotificationPosted).
- REAL, still open: site_media (SiteGen userBlock x3 wiring) = Part 5 point 5.

## NEXT (Part 5)
Part5 points 3-7: HUD speak events, MainActivity gallery picker + photo editor, SiteGen media (userBlock/promptBlock/applyUser), small-file autopsy fixes. Then skills-vs-UI match, animations/responsive, final zip + build steps.
