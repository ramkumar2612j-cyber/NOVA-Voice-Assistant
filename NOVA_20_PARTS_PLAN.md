# NOVA v7.36+ : 20 PARTS PLAN (owner order 17 Oct 2026)
Rules for every part: add-only (old lines are never removed), Hinglish, honest PASS / FAIL / NOT TESTED, one zip + one prompt per part. The owner opens the app after parts 5, 10, 15 and 20 only.

| # | Part | What the owner gets |
|---|------|---------------------|
| 1 | AUTOPSY + tools | Every Kotlin file / button / bridge / id / manifest entry checked; tools added; plan + report |
| 2 | PERFORMANCE GOVERNOR | App measures the phone by itself (FPS + stalls) and picks quality 1-4, no hang |
| 3 | NATIVE SMOOTHNESS | WebView + lifecycle + memory pressure handling; effects pause in background |
| 4 | SAFE STOP | STOP only stops talking / the job and drops half-heard speech; NOVA stays ON |
| 5 | FUTURISTIC DESIGN SYSTEM | One neon-glass look for the whole app, boot sequence. CHECKPOINT 1 (owner tests) |
| 6 | HOME REDESIGN | Core orb states, fixed status board, dock |
| 7 | MOTION LANGUAGE | Screen transitions, ripples, haptics, number tickers |
| 8 | CHAT UI | Futuristic bubbles, voice waveform, typing reveal |
| 9 | JARVIS PERSONA | Persona engine: humour, feelings, calls the owner by name, short witty lines |
| 10 | ROUNDABOUT COMMANDS | Understands hints / "ghuma ke" requests and does the job. CHECKPOINT 2 |
| 11 | MEDIA INTAKE | Share a reel / video / image to NOVA (or pick in app) |
| 12 | FRAME EXTRACTION | Key frames from video on a background thread, safe sizes |
| 13 | REAL ANALYSIS (4 hisse) | 13A rules DONE v7.52 / 13B vision + ANALYSE button / 13C search + sourced report / 13D voice, chat photo bug. Vision AI + deep web search, honest sources, nothing invented |
| 14 | GREEN LASER SCAN | Full-screen hacker-style scan overlay tied to the real analysis stages |
| 15 | RESULT CARDS | Report cards, voice read-out, History. CHECKPOINT 3 |
| 16 | MISSING WIRES | Every gap from the autopsy fixed (SocialReplyRules, nc_on, ...) |
| 17 | SETTINGS / SKILLS RESTYLE | New switches (quality, persona, stop behaviour) |
| 18 | HANG PROTECTION | Watchdogs, safe mode after a crash, memory trimming |
| 19 | FULL REGRESSION | All contract tests + compile + JUnit + FeatureVerify updated |
| 20 | FINAL | Final docs, handover prompt, final zip, owner steps. CHECKPOINT 4 |

## STATUS (updated v7.53, Part 13B-1 complete)
Parts 1-12 DONE in code; Part 13 split in 4, and 13B split again by owner: 13A rules DONE (v7.52); 13B-1 DONE (v7.53: AnalysisPipeline + bridge + ANALYSE button + MADAD / SKILLS; Kotlin compile PASS, JUnit only old org.json-stub fails, run_all_checks 0 fail); next 13B-2 (free-provider vision fallback, stuck-call fix, phone-test fixes), then 13C search + compose, 13D voice / chat. Phone / Actions NOT TESTED. Trace: docs/PART13_TRACE_v7_52.md.

## UPDATE (owner order 18 Oct 2026): 13B-2 ke 4 chote hisse
| Hissa | Kaam | Done kab |
|---|---|---|
| 13B-2a | FREE PROVIDER VISION FALLBACK: Groq / OpenRouter me jo image (image_url data URL) le sake use Gemini fail hone par chain me; request builder AnalysisPipelineRules me + JUnit; ApiUsage + cooldown; bina key honest metadata | compile + JUnit PASS |
| 13B-2b | CANCEL KE BAAD ATKI CALL: cancel / watchdog ke baad nayi analysis purani call ke peeche queue na ho (alag executor / abort handle) | JUnit ya contract test PASS |
| 13B-2c | UI POLISH: report text me saaf sections, bade report par scroll, 320 px par input + button theek; chat-bar me naya button nahi | headless UI check PASS |
| 13B-2d | PHONE TEST FIXES (owner ke result ke hisaab se) + FeatureVerify / FeatureStatus / FeatureVoice line + trace rows 12-14, 17-19, 22, 27 DONE + status + 13C ka prompt | run_all_checks 0 fail |
Rule: ek hissa poora khatam, zip + status + agla prompt, phir agla. Phone / Actions NOT TESTED jab tak owner na bataye.

## STATUS UPDATE v7.54: 13B-2a DONE (free-provider vision fallback). Next: 13B-2b (stuck call after cancel), 13B-2c (UI polish), 13B-2d (fixes + trace + status). OWNER RULE (18 Oct 2026): compile / JUnit / run_all_checks / phone tests run ONCE, when Part 13 is complete (after 13D), not after every part. Parts only get written + honestly marked NOT RUN until then.

## STATUS UPDATE v7.55: 13B-2b DONE (stuck call after cancel: new worker). Next: 13B-2c (UI polish), 13B-2d, then 13C, 13D. Tests only after 13D.

## STATUS UPDATE v7.56: 13B-2c DONE (report sections, scroll, 320 px). Next: 13B-2d (fixes + FeatureVerify line + trace + 13C prompt), then 13C, 13D. Tests only after 13D.

## STATUS UPDATE v7.57: 13B-2d DONE => 13B-2 and 13B COMPLETE (code). Next: 13C (SEARCH + COMPOSE), then 13D (voice / chat). Tests, compile, phone only after 13D.

## STATUS UPDATE v7.58: 13C DONE (code): SEARCH + COMPOSE, sourced report. Next: 13D (voice / chat wiring + acceptance: compile, JUnit, run_all_checks, phone test list). Tests, compile, phone only after 13D.

## STATUS UPDATE v7.59: 13D code likha (voice + chat photo), ACCEPTANCE baki (compile, JUnit, run_all_checks, headless, owner phone test). Agla account PART13D_STATUS_v7_59.md ke 'BACHA HUA' se shuru kare.

## STATUS UPDATE v7.60: 13D acceptance ~60 percent (hand review of new Kotlin, face-gate phrases, stale test fix, headless chat-photo check PASS). Compile / JUnit / full run_all_checks / phone NOT RUN. Next: PART13D_STATUS_v7_60.md BACHA HUA.

## STATUS UPDATE v7.61: 13D acceptance ~80 percent. Kotlin COMPILE PASS (main+test, kotlinc 1.9.24), JUnit 1287 run / 29 fail = purane org.json-stub fail (ConnectorParse 11, WebBrowsers 6, WebSources 6, VercelPublish 2, ConnectorRules, FeatureVerify, MusicCatalog, NovaV73 1 each); koi Analysis/FreeVision fail nahi. run_all_checks: 123 pass, 2 naye fail theek kiye (part6 CB quotes, p7 check scope). Mutation check: pt13d_chat guard hatane par test FAIL NAHI hua => test kamzor. Phone NOT TESTED. Next: PART13D_STATUS_v7_61.md.

## STATUS UPDATE v7.61 FINAL: PART 13 COMPLETE (code + sandbox). Kotlin compile PASS, Analysis/FreeVision JUnit PASS (29 purane org.json-stub fail alag), run_all_checks 125 scripts: 122 pass + 3 version-check fail theek kiye (alag chalane par PASS); mutation check PASS. Phone / GitHub Actions NOT TESTED. Next: owner phone test (PART13D_STATUS_v7_60.md a-g), phir PART 14 GREEN LASER SCAN.

## OWNER ORDER 18 Oct 2026 (22:38): PART 14B = LIVE SCREEN MOVIE SCAN (Part 14 ke saath, 13 ke BAAD, plan me add)
Owner kya chahta hai (example): Instagram par reel chal rahi hai, owner bole "nova ye batao kon si movie hai" -> NOVA jo bhi screen par LIVE chal raha hai use scan kare, full-screen GREEN LASER / PRINTER-LASER scan + hacker effects ke saath, phir movie ka naam bataye.
| Hissa | Kaam | Done kab |
|---|---|---|
| 14B-1 | VOICE / TYPED INTENT: "ye kon si movie hai", "ye kaun si film hai", "movie ka naam batao", "is reel ki movie" -> naya route (screen scan). Face gate se na takraye: "ye reel kaun si movie hai" mana NA ho (FACE_PHRASES sirf insaan ka naam mana kare); "is actor ka naam" / "ye kaun hai" abhi bhi mana | JUnit + contract test PASS |
| 14B-2 | LIVE SCREEN CAPTURE: ScreenAlert / NovaAccessibilityService / existing screen-monitor se 2-4 frames (kuch second ke gap par) lena; protected screen (bank, password, OTP) kabhi nahi (AnalysisRules.gateSource); frame sirf analysis ke liye, save nahi | compile + JUnit PASS |
| 14B-3 | MOVIE GUESS PIPELINE: frames + screen par dikha text (caption, watermark, @page, audio-name, hashtag) -> vision AI (13B chain) + web search (13C) -> naam + bharosa (high / medium / low) + sources; honest "verify nahi hua" agar pakka nahi; ACTOR KE CHEHRE SE NAAM KABHI NAHI (13A rule) | JUnit + contract test PASS |
| 14B-4 | FULL-SCREEN LASER OVERLAY: hacker-style green laser line jo upar se neeche printer ki tarah scan kare, grid / glitch / matrix text, stages asli analysis stages se jude (CAPTURE, READ TEXT, VISION, SEARCH, RESULT); overlay touch-through nahi, STOP / ROKO se turant band; lowfx / reduced-motion par saada; Android overlay permission (agar pehle se hai to wahi, nahi to ek baar mango) | headless render PASS + compile |
| 14B-5 | RESULT: naam bolkar + chhota card (naam, bharosa, sources); History me; chat me bhi | contract PASS |
Rule: tests / compile / phone sab ek baar 14B ke baad (owner ka purana rule). Phone par: Instagram reel + "nova ye kon si movie hai" = owner test.
Honest limit: Instagram jaise app ki screen par kuch apps screen capture rok sakte hain; tab NOVA saaf bataye "screen padh nahi paya".

## STATUS UPDATE v7.62: PART 14 DONE (ui.html pt14_laser, add-only). Headless PASS + mutation check; Kotlin untouched. Phone NOT TESTED. Next: PART 14B (live screen movie scan), then 15-20. Tests / compile / phone rule: ek baar 14B ke baad.
- v7.63 PART 14B-1 ADHURA: MovieScanRules.kt + NovaService route + MovieScanRulesTest likhe, compile / JUnit / contract NOT RUN. Dekho PART14B1_STATUS_v7_63.md
- v7.63 PART 14B-1 code + contract PASS + version/CHANGELOG done. Kotlin compile / JUnit / full run_all_checks / phone NOT RUN (is account me kotlinc + internet nahi). Next: compile, phir 14B-2.
- v7.64 PART 14B-2 live screen capture: code + contract PASS; compile / JUnit / phone NOT RUN. Next: compile, 14B-3 movie guess pipeline.
- v7.65 PART 14B-3 movie guess pipeline: code + contract PASS; compile / JUnit / phone NOT RUN. Next: compile, 14B-4 laser overlay, 14B-5 result card + History.
- v7.66 PART 14B-4 laser overlay: code + contract written; phone NOT TESTED. Next: 14B-5 result card + History.
- v7.66 PART 14B-5 result card + History: code done, compile 0 errors, 12 JUnit pass. PART 14 COMPLETE in code. Next: Part 15.
- v7.67 PART 15 result cards + poora padho: code done, compile 0 errors, 18 JUnit pass = CHECKPOINT 3. Next: Part 16.
- v7.68 PART 16 MISSING WIRES: code + static contract done (ANSWER_PHONE_CALLS never requested = fixed; callanswer + calendar MADAD topics; audit_dead_ui.py). Kotlin compile / JUnit / phone NOT RUN. Open owner decisions: voice-while-busy queue, wb_ll. Next: Part 17 SETTINGS / SKILLS RESTYLE.
- v7.69 PART 17 SETTINGS RESTYLE: SWITCHES card (quality / persona / stop behaviour) + STOP bridge + MADAD topic 48; contract + headless PASS; compile / JUnit see PART17_STATUS_v7_69.md; phone NOT TESTED. Next: Part 18 HANG PROTECTION.
- v7.70 PART 18 HANG PROTECTION: safe mode after 2 crashes in 10 min (30 min), banner + exit button, laser skipped; existing watchdogs / memory trim re-checked, unchanged. Compile / JUnit see PART18_STATUS_v7_70.md. Next: Part 19 FULL REGRESSION.
- v7.71 PART 19 FULL REGRESSION: compile 0 errors, JUnit 1352/1352 PASS (real org.json built by the script), run_all_checks 136/136, FeatureStatus safemode row. Phone / Actions NOT TESTED. Next: Part 20 FINAL (docs, handover prompt, final zip, owner steps = CHECKPOINT 4).
- v7.72 PART 20 FINAL: final docs (README FINAL section, OWNER_SETUP section G), OWNER_PHONE_TEST_LIST_v7_72.md (= CHECKPOINT 4), handover prompt NEXT_ACCOUNT_PROMPT_v7_72_AFTER_PART20.md, PART20_STATUS_v7_72.md, final zip. PARTS 1-20 ALL WRITTEN. Kotlin unchanged since v7.71. GitHub Actions NOT RUN, phone NOT TESTED. Next = owner runs APK build + phone list, then fixes only from his results.

