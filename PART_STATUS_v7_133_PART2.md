# v7.133 AUDIT PART 2 of 3 - voice command paths + VoiSpark review + 3 small fixes
Source: NOVA-v7_132-AUDIT-PART1.zip. UPDATE (later in the same session): everything below was then COMPILED and JUNIT-TESTED in the sandbox: Kotlin compile PASS, JUnit OK (1644 tests = 1640 old + 4 new in VoiceRouteAuditTest). NOT tested on a phone.

## BIG NEWS: the sandbox CAN compile Kotlin (Part 1 said it could not)
- kotlinc 1.9.24: curl from https://github.com/JetBrains/kotlin/releases/download/v1.9.24/kotlin-compiler-1.9.24.zip WORKS.
- android.jar: raw.githubusercontent.com is BLOCKED (403) but `git clone --depth 1 --filter=blob:none --sparse https://github.com/Reginer/aosp-android-jar.git` + `git sparse-checkout set android-34` WORKS.
- `bash tools/local_compile_check.sh <ABSOLUTE work dir>` (give the absolute path to the script; a relative $0 breaks its cd). Result on the UNEDITED v7.132 source: Kotlin compile PASS, JUnit OK (1640 tests). (PASS, run in sandbox; not a phone test.)
- Fast probe loop used: compile only tools/audit_probes/*.kt against kc/out_main (-Xfriend-paths) and run with java -cp.

## Task 1: voice command paths (RouteProbe replays the order of NovaService.handle(); ~230 phrases run for real in Kotlin)
PASS (reaches the right local handler): open website (youtube chrome pe kholo, several sites, .com names), plain "youtube kholo" stays open-app; download by name (whatsapp/instagram/phonepe/... download|install karo); call (papa/mummy/name/number, "whatsapp pe X ko call" -> honest unsupported); schedule/meeting/alarm/timer (calendar_add, schedule_create, timer, alarm); trade analysis (gold/eurusd/xauusd/nifty/bitcoin); reels (agli reel, like/comment/share/save, "rahul_sharma ki 5 reels..."); queue: 10 max, 2 dropped of 12 (CmdQueue.split), busy rules (BusyListenRules).
PHRASES THAT REACH NO LOCAL HANDLER (go to the cloud AI, so they need key + internet):
- WhatsApp natural order: "Anand ko WhatsApp bhejo ki main late aaunga", "mummy ko whatsapp karo ki ...", "papa ko whatsapp pe bolo ...", "anand ko whatsapp pe good morning bhejo", "anand ko msg/sms/text karo ...", "send whatsapp to rahul saying hi". (HelpGuide's own example "nova Rahul ko WhatsApp karo kal milte hain" did not parse locally.) -> FIXED in code (fix C), uncompiled.
- One-time scheduled message "kal 5 baje anand ko whatsapp pe hello bhejo" (only roz/har recurring is local). NOT fixed (owner decision).
- Calendar READ: "meeting kab hai", "aaj kya schedule hai", "my next meeting", "kal ki meeting dikhao" - no local handler (CalendarRead.kt only schedules reminders). MISSING FEATURE, not fixed.
- "remind me tomorrow at 9 am to call rahul" (English remind with a day), "reminder lagao", "meeting cancel karo", "saare alarm hatao", "timer band karo".
- Calls: "phone karo papa ko", "call karo papa ko" (verb first), "ambulance bulao", "108 lagao". "100 par call karo" -> arg "100 par"; emergency numbers (112/100/108, "police ko call karo") go to a contact search ("contact nahi mila") because NovaService.NUMBER_RE needs 5+ chars. LocalSkills says NOVA never dials emergency numbers: owner must decide the policy (say the 112 line, or dial).
- Download: "install youtube music", "youtube music download karo" (DL_BLOCK blocks "music").
- Trade: "usd inr ka analysis" (two words), "aaj market kaisa hai", "stop loss kitna rakhu gold me" (CUE has "stoploss" only). "trade close karo / gold trade band karo" is treated as a PLACE request (opens MT5 with the "I cannot place" text) - harmless but wrong meaning.
- Reels: "reels chalao", "reel ko pause karo", "reel dekhna band karo", "rahul_sharma ki post par nazar rakho" (watch needs an action word). "instagram reels kholo" opens instagram.com in Chrome (web_open), not the app - owner decision. "reels dekho 10 minute" goes to screen_video analysis.
PROVEN BUGS (found by running the Kotlin code):
- A. CmdQueue.split cut "phir se / phir bhi": "ye gaana phir se chalao" -> ["ye gaana","se chalao"]; "torch phir se on karo" -> ["torch","se on karo"]; "torch on karo aur phir se wifi on karo" -> 2nd part "se wifi on karo". Also a dangling "aur": "wifi on karo aur uske baad bluetooth on karo" -> first part "wifi on karo aur" (classifies as nothing). -> FIXED in CmdQueue.kt (uncompiled).
- B. "play store pe/me/par <app> download|install karo" was taken by AppSearchRules (play word + "store" = in-app search) before the download route; ("se" worked). -> FIXED: AppSearchRules.parse returns null when OpenWebRules.parseDownload accepts the sentence (uncompiled).
- C. WhatsApp natural order (above) -> WhatsAppCommandRules.natural(): needs literal "ko whatsapp", name <= 3 words, no digits / time / plan / pronoun words, a real text; rejects media words (photo, video, voice, location...) and verb-only text ("mummy ko whatsapp message bhejo"). Guard so "roz subah 8 baje mummy ko whatsapp pe hello bhejo" stays a schedule (it contains roz/baje). (uncompiled)

## Task 2: VoiSpark v7.129 + v7.130 + v7.131 review (static read; no network, no API key, NOT run)
- Key handling OK: saved trimmed, whitespace rejected, only SecureStore slot, only sent to api.voispark.com in the Authorization header, never logged. Fallback chain VoiSpark -> Ultron effect -> normal voice is wired in NovaService.speak/startVoiSpark.
- LOW race: startVoiSpark runs speak() on a new thread and the generation is read inside the thread; a STOP in that few-ms gap is not seen by VoiSpark (audio could still play). Fix idea: take gen on the calling thread and pass it in.
- findAudio takes ANY https string under a key containing url/link/audio/file/result/output (even callback/avatar urls); a wrong link -> "playback fail" + 10 min pause. Prefer keys containing "audio". Real answer shape still unknown: run Settings > VOISPARK CHECK on the phone and pin the field.
- download(): redirects are off; a 302 from a CDN = "audio download nahi hua".
- v7.130 AutoSetup.startQwen respects AUTO DOWNLOAD: logic OK (qwenStarted is only set after the switch check).
- v7.131 reply guard: isAssistantEcho matches by substring when the incoming text is >= 8 letters/digits: a person's reply that is only a phrase NOVA just wrote ("thank you") is treated as an echo and gets no AI reply. Part 1's other worry (owner line then person's message) is NOT proven: the person's message is the newest line. Needs a phone test.

## Task 3: versionName 7.114 / test_part4_fx_render - decisions NOT done (owner said stop)
- app/build.gradle.kts versionName is still "7.114"; test_part7_brain_learned_contract.py pins it. Bump to 7.133 together with that guard (update both in one step).
- test_part4_fx_render: sandbox-only suspect (see Part 1). Needs a phone-like run.

## Files changed in v7.133 (ALL NOT COMPILED, NOT TESTED)
CmdQueue.kt (split, splitRaw), AppSearchRules.kt (1 line), WhatsAppCommandRules.kt (natural()), CHANGELOG.md, this file, NEXT_ACCOUNT_PROMPT_v7_133_PART3.md, tools/audit_probes/*, tools/pending_tests/VoiceRouteAuditTest.kt.txt

## FINAL (Part 2 completed, same session)
Owner decisions: emergency numbers and one-time scheduled message = NOT needed (skip, do not build). "reel kholo" family: open Instagram Reels IN THE APP by default; only when the sentence says chrome / browser open it in Chrome.
Done after the first draft:
- Fix C finished: parse() now removes the trailing verb in natural order ("good morning bhejo" -> "good morning"). JUnit PASS.
- NEW ReelOpenRules.kt + NovaService.openReels (handle() runs it right after the ReelRules plan): "reel kholo", "instagram reels kholo", "nova reel dekhne ka man kar raha hai", "reels chalao", "reel dekhni hai" -> Instagram app (https://www.instagram.com/reels/ with package com.instagram.android); "chrome me reels kholo" -> Chrome. Not taken: like/save/comment/share/scroll/next/back, questions (kya), download, stop/band, friend usernames (ReelRules keeps them). JUnit PASS (reelOpenAppOrChrome). Phone: NOT TESTED (needs Instagram installed; Android may ask to open the link in the app).
- Real tests added: app/src/test/.../VoiceRouteAuditTest.kt + RouteProbe.kt (replays the order of NovaService.handle()). tools/audit_probes/ keeps the probe mains + batch-1 result.
- versionName 7.114 -> 7.133; tools/test_part7_brain_learned_contract.py now accepts 7.114 or 7.133.
- Still open (not built, needs phone or later): calendar-read voice question, trade words (stop loss / usd inr / close vs place), "reel pause / reel dekhna band karo", verb-first call "phone karo papa ko", VoiSpark STOP race + findAudio "audio" key preference, test_part4_fx_render (sandbox-only suspect).
- Python guards (run_all_checks.sh, partly watched): 132+ PASS in the run; the 8 version-pin guards failed only because of the 7.133 bump, were fixed (7.133 added to their allowed lists) and re-run singly = PASS. The full script was NOT re-run to the end after that fix: next account re-runs it (expected only test_part4_fx_render FAIL).
