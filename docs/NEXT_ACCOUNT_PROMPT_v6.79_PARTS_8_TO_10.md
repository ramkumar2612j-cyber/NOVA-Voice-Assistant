# NEXT ACCOUNT PROMPT - NOVA v6.79 (Parts 8-10 left)
Paste this whole file in the new chat with the latest NOVA-Voice-Assistant.zip.
OWNER RULE (v6.77/6.78): NOVA does every job directly (WhatsApp, Instagram, any installed app, calls), no yes/no, no permission/request chatter, keeps going until the job is finished. Only pay / buy / password / OTP / bank screens stay refused. Do NOT add confirmations back.
STATUS: Part 1-4 done. Part 5 (v6.75), Part 6 (v6.76), Part 7 (v6.79) DONE in source. NOT compiled, JUnit NOT RUN (sandbox had no kotlinc / internet); 45/45 Python contract scripts PASS.
FIRST: run GitHub Actions on this ZIP (./gradlew assembleDebug testDebugUnitTest) and send the log if red; then read docs/CONTINUATION_PART7.md and docs/TEST_REPORT_PART7.md. Parts 8-10 not started.
Honest rule: every item RUN / NOT RUN / PASS / FAIL / NOT TESTED. Hinglish short replies. Zip name exactly NOVA-Voice-Assistant.zip. Work in parts so the chat limit is not hit; give zip + next-account prompt at the end of each part.
Part 7 carry-overs: Memory screen NOT screenshot-tested; English question vs Hinglish fact does not match; safeSummary does not hide a spoken "password hunter2".

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
