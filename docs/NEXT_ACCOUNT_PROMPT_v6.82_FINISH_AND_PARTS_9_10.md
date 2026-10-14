# NEXT ACCOUNT PROMPT - NOVA v6.81 -> continue (fix 1 test, re-compile, Part 9, Part 10)
Paste this whole file in the new chat with the latest NOVA-Voice-Assistant.zip.
OWNER RULE (v6.77/6.78): NOVA does every job directly (WhatsApp, Instagram, any installed app, calls), no yes/no, no permission/request chatter, keeps going until finished. Only pay / buy / password / OTP / bank screens stay refused. Do NOT add confirmations back. Every feature must be REAL: no demo/placeholder/fake success. Hinglish short replies. Zip name exactly NOVA-Voice-Assistant.zip.
STATUS: Parts 1-8 done in source. Part 8 verified locally (639 JUnit, headless UI 19/19). After that, small v6.81 edits were made WITHOUT re-compiling (see docs/AUDIT_v6.81.md).
STEP 0 (do first, in this order):
1. `bash /ABSOLUTE/PATH/tools/local_compile_check.sh /some/workdir` -> must say OK (639 tests). If the v6.81 edits (MainActivity onRenderProcessGone, NovaService registerSysReceiver) fail to compile, fix them.
2. Fix tools/test_part1_voice_flow.py line 30: regex -> `registerSysReceiver\(batteryLowRx` (count 1). Then `bash tools/run_all_checks.sh` -> expect 46/46 PASS.
3. `python3 tools/ui_chat_check.py /some/outdir` (Playwright Chromium preinstalled) -> 19/19 PASS.
4. Ask the owner to run GitHub Actions (or `./gradlew testDebugUnitTest assembleDebug`) and send the log if red. Real Gradle cannot run in the sandbox (Maven/Google blocked, 403 - do NOT try to bypass).
THEN: Part 9, then Part 10 (below). Give zip + next prompt at the end of each part.

## COMMON HEADER
```
NOVA-Voice-Assistant.zip (latest) ko base lo; docs/CONTINUATION_PART*.md, docs/AUDIT_v6.81.md aur docs/ROADMAP_PARTS_4_TO_10.md padho.
Rules: pehle audit, phir implement. Existing working features/UI/settings/integrations preserve karo; bina zaroorat delete/rename/rewrite nahi.
Speaker recognition, voice lock, voice enrollment add mat karna. Permission/security restrictions bypass mat karna.
Demo/dummy/placeholder/fake success nahi. Har feature PASS / FAIL / NOT TESTED; real device par test nahi hua to clearly likho.
Har part ke end me: (1) ZIP, (2) build/test report + modified files + remaining bugs, (3) agle part ka prompt, (4) continuation notes.
```

## PART 9 - Security, secrets, configuration, logging (see AUDIT_v6.81.md findings)
1. Cap every @JavascriptInterface String parameter (~30 listed in the audit; ids <= 40 via ChatSessionRules.validId where they are chat ids, keys <= 400, json <= 20000, url <= 300). Add a Python contract check that no bridge String param is uncapped.
2. Config status panel: already exists (SAARE FEATURES / FeatureStatus). Verify with a headless run that missing keys + permissions show and the button opens the right Settings screen.
3. Logging: no key / OTP / message body / typed text in ErrLog or any log (grep every ErrLog.add caller; redaction JUnit). ErrLog masks 30+ char tokens only: also mask phone numbers / OTP-like digit runs if bodies can reach it.
4. Secret scan of the zip (AIza, gsk_, sk-, ghp_, github_pat_) - last scan found none.
5. Permission list: report only, do not remove.
6. SecureStore (Keystore, key pools, migrations), UpdateManager install flow, GitHub publish token: audit + findings with severity.
## PART 10 - Final regression, release package, real-device checklist
1) Regression table (voice, wake, phone control, calls/SMS/WhatsApp, timers/alarms, driving mode, chat sessions, fallback, memory, agent, scheduled tasks, recording, image/site gen, updates): PASS / FAIL / NOT TESTED with real local JUnit numbers.
2) Final ZIP with CHANGELOG, README, build instructions (GitHub Actions + `./gradlew`).
3) docs/FINAL_TEST_REPORT.md.
4) Owner's real-device checklist (step, expected result, PASS/FAIL box) incl. Part 8: export chat file, migration of old chats, pin/rename/search, WebView recovery.
5) Remaining limitations, nothing hidden. Call a feature "complete" only if implemented AND verified.
