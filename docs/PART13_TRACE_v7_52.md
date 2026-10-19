# NOVA Part 13 (REAL ANALYSIS) : 4 HISSE aur "kuch nahi bhoola" TRACE TABLE (v7.52)

Owner order 18 Oct 2026: "Part 13 ko 4 parts me karo, part 1 complete karo, zip full details ke sath, bina kuch bhoole".
Neeche Part 13 ki HAR cheez (owner prompt + handover ke har vaaky se) ek hisse se bandhi hai. Koi row khaali nahi. Status honest: DONE = code likha + jo test chala wo likha.

| # | Part 13 ki maang (original) | Hissa | Status v7.52 |
|---|---|---|---|
| 1 | AnalysisRules.kt pure Kotlin (JUnit chale) | 13A | DONE (likha; JUnit 45 test likhe, CHALE NAHI) |
| 2 | Prompt banane wale, Hinglish + English | 13A | DONE (visionSystemPrompt / visionUserPrompt / composeSystemPrompt / composeUserPrompt) |
| 3 | Model ka jawab sirf STRICT JSON allow-list (MiniJson tareeka) | 13A | DONE (parseVision, parseCompose) |
| 4 | Source URL sirf asli search results se; model ka banaya URL reject | 13A rule + 13C wiring | RULE DONE (validUrl, sanitizeResults, model sirf NUMBER deta hai, URL text me se hata diya jata hai); WIRING 13C DONE v7.58 (code; compile / phone NOT RUN) |
| 5 | Har claim ya source se bandha ya "verify nahi hua" | 13A rule + 13C wiring | RULE DONE (buildReport: bandha / unverified, koi teesra raasta nahi); WIRING 13C DONE v7.58 (code; compile / phone NOT RUN) |
| 6 | Insaan ko shakal se pehchaan kar naam nahi | 13A rule; 13B / 13D wiring | RULE DONE (gateQuestion, prompt, identityClaim filter); WIRING 13B (vision) + 13D (voice / chat) |
| 7 | Nuksaan wali jankari par saaf mana | 13A rule; wiring 13B / 13D | RULE DONE (gateQuestion HARM, model safety flag) |
| 8 | Protected / private screen ki file par saaf mana | 13A rule; wiring 13B | RULE DONE (gateSource, safety=private); WIRING 13B (Control.blocked wala pkg jahan pata ho) |
| 9 | OTP / PIN / password / card number kabhi nahi (owner rule 6) | 13A rule; wiring 13B / 13D | RULE DONE (gateQuestion SECRET + safety=secret) |
| 10 | Report: summary, kya dikha, likha hua text, jaanch ke dawe (source ke saath), kya verify NAHI hua, confidence (low / medium / high + wajah), limits | 13A model + text; 13C wiring | MODEL DONE (Report, confidence, limits, toJson, reportText, speechSummary) |
| 11 | Offline / no key: "images offline analyse nahi ho sakti" + sirf metadata (size, duration) | 13A text; 13B wiring | TEXT DONE (metadataReport); WIRING 13B |
| 12 | Stages INTAKE -> FRAMES -> VISION -> SEARCH -> COMPOSE -> DONE / FAILED, har stage ka asli event (Part 14 inhi par) | 13B (INTAKE FRAMES VISION DONE FAILED) + 13C (SEARCH COMPOSE) | stageMessage DONE (13A); pipeline 13B / 13C |
| 13 | VISION: frames Gemini inlineData pattern se (naya client nahi), batches, cap | 13B | batchSizes DONE (13A: <= 6 frame / call, <= 2 call); call 13B |
| 14 | VISION: quota ApiUsage se, chain fallback, free provider agar vision kare | 13B | 13B |
| 15 | SEARCH: Gemini Google Search grounding ya key-less web (WebContext / WebBrowsers), har sawal ki alag query, sources ke title + url + tareekh | 13C | 13C code me DONE v7.58 (runSearch: Gemini grounding + key-less, <= 4 calls); compile / phone NOT RUN |
| 16 | COMPOSE: model se report, claims numbers se bandhe | 13C | 13C code me DONE v7.58 (runCompose + 1 retry, numbers only); compile / phone NOT RUN |
| 17 | Bridge analysisStart(id\|chatPhoto, question), analysisStatus(), analysisCancel(), analysisResult(id) | 13B (start / status / cancel / vision result) + 13C (final result) | 13B / 13C |
| 18 | Media card ka ANALYSE button asli (pt12_frames ke saath, row .m11_act; chat-bar ka ek hi + button, naya button nahi) | 13B | 13B |
| 19 | MADAD + SKILLS "Analysis" topic (HelpGuide.kt Topic + ui.html CAPS barabar, SK entry, SCR) | 13B (pehli baar jab button aata hai), 13C / 13D me text badhao | 13B |
| 20 | Voice / chat: "nova ye video analyse karo", "is photo me kya hai", "isko check karo ki sach hai ya nahi" -> pipeline (MediaIntake.Voice.ANALYSE_NOT_YET + NovaService "media" block badlo) | 13D | 13D |
| 21 | KNOWN BUG: chat me photo jodkar sawaal poochho to vision jawab nahi aata -> isi part me theek, chat photo bhi isi pipeline se | 13D (13B ka vision call use karke) | 13D |
| 22 | Long job = agent jaisa background, STOP se ruke (watchdog Part 18) | 13B (pipeline apna 120 s watchdog + cancel + SafeStop) / 13D (voice) | 13B / 13D |
| 23 | Persona analysis jawab par nahi (owner rule 5) jab tak PersonaRules allow list me na dalo | 13D | 13D (default: nahi) |
| 24 | Silent / quiet mode ka dhyan (voice) | 13D | 13D |
| 25 | Tests: AnalysisRulesTest.kt (JSON allow-list, source validation, invented-URL reject, no-face-name) | 13A | DONE (45 test likhe) |
| 26 | Tests: test_analysis_contract.py (stages, model se direct URL nahi, quota check) | 13A (rules ka static contract) + 13B / 13C / 13D (pipeline / wiring contract) | 13A DONE: tools/test_analysis_rules_contract.py |
| 27 | Tests: headless render (FAKE bridge) | 13B (button + progress), 13D (end to end) | 13B / 13D |
| 28 | Done = asli photo / video par report, har dawa sourced ya "unverified"; offline / no key par saaf, jhoota nahi; chat photo + sawaal jawab de | 13D (acceptance) | 13D |
| 29 | Chat ka size owner ne approve kiya: mat badlo | har hissa | rule |
| 30 | Beech beech percentage batao | har hissa | rule |
| 31 | Owner ke 6 delivery niyam (zip + link, status, prompt zip ke andar, source zip, CHANGELOG, plan STATUS) | har hissa | 13A DONE |
| 32 | Add-only, UI se juda, sirf asli kaam, auto-reply AI-only (persona kabhi nahi), safety, guards, pinned cheezen (build.yml, versionName 2 jagah, ui.html end, ui ids >= baseline) | har hissa | 13A: build.yml / ui.html / manifest / bridge untouched; versionName 7.52 dono jagah |

## Hisson ka kaam ek nazar me
- 13A (DONE, v7.52): saare RULES pure Kotlin me + 45 JUnit test + contract. App me naya button nahi.
- 13B (agla): AnalysisPipeline INTAKE / FRAMES / VISION, bridge, ANALYSE button, progress, MADAD / SKILLS, offline honest report. Pehli baar app me dikhega.
- 13C: SEARCH + COMPOSE, final report (sourced / unverified), analysisResult.
- 13D: voice / chat commands, chat photo + sawaal bug, persona / silent, acceptance, owner test list.

## UPDATE v7.53: 13B ko owner ne 2 hisso me toda (13B-1, 13B-2)
Rows ka naya status (purani table ko chheda nahi):
- 12 stages INTAKE / FRAMES / VISION / DONE / FAILED: 13B-1 DONE (compile PASS, phone NOT TESTED); SEARCH / COMPOSE 13C.
- 13 VISION inlineData batches: 13B-1 DONE. 14 quota / chain fallback: ApiUsage + Gemini cooldown 13B-1 DONE; free-provider vision chain 13B-2.
- 17 bridge: analysisStart / Status / Cancel / Result / ResultText 13B-1 DONE (final sourced result 13C).
- 18 ANALYSE button: 13B-1 DONE (pt13_analyse). 19 MADAD + SKILLS + SCR: 13B-1 DONE (text 13C / 13D me badhega).
- 22 background + STOP: 13B-1 DONE (120 s watchdog, cancel, STOP hooks); stuck-call fix 13B-2.
- 27 headless render: 13B-1 DONE (test_p13_ui_runtime.py, fake bridge); end to end 13D.
- 6 / 7 / 8 / 9 wiring (face / harm / protected / secret): question gate + model safety flag wired in 13B-1; voice / chat 13D.
- 11 offline / no key metadata report: 13B-1 DONE.

## UPDATE v7.57 (13B-2d): 13B poora (a, b, c, d). Honest: sab CODE me, phone / compile / JUnit NOT RUN (owner order: Part 13 ke end me)
- 12 stages INTAKE / FRAMES / VISION / DONE / FAILED: DONE in code. SEARCH / COMPOSE 13C.
- 13 / 14 vision + quota / chain fallback: DONE in code (13B-1 Gemini; 13B-2a FreeVision.kt Groq qwen vision + OpenRouter free router, max 3 frames per call, shared cooldown / ApiUsage).
- 17 bridge: DONE (final sourced result 13C). 18 ANALYSE button: DONE. 19 MADAD + SKILLS + SCR: DONE (text 13C / 13D me badhega; privacy line now names Groq / OpenRouter).
- 22 background + STOP: DONE (120 s watchdog, cancel, STOP hooks; 13B-2b replaceWorker: stuck call no longer blocks a new analysis).
- 27 headless render: test_p13_ui_runtime.py written (13B-1), report sections added 13B-2c; NOT RUN since; end to end 13D.
- UI polish (sections, scroll, 320 px): DONE in code (13B-2c). FeatureStatus row "analyse" + FeatureCheck readiness line: DONE in code (13B-2d).


## v7.61 UPDATE (Part 13 complete, code)
Rows 15-17 (search, compose, bridge), 20-24 (voice, chat photo, background + STOP, no persona, quiet), 27 (headless render, mutation-proof) : DONE in code. Kotlin compile PASS + Analysis/FreeVision JUnit PASS in sandbox. Row 28 (real photo / video report, sources) : NOT TESTED on a phone - owner phone test list in PART13D_STATUS_v7_60.md (a-g).
