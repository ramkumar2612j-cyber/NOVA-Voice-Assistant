# NOVA next account prompt (v6.97 wip9) - paste as first message, attach NOVA-Voice-Assistant.zip

Owner: not a programmer, Android phone only (Chrome + GitHub website). Reply in Hinglish, short, ONE step at a time.
Compile only on GitHub Actions: github.com/skfashion142/NOVA-Voice-Assistant (main). Zip name must be exactly NOVA-Voice-Assistant.zip (top folder NOVA-Voice-Assistant-main). UI = app/src/main/assets/ui.html, Kotlin package com.nova.assistant.
Always say clearly RUN vs NOT RUN. Everything real, nothing fake. Owner does no manual tests: tests run automatically after install.

## STATE
- Base = v6.97 wip8 + Parts 1-10 audit work. Last known real compile + 834 JUnit was at wip8 on an earlier account; everything since then (wip9 below, and Part 7/8/9/9D/10B Kotlin) is NOT compiled and NOT tested on a phone. No kotlinc/internet in the last sandbox.
- run_all_checks.sh (python contracts) = 69 passed, 0 failed (RUN, this account).

## OWNER REQUEST (16 Oct 2026, this account)
1. Check that the features are in the app UI SKILLS screen.
2. Normal talk (typed or spoken) like with Claude in all processes.
3. When a command popup appears NOVA may see/control the screen automatically, NO button needed, also while tasks run. But when the owner says e.g. "screen analyse karo aur batao ye kaun si movie hai", NOVA must look live and search to answer.

## AUDIT FINDINGS (static, code read)
- SKILLS screen (ui.html renderSkills, static list G) did NOT list: 5 browsers, offline AI pack, Ringer/DND, Lock Guard, AI SMART, screenshot/rotation, business reply. wip9 added only 5 rows (see below); AI SMART, screenshot/rotation, business smart reply, notification reading, agent jobs, WhatsApp business rows are STILL missing from SKILLS.
- Conversation mode existed (ExtCfg.convo, followWindow) but was default OFF since v6.30 and max 3 follow-ups.
- Screen analysis existed (analyzeScreen -> ScreenSession, Gemini with screenshot) but: screenshot only when text is thin or "picture" intent; NO web search; the spoken question in "analyze + question" was dropped (generic summary only); "ye kaun si movie hai" without the word screen went to plain chat which cannot see the screen.
- Popup (NovaHud) has no screen-control button, only HAAN/NAHI for confirmations. Screen reading depends on the Accessibility service being connected (not a popup button).

## DONE in wip9 (NOT compiled, NOT run on phone)
- ScreenSession.kt: Intent.IDENTIFY, wantsIdentify(text) (needs a pointing word like ye/is/this + a thing word movie/film/gaana/actor/product... + an ask word kaun/naam/who/batao), askFor(IDENTIFY), intent() checks it before PICTURE.
- Logic.classify: wantsIdentify -> Cmd("analyze", sentence), placed just before the generic screen rule.
- NovaService: dispatch analyzeScreen(c.arg); screenStartFrom(sn, spoken) answers the identify question at once; screenTalk forces the screenshot for IDENTIFY and adds the google_search tool to that one call (sys prompt allows search for that question only); screenFollow keeps IDENTIFY (not turned into REFRESH); screenAnswer re-adopts the current screen for IDENTIFY. Protected-screen / FLAG_SECURE gate unchanged (bank/payment/password screens still refused).
- ExtCfg.convo default true; NovaService.MAX_FOLLOW 3 -> 6.
- ui.html SKILLS: group "NAYE KAAM (v6.97 wip9)" with 5 rows. CHANGELOG top entry added.
- Tests: ScreenSessionTest.identifyQuestionsWantScreenAndSearch (NOT RUN). tools/test_screen_alert_contract.py and test_screen_session_contract.py now look for "private fun analyzeScreen(".

## KNOWN RISKS / TO CHECK FIRST
- Compile errors likely places: NovaService.kt (screenTalk body/sys vars, analyzeScreen(spoken), screenStartFrom ident), ScreenSession.kt (new sets/askFor string escapes), Logic.kt (ScreenSession reference), plus older uncompiled Part 7-10 files (SmartReplyRules, SiteQuality, SiteGen, FeatureStatus, MainActivity openDndAccess/dndGranted).
- Gemini google_search together with an image + systemInstruction should work on 2.x models; postFallback tries other Gemini models/keys; Groq/OpenRouter fallbacks do NOT see images or search, so "ye kaun si movie hai" needs a Gemini key. Say this honestly. If search fails, the answer must say it is not sure, never invent a title.
- Convo ON by default may make NOVA listen without the wake word after replies (reason it was turned off in v6.30). If owner complains, shorten the follow window; switch is Settings > BAAT-CHEET.
- Live movie/video on screen: screenshot is a single frame (not video). Screens with FLAG_SECURE (Netflix, Prime, banking) return a black/blocked shot and NOVA refuses; say so.
- Possible misfire: wantsIdentify catches "ye kaun si movie hai" but not sentences without a pointing word.

## wip9b: source-level work for Parts 8/9/10 is finished (see PART8/9/9D_STATUS.md); what is left REQUIRES a compiler or a real phone, so it cannot be finished in a sandbox. Bracket balance of edited Kotlin files matches the original (static only).

## TODO (in order)
1. Push zip to GitHub, run "Build and Test APK", read the log; fix compile/JUnit errors first (start with the files above).
2. DONE (wip9b): SelfTest phrase "ye kaun si movie hai" -> analyze added (auto test checks it on the phone).
3. DONE (wip9b): SKILLS rows added for AI SMART, screenshot/rotation/vibrate, notification reading + reply. Still missing: business smart reply, agent jobs rows (optional).
4. Remaining old Parts: 8 chat history polish, 9 security/config, 10 final regression + release zip (see earlier prompts NEXT_ACCOUNT_PROMPT_PART*.md).
5. Real phone checks (NOT TESTED): popup + accessibility connected, identify answer, convo follow-ups, DND/ringer, screenshot, rotation.
6. Version bump to final 6.97/6.98 only after green build (pins in tools/test_android_runtime_contract.py, workflow summary, README, docs).

## STANDING RULES FROM OWNER
NOVA does jobs directly without yes/no; only pay/buy/password/OTP/bank screens stay refused. Honest PASS/FAIL/NOT RUN. Android limits (no direct WiFi/Bluetooth/mobile data toggle, no restart/factory reset) stay stated honestly.
