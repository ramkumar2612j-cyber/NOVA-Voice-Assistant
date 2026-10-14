# NOVA v6.90 — continue from here (paste this whole file as the first message, attach the ZIP)

Project: NOVA Android assistant (Kotlin, package com.nova.assistant, UI = app/src/main/assets/ui.html WebView). Keep ALL features, APIs, architecture, fallbacks.
Minimise tokens/tool calls. No fake results. Never claim a message/call/build is done without verification. Report PASS / FAIL / BLOCKED / NOT TESTED. Deliver full ZIP + CHANGELOG.md.
Owner talks Hinglish; reply in Hinglish, short.

## Sandbox facts
- Gradle/APK build = BLOCKED (Maven Central, Google Maven, services.gradle.org 403). Re-check once: curl -sI https://dl.google.com/dl/android/maven2/
- github.com works. Real compile + JUnit without Gradle:
  1. kotlinc 1.9.24: github.com/JetBrains/kotlin/releases/download/v1.9.24/kotlin-compiler-1.9.24.zip
  2. android.jar: git clone --depth 1 --filter=blob:none --sparse https://github.com/Reginer/aosp-android-jar.git; git sparse-checkout set android-34
  3. real org.json: git clone --depth 1 --branch 20240303 https://github.com/stleary/JSON-java.git; javac -d out src/main/java/org/json/*.java; jar cf orgjson.jar -C out .
  4. Put kotlinc/ + android.jar in work dir W, then: JSON_JAR=/abs/orgjson.jar bash /abs/tools/local_compile_check.sh W   (JUnit 4.13.2 comes from /opt/gradle*/lib)
  5. python contracts: bash tools/run_all_checks.sh
  6. UI screenshots (Chromium + Playwright installed): tools/ui_shots.py has a FAKE window.Android bridge; copy its FAKE and add getChatAi/setChatAi stubs.

## DONE in this ZIP (v6.90, see CHANGELOG top entry). Last real run: JUnit 704/704 OK, python 53/53 PASS, main compile OK.
Earlier (v6.89): contact/WhatsApp fixes A-F (Business identity, negation in payload, ActionDedupe, picker for SMS/WhatsApp, honest wording, duplicate-contact merge).
v6.90: ChatHonesty guard + prompt rules; website follow-up route (siteFollowUp in NovaService, SiteIntent.parseFollowUp, SiteGen.improvePrompt);
AiChoice (auto|gemini|free provider + auto-fallback) wired into rankedProviders/cloudStep, bridge getChatAi/setChatAi, chip "AI: AUTO" + dialog (ui.html, script id chat-ai-v690);
ProviderChain `accept` so a prose answer in the phone-action pass does not end the chain; finishTurn busy released in finally + 120 s watchdog in typed(); modern CSS block id modern-v690.

## UPDATE (end of this account)
- UI CHECK DONE with Chromium screenshots (home, chat, AI dialog, settings at 390x844): looks fine, 0 JS errors, AI chip saves with the fake bridge. Only the Playwright tab click needs the CHAT tab by text and the Update Center popup closed first.
- Draft tests were written but are NOT in this ZIP because they were never compiled: AiChoiceTest, ChatHonestyTest, SiteFollowUpTest (see item 2 for what they must cover). Write and run them first.

## STILL TO DO (in this order)
1. UI CHECK (not done): run a Playwright screenshot of home, chat (tab id may NOT be t_chat: read the nav ids in ui.html line ~2158), the AI dialog (#c_ai) and settings at 390x844 and 360x640.
   Fix any overlap/contrast problem in the modern-v690 style block. Confirm #c_ai chip, dialog radio list, fallback checkbox, SAVE work with the fake bridge and no JS errors.
2. NEW unit tests: AiChoiceTest (clean, order with/without fallback, geminiMode, chainAllowed), ChatHonestyTest (the real bad reply "Website ko ... 3D animations ... add kar raha hoon" is replaced; a normal answer is untouched; toolRan=true untouched),
   SiteIntentTest follow-up ("Issko or professional bnao or app ka api ka setup ke baare ma bhi btao" => followUp; "isko call karo" and "website publish karo" => null), ProviderChain accept test (prose answer skipped, next provider used, no cooldown on the first).
3. Hang audit (static, only confirmed problems): main-thread work in MainActivity bridge calls, several requestAnimationFrame loops in ui.html (loop/gloop/tick: pause when a tab covers the orb: window.__cover is set only for tabs), large chat list rendering (63+ messages; consider rendering only the last ~40 DOM nodes), ChatStore load on the UI thread.
4. Re-run JUnit + python contracts, update CHANGELOG, deliver ZIP + final report (changed files, real counts, Gradle = BLOCKED, remaining issues).

## Device-only, NOT TESTED
Real chat on the phone (honesty guard wording), AI picker with real keys and fallback OFF, website follow-up end to end, hang/freeze under load, WhatsApp send via Accessibility, CALL_PHONE, real contacts, popup picker UI for SMS/WhatsApp.
