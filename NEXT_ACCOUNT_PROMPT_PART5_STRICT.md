# NOVA - PART 5 PROMPT (STRICT). Paste FIRST in the new account + attach NOVA-Voice-Assistant-v7_33-PART4.zip. Also read NEXT_ACCOUNT_PROMPT_FULL_v7_33_PART4.md inside the zip (all sections) and FULL_v7_24 sections 1-3.

## A. OWNER RULES (compulsory, nothing is optional)
Owner is NOT a programmer, Android phone only. Reply in HINGLISH, SHORT (phone screen ~7 lines), ONE step at a time. Report PASS / FAIL / NOT TESTED with evidence; never claim a compile, JUnit, APK or phone test that was not really run. Do NOT push anything to GitHub until the owner says so. After the part: (a) full ZIP, (b) refreshed FULL prompt, (c) short Hinglish status.

## B. STRICT: KUCH CHANGE NAHI KARNA (owner's exact order)
1. Existing code, existing features, existing behaviour, existing texts, existing ids, existing tests = DO NOT CHANGE. No rename, no cleanup, no refactor, no "small fix", no reformat, no deleting, no moving.
2. Only ADD new things (new file, new appended block, new function, new test). Removed lines vs the v7.33 zip must be 0 (check with diff; for every old file `diff old new | grep -c '^<'` = 0).
3. Never touch .github/workflows/build.yml (must stay byte-identical).
4. If you think an old line is wrong or slow (example: the hidden #orb3d WebGL loop still runs): DO NOT edit it. Write it in the report as "suggestion, needs owner yes" and stop. Same for the 11 known stale guards: leave them, do not "fix" them.
5. If a new addition would need an old line to change, do not do it; report it instead.

## C. YOUR JOB = PART 5, FULL AND COMPLETE (everything real, nothing half)
Goal: the whole "AUTO + FUTURE UI" series (Parts 1-4) must be FULLY WIRED end to end, not only present in code. Check each link of every chain below. If a link is missing, ADD it (add-only). If it exists, leave it.
Chains to verify and complete:
1. Auto detect: typed text -> ui.html p1_auto chip -> Android.detectIntent (MainActivity) -> AutoIntent.kt -> NovaService.process autoReq -> siteBegin / imageBegin -> result window.onSite / window.onImage -> chat. Also voice path (VoiceText.normalize -> same process). Photo edit path: p2_auto -> Android.editPhoto -> window.onPhotoEdited.
2. Chat effects (p3_fx): reveal, image reveal, photo zoom, website progress card, lowfx / reduced motion off-switch.
3. Home (p4_fx): #p4_core states follow #app class, tap = ACTIVATE, #p4_stars touch.
4. Wiring audit: every Android.* function called in ui.html (including the new scripts) exists as @JavascriptInterface in MainActivity, and every new JS function called from Kotlin (evaluateJavascript) exists in ui.html. Run tools/test_wiring_audit_contract.py and tools/test_part10_featuremap_contract.py (counts 212 bridge funs now; if you ADD a bridge fun, update ONLY the new guard you write, not the old one).
5. Help / feature visibility: every new feature must be visible in the app and known to NOVA. Existing HelpGuide topics "image" and "website" already exist; do NOT edit them. If a NEW topic is needed, add it in BOTH HelpGuide.kt and the ui.html MADAD list so tools/test_help_contract.py stays green (it keeps both lists equal), and add a new FeatureStatus row only by appending.

## D. LOGGING (owner order: "app log kare, wo hume dikhe aur NOVA ko bhi pata ho")
Facts found in the code (use them, do not change them):
- ActionLog.kt (object ActionLog): persistent history on the phone, newest 300, shown in the app panel "NOVA NE KYA KIYA (HISTORY)" via Android.getActionLog -> ui.html histRender. ActionLog.add(ctx, app, sender, incoming, status, detail, pkg). status values the panel knows: sent, failed, skipped, deleted, delete_failed (anything else shows the heading "KAAM"). Never store code / password / message bodies.
- ErrLog.kt (object ErrLog): in-memory last 30 FAILURES, already masked (tokens, e-mails, 4+ digit runs, quoted text become ***). ErrLog.dump() is included in HealthCheck.report() = the report the owner copies and pastes to Claude, and FeatureStatus reads ErrLog.latest().
- ui.html has NO bridge for JS errors yet (only a window 'error' toast at ~line 3285). The new scripts p1_auto, p2_auto, p3_fx, p4_fx catch their own errors silently.
What to ADD (new file + appended lines only):
1. NEW pure Kotlin object (suggested name AutoTrace.kt): ring buffer of the last ~30 events, each = time, kind (SITE / IMAGE / PHOTO_EDIT / WRITE / NONE), short subject (cleaned with ErrLog.clean), route taken, result (started / blocked / busy / failed). NEVER the user's full sentence. Methods add(...), summary(): String (few Hinglish lines), unit test AutoTraceTest.kt.
2. NovaService.process autoReq block: AFTER the existing lines (do not edit them) record AutoTrace.add and ActionLog.add(app="NOVA AUTO", status "sent"/"skipped"/"failed", detail "kind + subject"). The History panel then shows it to the owner. On any exception: ErrLog.add("AutoIntent", ...).
3. HealthCheck.report(): append (do not change existing lines) the section "AUTO TRACE" from AutoTrace.summary(), so the owner's copied report and NOVA's own health check know what the auto routing did. Also make NOVA able to answer "auto kya samjha" (ask: last auto decision) from AutoTrace.summary() with a new add-only local route in the same style as the existing FeatureVoice answers.
4. A NEW bridge (e.g. Android.uiLog(tag, msg): String) that takes a short UI line from the new scripts (p3 card shown / p4 core running / lowfx on) and stores it with ErrLog.clean in AutoTrace; call it from NEW wrapper code appended at the end of ui.html (typeof Android.uiLog check + try/catch), never from old code. Cap the rate (max 1 event per tag per 10 s) so it never spams. Add the new bridge to the NEW guard test only.
5. Test: tools/test_part5_contract.py checks every item above (files exist, calls present, no old line removed, build.yml identical, no localStorage of user text, privacy: no full sentence in logs).
Privacy rule: logs stay on the phone, nothing is uploaded, no keys, no full user sentences.

## E. FINAL STEPS
1. Run: bash tools/run_all_checks.sh (exit 0 required; only the 11 KNOWN stale may FAIL, same list as KNOWN_STALE_GUARDS.txt), tools/test_ui_render_smoke.py, tools/test_auto_chip_render.py, tools/test_part3_fx_render.py, tools/test_part4_fx_render.py, tools/test_part5_contract.py, node --check on every edited script, brace/paren balance of every new .kt file.
2. Sandbox has NO kotlinc and NO internet: Kotlin is static review only. State clearly NOT RUN: Kotlin compile, JUnit, APK, phone. List first compile suspects.
3. Deliver: final ZIP (NOVA-Voice-Assistant-v7_34-PART5.zip), refreshed FULL prompt, OWNER steps (phone only, tap by tap, exact github.com link: upload extracted zip to the real repo main, Actions > NOVA Android CI > Run workflow, send only the red lines if red, green = download nova-apk), and a phone test list for Parts 1-5 including: open History panel and see the "NOVA AUTO" lines, copy the health report and see "AUTO TRACE".
4. Repo (ask the owner once which is real): https://github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant (older notes: skfashion142/NOVA-Voice-Assistant, devboutique0-wq/NOVA).
