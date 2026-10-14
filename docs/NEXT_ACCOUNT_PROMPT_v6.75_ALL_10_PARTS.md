# NEXT ACCOUNT PROMPT - NOVA v6.75 (Parts 6-10 left)
Paste this whole file in the new chat with the latest NOVA-Voice-Assistant.zip.
STATUS: Part 1-4 done. Part 5 DONE in source (v6.75: 4 classification bugs fixed, 181-phrase CommandCorpusTest, VoiceDiagnosisTest, docs) - NOT compiled, JUnit NOT RUN (sandbox had no kotlinc / internet); 44/44 Python contract scripts PASS.
(Part 5 closed with a hand compile-review, docs/TEST_REPORT_PART5.md section 6.)
FIRST: run GitHub Actions on this ZIP (./gradlew assembleDebug testDebugUnitTest) and send the log if red; then read docs/CONTINUATION_PART5.md and docs/TEST_REPORT_PART5.md. Parts 6-10 not started.
Honest rule: every item RUN / NOT RUN / PASS / FAIL / NOT TESTED. Hinglish short replies.

# NOVA - roadmap Part 4 to Part 10 (ready-to-copy prompts)
Base of every part: the LATEST NOVA-Voice-Assistant.zip (now v6.75). Read docs/CONTINUATION_PART1/2/3.md first.
Status today: Parts 1-3 done (chat sessions, fallback context, full chat history, agent restore). Nothing from Parts 1-3 was compiled or run on a phone.

## COMMON HEADER (paste at the top of every part)
```
NOVA-Voice-Assistant.zip (latest) ko base lo; docs/CONTINUATION_PART*.md aur docs/ROADMAP_PARTS_4_TO_10.md padho.
Rules: pehle audit, phir implement. Existing working features/UI/settings/integrations preserve karo; bina zaroorat delete/rename/rewrite nahi.
Speaker recognition, voice lock, voice enrollment add mat karna. Permission/security restrictions bypass mat karna.
Demo/dummy/placeholder/fake success nahi. Har feature PASS / FAIL / NOT TESTED; real device par test nahi hua to clearly likho.
Har part ke end me: (1) updated ZIP exact naam "NOVA-Voice-Assistant.zip", verify karke link, (2) build/test report + modified files + remaining bugs,
(3) agle part ka ready-to-copy prompt, (4) continuation notes (done, pending, decisions, next steps).
```

---
## PART 4 - Build gate: compile, JUnit, fix
Goal: Parts 1-3 (and v6.68) ko pehli baar compile karke saabit karo.
```
[COMMON HEADER]
PART 4 - Build gate.
1) Check karo kaun sa build tool available hai (gradlew, kotlinc, internet). Agar Gradle chal sakta hai: ./gradlew assembleDebug testDebugUnitTest chalao. Agar nahi chal sakta: clearly likho, phir .github/workflows ka build workflow audit karo (JDK, Android SDK, Gradle version, NOVA_VERSION_CODE) aur ek step-by-step "GitHub Actions se build" guide likho.
2) Compile errors theek karo (sirf zaroori). Files jinhe pehle dekho: ChatSessions.kt, ChatSessionRules.kt, FallbackContext.kt, ConfigReport.kt, AgentTaskRules/Ledger/AgentRules (v6.72), NovaService.kt (emit, agentSettle, chatWindow, configState), FreeProviders.kt, LocalChat.kt, LocalBrains.kt, MainActivity.kt bridge.
3) Saare JUnit tests chalao (naye: ChatSessionRulesTest, FallbackContextTest, ConfigReportTest, FreeProvidersTurnsTest, AgentProgressTest). Fail hone wale ko test ya code me theek karo; test ko sirf tab badlo jab behaviour intentional badla ho aur wajah likho.
4) Python contract tests (tools/test_*.py) bhi chalao.
Done jab: build PASS ya clearly "NOT RUN: reason"; test table; compile fixes ki list.
```

## PART 5 - DONE in v6.75 (kept for reference) - Real voice control audit (wake word, STT, TTS, permissions)
```
[COMMON HEADER]
PART 5 - Voice + Android control.
Audit: wake word (Vosk/Porcupine), speech recognition (Vosk offline, Groq/Gemini audio), TTS (Hindi/English voice), mic permission, foreground service + background restrictions (battery optimisation, Android 12+ FGS limits), Accessibility setup flow, notification access, overlay.
Karo: (1) har step ka asli failure message (permission nahi, service band, TTS voice missing) - FeatureStatus + ConfigReport se milao. (2) Hindi/Hinglish/English command corpus banao (kam se kam 150 vakya: apps kholna, torch, volume, alarm/timer, call/sms, navigate, settings, media) aur Logic.classify ke JUnit tests likho; jo galat classify ho unhe theek karo bina purane commands tode. (3) Unsupported actions (Android jo allow nahi karta: ex. locked screen, protected/banking screens, secure settings) par saaf bolna, bypass nahi. (4) Wake-word false-trigger/miss handling aur battery notes.
Test table: har command-kind PASS/FAIL/NOT TESTED (device).
```

## PART 6 - Agent loop: Understand -> Plan -> Execute -> Verify -> Recover -> Report
```
[COMMON HEADER]
PART 6 - Agent loop hardening.
Audit: runTool outputs (open_app, call, sms, whatsapp, alarm, timer, torch, volume, brightness, navigate, settings, media, calendar_event, search_web) - kaun sa result asli verify hota hai aur kaun sirf "ok" bolta hai.
Karo: (1) har tool ke liye ek verify step (jahan Android allow kare: foreground package, AudioManager volume, alarm intent resolved, torch state, etc.); verify na ho sake to reply me "verify nahi hua" likho, fake success nahi. (2) Recovery: har failure kind ke liye ek bounded retry/alternate raasta (e.g. app naam nahi mila -> label search; permission nahi -> setup guide). (3) Sensitive actions (call, message send, payment, delete, order, post) ke liye confirmation rules ka audit + tests; koi sensitive action bina confirmation nahi. (4) Report: har kaam ke end me ek hi saaf sentence: kya hua, verify hua ya nahi, kya baki. (5) AgentRules.check (DENY/CONFIRM) ke JUnit cases badhao (banking/payment/install screens).
Existing verifier aur v6.68 speed/stall logic mat todo.
```

## PART 7 - Memory manager (long-term + current task)
```
[COMMON HEADER]
PART 7 - Memory.
Audit: PersonalMemory (YAAD facts), KnowledgeMatcher/LearnStore (notes), ChatSessions (chat), AgentTaskLedger (task), ContextMemory (RAM follow-ups).
Karo: (1) Memory screen me facts dekhna/edit/delete/search, export/import (local file), limit aur privacy line. (2) Relevance retrieval ka test corpus: New Chat me purana transcript mix nahi hona chahiye, par relevant saved facts jaane chahiye (PersonalMemory.relevant ke JUnit tests, Hindi/Hinglish). (3) Current-task memory: running/last task ka safe summary Gemini aur backup AI dono ko mile (FallbackContext.taskLine ke saath consistent). (4) Memory ko kabhi keys/passwords/OTP store na karna (redaction tests). (5) "yaad rakh" / "bhool ja" / "kya yaad hai" commands ke end-to-end tests.
Sensitive facts ka consent/limit rakho.
```

## PART 8 - Chat & history polish
```
[COMMON HEADER]
PART 8 - Chat history UX + storage.
Audit: ChatSessions (SharedPreferences, 30 chats x 500 msgs), chat UI (NAYI CHAT, HISTORY), resume, delete.
Karo: (1) history me search, rename, pin, "sab delete" (confirmation ke saath), export ek chat text file me. (2) Storage scale: SharedPreferences bahut badi chat par dheema ho to file/Room par migrate (purana data safe migrate, rollback). (3) Loading/empty/error states, long chat par smooth scroll, "context kitna gaya" indicator (kitne messages AI ko gaye, kitne cut). (4) Resume correctness tests: restart ke baad chat+context wapas, busy me switch block, deleted current chat ka behaviour. (5) Chat screen ka bottom tab overlap (screenshot me dikha) theek karo.
UI ke screenshots (headless browser) PASS/FAIL ke saath dikhao.
```

## PART 9 - Security, secrets, configuration, logging
```
[COMMON HEADER]
PART 9 - Secure configuration.
Audit: SecureStore (Keystore, key pools), SecureStore migrations, AndroidManifest (allowBackup=false, exported components, permissions), WebView hardening (hardenWebView, JS bridge surface), network security config, ErrLog redaction, UpdateManager (install flow), GitHub publish token.
Karo: (1) Config templates: local.properties.example / keys ke liye sirf placeholders (koi asli secret nahi) + README section "keys kahan daalni hain". (2) Settings me ek "Configuration status" panel (ConfigReport + FeatureStatus) jo missing keys/permissions saaf dikhaye aur seedha sahi Settings screen khole. (3) Logging: koi key/OTP/message body/typed text log me na jaye (redaction tests, grep audit). (4) JS bridge: har @JavascriptInterface method ka input validation + length cap audit (chat bridge methods v6.69 dhyan se). (5) Permissions minimum principle: jo permission use nahi ho rahi use hatane ki list do (hatao nahi, pehle batao).
Finding ki list severity ke saath.
```

## PART 10 - Final regression, release package, real-device checklist
```
[COMMON HEADER]
PART 10 - Final delivery.
1) Pura regression: har feature (voice, wake, phone control, calls/SMS/WhatsApp confirmations, timers/alarms, driving mode, chat sessions, fallback, memory, agent, scheduled tasks, recording, image/site gen, updates) ka PASS / FAIL / NOT TESTED table; jahan Gradle/JUnit chale unka asli result.
2) Final ZIP: complete source, resources, config templates, build instructions (GitHub Actions + local Gradle), CHANGELOG, README.
3) Consolidated test report (ek file: docs/FINAL_TEST_REPORT.md).
4) Real-device verification checklist: owner ke liye step-by-step manual test script (har feature ka kaise test karna, expected result, PASS/FAIL box), kyunki sandbox me phone nahi hai.
5) Remaining limitations ek saaf list me (Android/provider ki limits chhupana nahi).
Sirf actually implemented aur verified features ko "complete" bolo.
```

---
## Order and why
4 build pehle (baaki sab uske upar), 5-6 core behaviour, 7-8 memory/chat, 9 security, 10 final proof. Agar Part 4 me build chal jaye, baad ke parts me har part ke end me build+JUnit dobara chalao.
