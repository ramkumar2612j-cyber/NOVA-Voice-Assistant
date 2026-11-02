# NEXT ACCOUNT PROMPT - NOVA v6.83 -> continue Part 9, then Part 10
Paste this whole file in the new chat with the latest NOVA-Voice-Assistant.zip.
OWNER RULE: NOVA does every job directly, no yes/no/permission chatter. Only pay/buy/password/OTP/bank screens stay refused. Every feature REAL, no demo/fake success. Hinglish short replies. Zip name exactly NOVA-Voice-Assistant.zip. Owner is a non-programmer Windows user: exact copy/paste steps, no giant code dumps. Never say DONE/VERIFIED without evidence.

## VERIFIED THIS SESSION (sandbox, real runs)
- STEP 0 complete: `bash /ABS/tools/local_compile_check.sh /workdir` -> compile OK, JUnit OK (639 tests). (Needs android.jar: raw.githubusercontent.com is blocked, use the sparse git clone from the comment in the script: github.com/Reginer/aosp-android-jar, android-34/android.jar.)
- `bash tools/run_all_checks.sh` -> 46 passed, 0 failed (test_part1_voice_flow.py line 30 was already fixed).
- `python3 tools/ui_chat_check.py <outdir>` -> 19/19 PASS.
- Real Gradle/APK build still NOT possible in sandbox (hosts blocked). Owner must run GitHub Actions or `./gradlew testDebugUnitTest assembleDebug` and send the log if red.

## PART 9 STATUS: STARTED, NO SOURCE CHANGE MADE YET
Nothing in app/src was edited this session. Next steps:
1. Cap ~35 @JavascriptInterface methods in MainActivity.kt (list below). Plan: rename param x -> x0 and add `val x = x0.take(N)` at body start; for keys/url/json REJECT instead of truncating (`if (x0.length > N) return "invalid"`; setFeedUrl "bad"; saveSettings "bad"; openPublished plain return). Do NOT truncate setFeedUrl (empty string clears the feed URL).
   Caps: schedule/learned/api ids 40; recording id 300 (it is a content:// URI); update ids 100; keys raw 400 (saveKeys pool 4000, MAX_KEYS=10); url 300; json 20000; time 16; ts 20; facing 10; slot 16; text(autoreply) 500; expected 1000; teach alias 100 / value 400; routine name 100 / trigger 200 / steps 2000; speakMsg t 4000.
   Methods: saveKeys, setSchedulePrio, cancelSchedule, addSchedule(time), saveGroqKey, savePorcupineKey, saveOpenRouterKey, saveZaiKey, saveZaiKeyN, memoryEdit(expected), memoryDelete(expected), saveGithubToken, openPublished, keepLearned, deleteLearned, setAutoReply, deleteSent, videoStart, playRecording, shareRecording, deleteRecording, setApiLimit, teachAdd, teachDelete, saveRoutine, deleteRoutine, speakMsg, saveImgKey, hasImgKey, clearImgKey, setFeedUrl, installUpdate, skipUpdate, rollbackUpdate, saveSettings.
   Method shapes differ (block body, `= try {`, one-line `= expr`, `=` + next line expr, inline `{ ... }`), so edit carefully or script it; a first script attempt failed on `= if (...) "ok" else "notfound"` one-liners (regex needed to allow quotes/else).
2. Add tools/test_bridge_caps_contract.py: every @JavascriptInterface String param must have a .take( / .length > cap in its method. Add to run_all_checks.sh.
3. Re-run compile check (639+ tests) and run_all_checks.sh after the edit.
4. Remaining Part 9 items (see docs/AUDIT_v6.81.md): verify config status panel headless; logging audit (grep every ErrLog.add, mask phone/OTP digit runs, add redaction JUnit); secret scan of the zip (AIza, gsk_, sk-, ghp_, github_pat_); permission list report only; SecureStore / UpdateManager / GitHub token audit with severity. Write docs/AUDIT_PART9.md.
## PART 10 (after Part 9)
Regression table with real numbers (PASS/FAIL/NOT TESTED), final ZIP + CHANGELOG + README + build instructions, docs/FINAL_TEST_REPORT.md, owner real-device checklist (incl. Part 8: chat export, old chat migration, pin/rename/search, WebView recovery), remaining limitations. Call a feature complete only if implemented AND verified; nothing here is device-verified yet.
