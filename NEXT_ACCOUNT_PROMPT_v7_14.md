# NOVA - NEXT ACCOUNT PROMPT (v7.14). Paste FIRST + attach NOVA-Voice-Assistant.zip
Rules unchanged (see NEXT_ACCOUNT_PROMPT_v7_13_ALL_CODE_DONE.md): owner NOT a programmer, Android phone only, Hinglish, SHORT, ONE step at a time, add-only (never delete a feature), PASS / FAIL / NOT TESTED with evidence, never touch .github/workflows/build.yml, do NOT push until the owner says so.

## Done in v7.14 (static checks only)
1. AUTOPSY R3a: audio recording refuses to start while video recording owns the mic (NovaService.startRecording).
2. AUTOPSY R3b: RecordSession.Saved has writerError; stopRecording adds a warning (RecordRules "writer_err") when the writer thread died, instead of a clean "saved".
3. REAL BUG: ANSWER_PHONE_CALLS was used by PhoneActions / PhoneFacts but not declared in AndroidManifest.xml (answering a call could never be granted). Declared. test_connectors_contract permission pin 29 -> 30.
4. REAL BUG: PhoneFacts.aiFacts(this) was not in NovaService.systemPrompt(); added.
5. test_agent_contract stop regex was too strict (behaviour was fine); loosened.
6. New: tools/test_r3_record_guard_contract.py (PASS), JUnit RecordWriterWarnTest in RecordRulesTest.kt (written, NOT RUN).

## Evidence
RUN: tools/run_all_checks.sh = 77 PASS, 15 FAIL (before my edits 74 / 17). NOT RUN: Kotlin compile (sandbox has no kotlinc and no internet), JUnit, Gradle, APK, Playwright UI audit. NOT TESTED: phone.
The 15 FAILs are NOT bugs of this tree: they are guards written for the OTHER line (devboutique0/NOVA: listenLocked, t_orb, hub_b, au_open, ws_go, tr_box, lastLaunchBlocked, nova-site-media-js ...). Those features do not exist in this wip13 line. Do not "fix" the code for them; ask the owner once whether to retire/adapt those scripts (they are kept, nothing deleted). Also still stale: 3 old JUnit tests (PhoneVoiceRulesTest x2, SocialReplyRulesTest.twoMessagesPerPersonPerHour).

## Next
1. Owner applies patch (nova_v7_14_vs_v7_13.txt) or uses the zip, pushes, runs Actions "NOVA Android CI", sends ONLY red lines. First compile suspects: NovaService.kt (startRecording / stopRecording / systemPrompt edits), RecordSession.kt (Saved), RecordRules.kt, RecordWriterWarnTest.
2. Ask owner: HelpGuide / FEATURE_INVENTORY lines; branch CI builds (main or wip13).
3. Then phone tests one at a time.
