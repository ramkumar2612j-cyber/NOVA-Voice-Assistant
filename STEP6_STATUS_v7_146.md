# STEP 6 OF 10 (v7.146): atakna / hang watchdog
Owner order 30 Oct. Add-only. CHANGELOG.md not touched (test_part20_contract rule).

## What it does
- MainStallWatchdog.kt: daemon thread "nova-stall-watch" posts a tiny task to the main thread every 1 s. If main did not run it for 3.5 s it records ONE short line (time, ms, first NOVA code frame; class and method names only) in a ring of 10 (SharedPreferences nova_stall, apply() on the watchdog thread) and one ErrLog line "hang" (so Settings error-log COPY shows it). Skips when the whole phone was frozen (doze). Never kills, interrupts or changes anything; every step is wrapped in try/catch. Started once from NovaApp.onCreate.
- StallRules.kt: pure rules (threshold, frozen check, top frame, ring, encode/decode, honest spoken summary). StallRulesTest (7 tests).
- Voice: "nova atakne ka hisaab batao" (FeatureVoice key mainstall, read-only). Empty list says plainly that nothing recorded does not prove it cannot freeze.
- UI: MADAD topic mainstall (65 topics, HelpGuide.kt = ui.html) and a Skills-screen entry.

## Audit result (limited, honest)
- Already fixed earlier: initTts on a worker thread (v7.138), voiceKey cache (v7.139).
- Found by grep: SecureStore Keystore is only used in SecureStore.kt; many SharedPreferences .commit() calls (ChatSessions, ConnectorStore, CrashGuard, AgentTaskLedger, GmailStore...) are synchronous disk writes. Whether any of them runs on the main thread was NOT proven. The watchdog is the evidence tool: after the owner uses the app, "nova atakne ka hisaab batao" or the error log shows the real culprit frames. Do NOT change those .commit() calls blindly; fix the ones the watchdog names.

## Evidence
- tools/test_step6_stall_contract.py PASS; help (65 equal), skills, feature voice, render smoke PASS; all 160 contract scripts PASS (test_part5_ui_screens failed once under parallel load, PASS alone; test_part4_fx_render needs playwright, not run here).
- NOT DONE: no kotlinc in this sandbox this time, so StallRules/StallRulesTest/MainStallWatchdog were NOT compiled (only read through carefully). Android compile = GitHub build (NOT CONFIRMED). Phone NOT TESTED.
