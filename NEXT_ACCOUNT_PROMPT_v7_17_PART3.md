# NOVA - NEXT ACCOUNT PROMPT (v7.17 = PART 3 of 10). Paste FIRST + attach the part zip.
Rules unchanged (v7_13, v7_14, v7_15_PART1, v7_16_PART2 prompts in zip): owner NOT a programmer, Android phone only, Hinglish, SHORT, ONE step at a time, add-only, PASS / FAIL / NOT TESTED with evidence, never touch .github/workflows/build.yml, do NOT push until owner says so. Only branch `main` is wanted (owner decision); he must delete wip branches himself on github.com, one step at a time, after confirming main has the latest code.

## PART 3 DONE (static only)
1. REAL BUG: NovaNotificationService had no onListenerDisconnected: if Android dropped the listener, auto-reply / alerts stopped silently. Added requestRebind(ComponentName) (minSdk 26 OK), failure goes to ErrLog.
2. REAL BUG: NovaService learn said "seekha aur yaad rakh liya" even when LearnStore.addKept returned null (duplicate topic). Now checks keptId; duplicate = honest text "pehle se note hai, dobara save nahi kiya".
3. REAL BUG: ConnectorApi n8n "executions" sent a spoken workflow NAME as workflowId (n8n wants the id). Now resolved with n8nWorkflowId(base, h, wf); unknown name = honest error "n8n par 'X' workflow nahi mila" (never "all runs").
4. Guards: new tools/test_part3_contract.py PASS; tools/test_autopsy_part4_contract.py now fully PASS.

## EVIDENCE
RUN: tools/run_all_checks.sh (numbers in chat). NOT RUN: Kotlin compile (3 new Kotlin edits: NovaNotificationService, NovaService learn block, ConnectorApi executions = first compile suspects), JUnit, Gradle, APK, phone.

## REMAINING FAILs (classified by reading code, NOT run)
- Other-line guards (feature does not exist here; ask owner once whether to retire/adapt, never delete): autopsy_part2 listenLocked / wb_ll (this tree uses LockGuard + wb_lg instead), no_feature_loss ids (hub_b, au_open, ws_go, tr_box, al_batt, ar_ai, mem_copy...), part4_part5 (hasCalendarRead, mem_copy, al_batt, ar_ai, callanswer help topic), social_reply (SocialReplyRules gate wording), auto_media (t_orb, hub_b), payment_reply (old strings).
- Probably stale: autopsy_part7 (guard expects private fun scheduleAlarmState; tree has scheduleAlarmState with different shape), autopsy_part8 (ALARM_* constants: none misused; notifyMissed/notifySkipped postNote; MainActivity brace check), autopsy_part9 (listener helper isolation: check NovaNotificationService onNotificationPosted try/catch per helper).
- Probably REAL, still open: autopsy_part3 (musicPlay uses raw startActivity instead of launch(); launch() does not record blocked start; callTool ACTION_CALL not through launch()), autopsy_part5 (image failure should include the provider reason), autopsy_part6 (UI does not know status stop_failed), site_media (SiteGen userBlock x3 wiring).

## NEXT (Part 4)
Fix the REAL open ones above (music launch verification first: owner explicitly wanted real playback check + honest "not found"), UI stop_failed text, image provider reason. Then Part5 points 3-7, skills-vs-UI match, animations/responsive, final zip + build steps.
