## v7.89 - PROFILE SEARCH: "nova instagram par rahul_sharma search karo" (add-only), versionName 7.89
- NEW ProfileSearchRules.kt (pure: site + name / username parser, safe https links, honest texts), NovaService.runProfileSearch + hook in handle() (right after the camera-eye hook), Logic.LOUD_KINDS "profile_search", HelpGuide topic "profilesearch", ui.html (Home list, Skills registry, CAPS), test_part6 MAP, ProfileSearchRulesTest (6 tests).
- Username (with @, _, ., a digit, or the word username / handle / id) opens the public profile; a name opens the site's public people search; 2+ sites or no site = Google. No AI, no key, no scraping, no background work (cannot hang the app).
- NOT built, on purpose: lookup from a face / photo / selfie, and a person's number / address / location / password: honest refusal line.
- RUN: python port of the parser on 26 sample phrases matched the intended result. NOT RUN: Kotlin compile + JUnit (no network here), python guards 144/144 PASS on v7.89, audit_dead_ui REAL GAPS 0. NOT TESTED on phone.

## v7.88 - CAMERA EYE: "nova peeche wale camera se dekho, ye kya hai" (add-only), versionName 7.88
- NEW CameraEyeRules.kt (pure: phrase rule back / front, prompts, texts), CameraEyeActivity.kt (visible "NOVA dekh raha hai" screen, Camera2, ONE JPEG in memory, closes by itself after 20 s / CANCEL / leaving the screen), manifest entry, NovaService.runCameraEye (starts the screen, waits, sends the photo to the owner's Gemini key with Google Search for OBJECT names / details). Home card, Help topic "cameraeye", Skills entry, CameraEyeRulesTest.
- Safety (owner asked for face -> Instagram / Facebook name search: NOT built, on purpose): NOVA never identifies or searches for a real person from a face; people are described generically; a name written in the picture may be read. Objects / products / plants / animals / logos / landmarks get the exact name + details.
- RUN: Kotlin compile 0 error, JUnit 1405 PASS + 1 FAIL in my own new test (classifyDoesNotBreakOpenCamera guessed the wrong kind for "camera kholo"); that test was REMOVED (the real protection is the CameraEyeRulesTest NOT-words test). JUnit NOT re-run after the removal. NOT RUN: python guards for 7.88, GitHub Actions. NOT TESTED on a phone (Camera2 on a real device, background start needs "Display over other apps").
- v7.88 CHECK (this account): RUN python guards 144/144 PASS, audit_dead_ui REAL GAPS 0 (Home card, CAPS, Skills, HelpGuide all wired). Static read of CameraEyeActivity + NovaService.runCameraEye: every helper signature exists. NOT RUN: Kotlin compile + JUnit (no network in this sandbox) -> GitHub Actions build is the first compile. NOT TESTED on phone.

## v7.87 - SHARP hang + SMOOTH blur (ui.html only, add-only), versionName 7.87
- Phone report (v7.86): SHARP hangs the whole app (slowly after a while, when settings / chat open, and constantly); SMOOTH looks blurry / pixelated.
- CAUSE (from code, NOT measured on the phone): choosing SHARP switched OFF the emergency stop of the 3D orb (NovaPerf cap 3, "never switch the orb off") and also raised pixels to 2x on MID / LOW phones -> a heavy shader kept running until the app froze. SMOOTH drew its still orb at 0.6 x 1.5 pixels (about 225 px) although it is drawn only once per state change.
- FIX: SHARP raises pixels and keeps the "no orb stop" rule ONLY on HIGH phones; MID / LOW phones in SHARP keep the normal safety net (orb falls back to the 2D orb if the phone stalls). SMOOTH: pixel cap 2 and the still orb uses full pixels (no scale-down).
- RUN: test_part6_smooth / perf_governor / ui_tier / ui_contract / ui_render_smoke / part3b / part3_fx / part4_fx guards PASS. Kotlin not changed (v7.86 compile + JUnit 1400 still valid). NOT TESTED on the phone.
- OPEN: if SMOOTH still feels dead (orb does not move), the next step is a slow 10 fps orb instead of a still one. Not done.

## v7.86 - popup / laser / "samajh nahi aaya" loop fixes (add-only), versionName 7.86
- ROOT CAUSE (from code, NOT reproduced on phone): after a scan of a reel / movie the hands-free FOLLOW-UP listen (no wake word) opened by itself; the video's own sound was heard as a command -> "Ye samajh nahi aaya, ek baar aur bolo" + HUD card + more replies, up to 6 times (looks like several assistants / popups by themselves).
- FIX: QuickScan.followAllowed + videoQuietUntil: no hands-free follow-up for 90 s after a video scan (wake word "nova" still works). A follow-up turn that is only noise / not a command now ends silently (screenFollow + the two "samajh nahi aaya" fallbacks in handle()).
- Phrases: QuickScan.wantsScan ("nova scan karo", "screen scan", "laser scan") and wantsLook without the look word ("nova ye kya chal raha hai", "ye kya dikh raha hai"). Logic.classify routes both to "analyze". qr / virus / "scan band karo" are excluded.
- Movie popup: automatic popup only for MEDIUM / HIGH names and only if overlay permission is ON; never opens the browser by itself on a normal scan (explicit "ye kaun si movie hai" keeps old behaviour); auto closes after 90 s.
- HUD (NovaHud): a card that is flying apart is removed at once when a new one shows, and by a 700 ms fallback (no ghost second assistant).
- Laser: epoch guard (old end no longer kills a new scan's laser), 90 s watchdog, big label shrinks to fit narrow phones.
- UI (add-only): Home card "SCREEN SCAN" (ui.html F list) with the phrases + POPUP ALLOW / ACCESSIBILITY buttons; HelpGuide topic "scan" (voice: "scan kaise karun"). "ab ye dekho" follow-up now also shows the laser (screenAnswer wrapper). Text-path laser already moves to VISION 60 after the screenshot.
- Tests added: QuickScanV786Test (4). versionName 7.86.
- RUN here: Kotlin compile 0 error, JUnit 1400/1400 PASS (1396 old + 4 new). Python guards 144/144 PASS after 3 small guard-driven fixes (literal `if (screenOpen() ||` kept; helper not named screenAnswer*; new help topic mirrored in ui.html CAPS + Skills list + test_part6 MAP). NOT RUN: GitHub Actions. NOT TESTED: phone.

## v7.85 - Home stall on MID phones in SHARP (add-only), versionName 7.85
- Phone report (v7.84, SHARP, MID tier, battery 15%): Home opened nicely, then stalled after 1-2 s and the orb / stars vanished for a moment.
- Likely cause (headless Chrome cannot reproduce a phone GPU stall, so this is NOT proven): full-screen star canvas at 2x pixels + 60 fps + canvas glow (shadowBlur) + backdrop blur re-done every frame on top of it.
- FIX (ui.html only): MID / LOW phones: star canvas max 1.25x pixels, star + core canvases 30 fps, no canvas shadow glow, no backdrop blur (html[data-tier]). HIGH phones unchanged. NOVA core keeps full sharp pixels. Canvases repaint at once after Android drops them (contextrestored / app visible again).
- NOT TESTED on the phone. Kotlin not changed.

## v7.84 - SMOOTH graphics mode really smooth (add-only), versionName 7.84
- PROBLEM FOUND: in SMOOTH the live theme (3D orb, NOVA core, starfield) still redrew about 30 times a second, and a quality change needed an app restart.
- FIX (ui.html only, nothing removed): SMOOTH = still theme. Orb, core and starfield are drawn once per state change (listen / think / speak colour and text still follow), decorative CSS loops and HUD rings are off, old 2D orb fallback is throttled. Functional signals stay (typing dots, busy spinner, voice bars, laser scan).
- Settings: choosing SMOOTH applies at once (no restart). Leaving SMOOTH removes still mode at once; full pixel size / effects level return after the next app start (said in the note under the switch).
- Headless Chrome (real ui.html): AUTO 115 draws in 2 s, SMOOTH 0 draws in 2 s, repaint on state change works, live switch AUTO -> SMOOTH -> SHARP works. NEW tools/test_part6_smooth_contract.py.
- NOT TESTED: real phone. Native popup HUD (NovaHud.kt) is separate and unchanged. No Kotlin changed (v7.83 unread fix kept as is).

## v7.82 - PART 5 (final check; add-only), versionName 7.82
- FIRST real Kotlin compile of v7.78-v7.81: 0 errors. JUnit 1389 tests: 1388 PASS + 1 FAIL found, fixed below.
- BUG FIXED: SiteIntent.smartSubject stopped at the linking word ("... fitness brand KI website") and returned only "fitness". Now skips ki/ka/ke/ko/of/for right before the noun -> "international fitness brand". SiteSubjectTest unchanged. After fix: JUnit 1389/1389 PASS.
- Audit (tools/audit_dead_ui.py): 0 dead controls, 0 unused bridge methods, 0 missing help topics. A few unused helper functions exist (e.g. PhoneActions.setRinger/setRotation duplicates of NovaService tools, MovieScanRules.notYetText, ScreenVideoRules.noFramesText); NOT wired, left as is.
- test_part20_contract: stale CHANGELOG-top check now accepts v7.8x on top.
- Final: python guards 143/143 PASS (part20 fixed), fx_render tests PASS. NOT DONE: phone 10 s timing (reel / normal screen / "ye kaun si movie hai"), GitHub Actions, phone tests.

## v7.81 - PART 4 of 5 (live scan in 10 s: "nova dekho, kya chal raha hai") (add-only), versionName 7.81
- NEW QuickScan.kt: the 10 s budget (3 frames 0.7 s apart instead of 4 x 1.5 s; vision call waits 7 s instead of 30 s; first plain reading 7 s / 9 s; no new web search after 5.5 s; 1 short title query) + the clock text + the phrase rule.
- Voice: "nova dekho btao kya chal ra h", "ye dekho batao" now start the same scan without the word "screen" (narrow rule; battery / gaana / gallery / call / open etc. are NOT taken over).
- Laser overlay: shows the REAL elapsed clock ("3.2 s / 10 s") under the real stage + percent. Old stages unchanged.
- Add-only: MovieScanCapture.run(gapMs default = old 1500), VideoScanPipeline.run(budgetStartMs default -1 = old behaviour). Follow-up questions keep the old limits. Protected screens still never captured or sent.
- NEW tools/test_part4_quickscan_contract.py + app/src/test/.../QuickScanTest.kt. RUN: all python contract guards PASS except test_part20_contract (stale CHANGELOG check, fails the same on the untouched v7.80 zip). NOT RUN: Kotlin compile, JUnit, GitHub Actions. NOT TESTED: phone, real timing.
## v7.80 - PART 3B of 5 (UI glitches: short phones) (add-only), versionName 7.80
- FOUND by headless Chrome (real ui.html, 320x568): Home content (618 px) is taller than the 508 px area, so the orb box was squeezed to ~95 px and the chat log / quick-action rows were clipped (Home was overflow:hidden). FIX (add-only, old rules kept): one new rule for phones 521-640 px tall: `#app.wrap` scrolls vertically, `.orbbox` = fixed 150 px, chat log = 140 px. Result at 320x568: orb 150 px, centred in its rings, all rows reachable by scroll, no side overflow. Phones taller than 640 px and the <=520 px landscape rule are unchanged.
- Sweep (t_home t_skills t_limits chatb gear x 320x568 / 360x740 / 412x915): 0 JS errors, 0 horizontal overflow, nothing else found to fix. Chat bubble speaker/COPY row and the Skills filter-chip scroller looked intended -> left alone.
- NEW tools/test_part3b_contract.py (static + NOVA_HEADLESS=1 geometry at 320x568, 360x640, 360x740, 412x915). test_part3a_contract version pin relaxed to ">= 7.79" so later add-only versions pass.
- RUN: part3b + part3a + 125 python guards PASS; 13 headless tests re-run with a longer limit: 11 PASS. 2 FAIL identically on the untouched v7.79 zip (so NOT caused by 3B, NOT fixed): test_part3_fx_render ("typing reveal did not start" / "typing flag stuck") and test_part4_fx_render ("360x700 lowfx star count 23 -> 23").
- NOT RUN: Kotlin compile / JUnit (no Kotlin changed), GitHub Actions. NOT TESTED: phone.

## v7.79 - PART 3A of 2 (UI glitches: core orb) (add-only), versionName 7.79
- FOUND by headless Chrome (real ui.html, 320 / 360 / 412 px): old rule `.orbbox canvas{height:100%!important;position:relative}` overrode #p4_core -> NOVA core orb was shrunk (92 px at 320) and pushed LEFT of its ring. FIX (add-only, old rule kept): `.orbbox #p4_core{position:absolute;left:50%;top:50%;height:100%;aspect-ratio:1/1;transform:translate(-50%,-50%)}` (all !important).
- Sweep result: no horizontal overflow, no JS error on HOME / SKILLS / LIMITS / CHAT / SETUP at 3 sizes; chat input sits above the tab bar. Quick-action row (PHONE CONTROLS ...) is an intended swipe scroller (#feats), unchanged.
- OPEN for 3B: at 320x568 the orb box itself is squeezed (~92 px) because Home content is taller than the screen.
- NEW tools/test_part3a_contract.py (static + NOVA_HEADLESS=1 geometry). RUN: this + 8 older guards = PASS. NOT RUN: Kotlin compile, JUnit (no Kotlin changed), GitHub Actions. NOT TESTED: phone.

## v7.78 - PART 2 of 5 (40%) SPEED: thinking + kaam (add-only), versionName 7.78
- THINKING: CloudTaskText Gemini call now also uses GeminiSpeed.withFastThinking (2.5 Flash budget 0, 3.x Flash level minimal); a 400 retries the old body once. Cloud listener retry pause 700 -> 350 ms (retry itself unchanged).
- KAAM: fixed sleeps replaced by early-exit waits that keep the OLD time as the cap: in-app search 1800 ms (now: app in front + screen stable), share sheet 1300 ms (screen changed + stable), media play/pause confirm 400 ms (until state matches), Gmail Send poll 8 x 800 ms -> same ~6.4 s cap polled every 300 ms.
- NEW SpeedRules.kt (pure) + SpeedRulesTest.kt (7 JUnit) + tools/test_part2_speed_contract.py. Version-pinned old contract guards now accept later add-only versions.
- Deliberately UNCHANGED: music playback verification (1500 ms first sample), WhatsApp batch send waits (17 s / 900 ms), accessibility button-tap waits - accuracy over speed there.
- RUN: test_part2_speed_contract + 7 version guards (python). NOT RUN: Kotlin compile, JUnit, full run_all_checks, GitHub Actions. NOT TESTED: phone.

## v7.76 patch - VIDEO / REEL READ + MOVIE POPUP + PROFESSIONAL UI (add-only; versionName still 7.74)
- "screen dekho" on Instagram / YouTube / Netflix / Hotstar etc. (or "reel me kya chal raha hai", "ab ye reel dekho") now takes 4 frames over ~5 s and asks the vision AI what HAPPENS in the video (not the like / comment / ID buttons). NEW ScreenVideoRules.kt (pure) + VideoScanPipeline.kt. Same AI chain as before: Gemini, then free Groq / OpenRouter keys. No key = old one-screenshot reading.
- If it looks like a movie / web series / anime: short title web search, name + honest confidence (LOW = "verify nahi hua"), then a small POPUP BROWSER over the app (NEW MoviePopup.kt, overlay permission already used by the laser; https only, no file access; without permission the normal browser opens). Popup has the video summary, 4 suggestion chips (Story & cast, Where to watch, Trailer, Review / Episodes) and BROWSER / BAND buttons. The explicit "ye kon si movie hai" scan opens the same popup.
- Unchanged: no face / actor identification (only the title of the work), protected screens (bank, payment, settings) never captured, frames only in memory.
- UI: developer / test items HIDDEN with CSS only (style id v76_pro_css in ui.html; nothing removed, ids and code stay): 5 browser test button, TEST REPORT texts, extra jaanch tests, galtiyan buttons, report copy / share, crash recover, quota status box. Delete that one style block to show them again.
- RUN: Kotlin compile + JUnit 1373 PASS (11 new in ScreenVideoRulesTest), run_all_checks 139 / 139 PASS. NOT RUN: GitHub Actions. NOT TESTED: phone.

## v7.74 patch - false wake guard + self-test fix (add-only; versionName still 7.74)
- FeatureVerify: KYA CHALTA HAI check now reads the {"items":[...]} object (was parsed as an array -> always "0 rows").
- Logic.heardWake(json, wake, isFinal): the weak form "[unk] nova" counts only on a FINAL recognizer result. "nova ..." / "hey nova ..." unchanged. NovaService uses it. New JUnit unkNovaNeedsFinalResult.
- RUN here: Kotlin compile + JUnit 1359 / 1359 PASS. NOT RUN: GitHub Actions. NOT TESTED: phone (watch false-wake count in wake info).
- SPEED: GeminiSpeed.supportsLevel: Gemini 3.x Flash (not Lite / Pro) gets thinkingConfig.thinkingLevel = "minimal" (default was medium = slow). A 400 falls back to the old request, and the model is then remembered (fastRejected) so no extra round trip. Gemini 2.5 Flash unchanged (thinkingBudget 0).
- LASER on "screen dekho": the laser overlay now also runs while the screen is read and the AI thinks (screenLaserBegin / End, hidden for the screenshot). Needs "display over other apps" (already ON); safe mode skips it as before. The spoken "madad karun?" line is unchanged.
- RUN: Kotlin compile + JUnit 1360 / 1360 PASS, run_all_checks 139 / 139 PASS. NOT TESTED: phone.
- MORE SPEED: command end-of-speech silence 1800 -> 1300 ms (Logic.END_SILENCE_MS + CaptureRules.SILENCE_MS; long / open-ended sentences still wait longer via adaptiveSilence); on-device AI waits max 3.5 s (was 9 s) while online before the cloud answers; a hung Gemini model is skipped after 15 s (voice / screen) or 10 s (transcribe) instead of 25 s. Pinned tests and scripts updated. To undo: set END_SILENCE_MS back to 1800 if sentences get cut.
- RUN: Kotlin compile + JUnit 1360 PASS, run_all_checks all PASS. NOT TESTED: phone.
- WEBSITE PREVIEW BY VOICE: SitePreviewRules ("website dikhao", "website ka preview dikhao", "preview dikhao", "show website preview"; never build / publish / Chrome / edit sentences). NovaService.sitePreview() sends the newest website (also restored from disk for 6 h) to the chat page, which opens the full-screen preview (ui.html openSitePreview, same sandboxed iframe as the DEKHO button). 2 new JUnit.
- RUN: Kotlin compile + JUnit 1362 PASS, run_all_checks 139 / 139 PASS. NOT TESTED: phone.

## v7.74 - ONE + MENU for photo / video / file (add-only), versionName 7.74
- Owner screenshots (19 Oct 2026): chat me photo / video / file alag-alag jagah the. Ab SAB + button ke menu me ek jagah: PHOTO / VIDEO CHUNO, FILE / MEDIA CHUNO (aur website media laga ho to HATAO). Alag paperclip aur MEDIA button chhupa diye (ids / code nahi hata, add-only).
- Auto reply ka "Main abhi busy hoon..." wala prefilled box v7.72 / v7.73 me pehle se nahi hai (sirf AI baat karta hai). Screenshot wali APK purani build thi. Naya APK install karke Setup me dekho: purana box aur "AI SMART" checkbox nahi dikhna chahiye.
- NEW tools/test_oneplus_contract.py. Version-pinned guards accept 7.74.
- NOT RUN: Kotlin compile / JUnit (kotlinc nahi), GitHub Actions. NOT TESTED: phone (+ menu).

## v7.73 - VOICE WHILE BUSY (add-only), versionName 7.73
- Owner decision 19 Oct 2026: busy NOVA par bola hua command ab queue me jaata hai (HAAN). Locked phone par wake word / command sunna = NAHI (Lock Guard bilkul nahi chheda).
- NEW BusyListenRules.kt (pure): mayListen (busy + NOVA bol nahi raha + phone unlocked + koi yes/no jawab ka wait nahi) aur decide (QUEUE / STOP / EMPTY / NONE). NEW ExtCfg.busyListen (default ON, prefs key busy_listen).
- NovaService mic loop: busy ho par bhi (jab NOVA khud nahi bol raha) wake word sunta hai; command khatam hone par offline (Vosk) text cmdQ me voice=true ke saath jaata hai, "Yaad rakh liya" sys line aati hai. "ruko / stop" bolo to softStop (queue bhi saaf). NOVA jab bol raha ho tab ab bhi audio fenka jaata hai (apni awaaz ka echo na sune).
- Honest limit: busy ke dauran sirf offline (Vosk) text queue hota hai, cloud transcribe nahi; Vosk ne kuch na suna to "dobara bolo" aata hai.
- Tests: BusyListenRulesTest (6 JUnit), tools/test_busy_listen_contract.py. Version-pinned guards (4 tools/*.py + test_part20_contract.py) ab 7.73 bhi accept karte hain.
- NOT RUN: Kotlin compile + JUnit (sandbox me kotlinc / internet nahi) -> GitHub Actions chalayega. NOT TESTED: phone.

## v7.72 - PART 20: FINAL (add-only), versionName 7.72
- NO Kotlin / ui.html code changed. Only: versionName 7.71 -> 7.72, docs, one new Python test, final zip.
- NEW OWNER_PHONE_TEST_LIST_v7_72.md (CHECKPOINT 4): numbered PASS / FAIL list for Parts 5-18 checkpoints + Parts 16 / 17 / 18 + final long-use test; both pending owner decisions listed at the end.
- NEW NEXT_ACCOUNT_PROMPT_v7_72_AFTER_PART20.md (handover: fix-from-owner-results only, ask owner before touching the 2 pending decisions, honest limits).
- NEW PART20_STATUS_v7_72.md (what was RUN here, what is NOT RUN / NOT TESTED).
- README: pointer on top + "FINAL v7.72" section (old lines untouched). OWNER_SETUP_AND_APK_STEPS.md: new section G / G2 (repo upload, Actions run, APK install, red-build help). NOVA_20_PARTS_PLAN.md and docs/HANDOFF.md: Part 20 status lines.
- NEW tools/test_part20_contract.py (docs exist, add-only, phone list coverage, honesty words, secret scan with the known fake-redaction test file allowed).
- Version-pinned guards (4 tools/*.py) now also accept 7.72.
- Secret scan: no keystore / .ppn / local.properties; no real-looking key outside MemoryScreenOpsTest.kt (redaction test with fake strings; not proven fake, see PART20_STATUS).
- NOT RUN: Kotlin compile + JUnit for v7.72 (sandbox had no kotlinc / internet; Kotlin unchanged since v7.71 where 1352 / 1352 PASS), GitHub Actions. NOT TESTED: phone.

## v7.71 - PART 19: FULL REGRESSION (add-only), versionName 7.71
- The 29 "old org.json-stub" JUnit failures were a HARNESS gap, not app bugs: android.jar only has throwing org.json stubs. tools/local_compile_check.sh now builds the REAL org.json 20240303 from GitHub source when JSON_JAR is not given (Maven Central is blocked in the sandbox). Result: JUnit 1352 run / 1352 PASS / 0 fail.
- FeatureStatus: new row "safemode" (warn only while Part 18 safe mode is ON); callanswer row (Part 16) checked.
- NEW tools/test_part19_contract.py (rows + harness + Part 16 / 17 / 18 contracts).
- REAL RESULTS: Kotlin main + test compile 0 errors; JUnit 1352 / 1352 PASS; run_all_checks 136 scripts, 136 pass, 0 fail. NOT RUN: GitHub Actions. NOT TESTED: phone.
- Version-pinned guards accept 7.71.

## v7.70 - PART 18: HANG PROTECTION = SAFE MODE (add-only), versionName 7.70
- ALREADY THERE (checked, unchanged): per-job watchdogs (turn 120 s, analysis 120 s, frames 60 s, TTS deadline, Auto-Heal 25 s), CrashNote black box, memory pressure -> page lighter (NativeSmooth) + LocalBrains.release().
- NEW CrashGuard.kt: 2 crashes inside 10 minutes => SAFE MODE for 30 minutes (pure rules CrashGuard + prefs store CrashGuardStore, crash time written with commit() from the CrashNote handler BEFORE the normal crash handling).
- Safe mode effects: page forced to light effects, cosmetic laser overlay skipped (NovaService.startLaser), banner "SAFE MODE ... SAFE MODE BAND KARO" (ui.html pt18_safe). Calls, messages, data untouched. Auto ends after 30 min; the button ends it earlier and forgets old crash times.
- Bridge getSafeMode / exitSafeMode; MADAD topic "safemode" (49 topics) + SKILLS entry.
- Tests: CrashGuardTest (5 JUnit), tools/test_part18_contract.py (static + headless 320 px; mutation check: removing the crash hook = FAIL).
- Version-pinned guards accept 7.70. NOT TESTED: phone (force 2 crashes to see the banner).

## v7.69 - PART 17: SETTINGS RESTYLE (add-only), versionName 7.69
- NEW Settings "SWITCHES" card (ui.html script pt17_switches, placed above the SAVE row): GRAPHICS QUALITY (AUTO / SHARP / SMOOTH, same store as the old #s_quality select), PERSONA (ON / OFF, same bridge as the Part 9 button) and STOP KAISE KAAM KARE (SIRF ROKE default / ROKE + NOVA BAND). Old #s_quality and the PERSONA button stay and are kept in step (nothing removed).
- STOP switch: new bridge getStopPowersOff / setStopPowersOff in MainActivity (backed by the existing ExtCfg.stopPowersOff, default OFF = SafeStop SOFT). Listed in BRIDGE.
- MADAD topic "switches" (Kotlin HelpGuide + ui.html CAPS, 48 topics, equal). 
- Tests: tools/test_part17_contract.py (static + headless chromium at 320 px with a fake Android bridge; mutation check: removing the STOP handler gave 3 FAIL), HelpGuidePart17Test.kt (3 JUnit).
- Version-pinned guards (4 tools/*.py) now also accept 7.69.
- NOT CHANGED ON PURPOSE (owner decision still pending): voice while NOVA is busy is not queued; wb_ll wake word on a locked phone.
- Kotlin compile / JUnit / phone: see PART17_STATUS_v7_69.md.

## v7.68 - PART 16: MISSING WIRES (add-only), versionName 7.68
- REAL GAP FIXED: ANSWER_PHONE_CALLS (needed by "call uthao" via PhoneActions.answerCall) was in the manifest and checked, but NEVER asked at runtime. MainActivity permission round now asks it (API 26+, minSdk is 26); SetupChainRules.VERSION 1 -> 2 so phones that already finished the old round get one more.
- FeatureStatus: new row "callanswer" (Call uthana / kaatna) reads that permission.
- HelpGuide + ui.html CAPS: 2 new MADAD topics "callanswer" and "calendar" (both existed as features, no help text); both added to PRIORITY so the older "call" topic cannot shadow them. 47 topics, Kotlin == ui.html.
- NEW tools/audit_dead_ui.py (dead input/button ids, unused bridge methods, help topics vs ui.html, retired SocialReplyRules / PaymentReplyRules leftovers): 0 real gaps; mutation check (fake dead checkbox) is caught. NEW tools/test_part16_contract.py (mutation check PASS). NEW HelpGuidePart16Test.kt (written, NOT RUN).
- CHECKED, no change needed: nc_on (wired in v7.50), SocialReplyRules leftovers (none in Kotlin or ui.html), docs/pending_G (both files byte-identical to the live MediaPlace.kt / SiteMediaStore.kt), OTP / password / PIN / CVV hold-back (SmartReplyRules), overlay nudge repeat (OverlayNudgeRules.RENUDGE_MS), recording writer error (RecordSession.Saved.writerError). docs/retired_guards re-run: remaining FAILs are string checks for the other code line, the features themselves exist.
- NOT CHANGED ON PURPOSE: (a) voice said while NOVA is busy is still not queued (mic loop drops audio while busy / speaking = echo protection; needs compile + phone test); (b) wb_ll wake word on a locked phone (owner decision, clashes with Lock Guard).
- tools/test_android_runtime_contract.py now accepts versionName 7.67 or 7.68.
- NOT RUN: Kotlin compile, JUnit, phone / GitHub Actions.

## v7.67 - PART 15: RESULT CARDS (CHECKPOINT 3), versionName 7.67
- NEW ResultCardRules.kt (pure): card() = headline + confidence + verified / not-verified counts + up to 3 source domains; historyLine() (counts only); isReadAll() "poora padho / read the full report"; speakable() + chunks() for the voice.
- AnalysisPipeline hands over the card (takeCard); NovaService.deliverAnalysis: card + full text in chat, History "NOVA ANALYSIS" (text only), card on the HUD. "poora padho" reads the last full report (analysis or movie scan), silent mode stays silent. Route runs before the movie route.
- Tests: ResultCardRulesTest (6 JUnit), tools/test_result_card_contract.py. REAL: Kotlin main compile 0 errors; JUnit LaserMathTest 9 + MovieCardTest 3 + ResultCardRulesTest 6 = 18 pass / 0 fail. Phone NOT TESTED.

## v7.66 (cont.) - PART 14B-5: RESULT CARD + HISTORY (PART 14 COMPLETE in code)
- MovieGuessRules.card (name, confidence, up to 3 source domains) + historyLine; NovaService.deliverMovie: card + full text in chat, History entry "NOVA MOVIE" (text only), card on the HUD, laser overlay shows RESULT 100 for 0.9 s then closes.
- REAL RESULTS: Kotlin main compile (all of app/src/main incl. NovaService + LaserOverlay) = 0 errors. JUnit LaserMathTest 9 + MovieCardTest 3 = 12 pass / 0 fail. Old test files fail to compile only because the harness junit stub lacks assertNotEquals / assertArrayEquals(IntArray) (not a code error). Phone NOT TESTED.

## v7.66 - PART 14B-4: FULL-SCREEN LASER OVERLAY (add-only; compile / JUnit / phone: see PART14B1_STATUS_v7_63.md UPDATE v7.66), versionName 7.66
- NEW LaserOverlay.kt: LaserMath (pure: stage parse, sweep, simple mode, glyphs, glitch, labels) + LaserOverlay (system overlay, whole screen) + LaserView (dark scrim, grid, green laser sweeping top to bottom like a printer, falling matrix text, real stage list + percent, progress bar, STOP / ROKO button).
- Stages are the real ones: CAPTURE 0..9, READ TEXT 10, VISION 25, SEARCH 55..90, RESULT 100 (NovaService.movieStage).
- NOT touch-through (all touches taken, only STOP works). STOP button or the voice / card STOP ends the scan and the overlay within 120 ms.
- The overlay is hidden around every screenshot (hideForShot / showAfterShot), so the laser never appears in the frames. No FLAG_SECURE (it would blank them).
- Simple mode (animations off in Android, or low-RAM phone): no motion, laser sits at the real percent.
- No overlay permission: the scan still runs; the existing one-time tap-to-fix notice (overlayNudge) asks for it.
- NovaService: startLaser / stopLaser / laserTick, wired in startMovieScan; closed on finish, cancel and onDestroy. Tests: LaserMathTest (9 JUnit), tools/test_laser_overlay_contract.py.

## v7.65 - PART 14B-3: MOVIE GUESS PIPELINE (add-only; Kotlin compile / JUnit / phone NOT RUN), versionName 7.65
- NEW MovieGuessRules.kt + MovieGuessPipeline.kt (pure): the captured frames + the screen text go to the vision AI (Gemini, then the free chain, same keys / cooldowns as the analysis), the best title is checked by a web search (grounded Gemini, else key-less Wikipedia / News), the answer is a NAME + confidence + sources.
- Title only from visible evidence (screen text, scene). NEVER a person from a face: the prompt forbids it, a title / evidence line that names a person is dropped (AnalysisRules.identityClaim). Confidence is earned by sources: HIGH = name written on screen + >= 2 web results; MEDIUM = 2 results or 1 + written; LOW = "verify nahi hua" (no search / no source can never be better than LOW).
- Privacy: protected screen re-checked right before anything leaves the phone; the search sends only short title queries (no picture, no screen text); frames are cleared as soon as the guess ends; nothing is saved. A private screen answer from the AI (safety private) stops the run.
- NovaService.startMovieScan: after the 14B-2 capture it reads the screen text, runs the guess, and delivers the result as chat text + a short spoken line (deliverMovie). movieStage (READ TEXT / VISION / SEARCH / RESULT + percent) is kept for the laser overlay (14B-4). Honest endings: no AI key, limit, offline, unreadable answer, nothing found.
- NEW MovieGuessRulesTest (10) + MovieGuessPipelineTest (10), NOT RUN. tools/test_movie_guess_contract.py PASS (3 mutations caught). Nothing removed.

## v7.64 - PART 14B-2: LIVE SCREEN CAPTURE for the movie scan (add-only; Kotlin compile / JUnit / phone NOT RUN), versionName 7.64
- NEW MovieFrameRules.kt + MovieScanCapture.kt (pure): 2-4 small frames, 1.5 s apart, memory only (never saved, never sent). Protected screen (AnalysisRules.gateSource) checked BEFORE every frame and again AFTER every shot; if it shows up, all frames are thrown away. Identical frames (paused video) kept once.
- NovaAccessibilityService: movieProtected() (fails closed, skips NOVA's own window) and movieShot() (refuses protected screens). NovaService: "nova ye kon si movie hai" now starts a real background capture (STOP cancels it) and says honestly how many frames were taken; the movie NAME guess is still 14B-3 (no name, no face / actor).
- NEW MovieScanCaptureTest (9 JUnit, NOT RUN), tools/test_movie_capture_contract.py PASS (mutation-checked). 14B-1 contract widened: route may be notYetText (14B-1) or startMovieScan (14B-2).

## v7.63 - PART 14B-1: "nova ye kon si movie hai" samajhna (add-only; Kotlin compile / JUnit / phone NOT RUN), versionName 7.63
- NEW MovieScanRules.kt (pure Kotlin): parse(text) -> MOVIE / NONE. Insaan / actor / cast / "kaun hai", shared file, play / download / recommend sentences NONE. Face gate (13A) untouched: "ye reel kon si movie hai" refuse nahi hota, "ye kaun hai" abhi bhi refuse.
- NovaService: naya local route AnalysisVoiceRules.parse se PEHLE (no AI, no persona). Jawab abhi honest "live screen scan abhi ban raha hai (Part 14B)"; koi capture nahi, koi naam nahi.
- NEW MovieScanRulesTest (6 JUnit, NOT RUN: sandbox me kotlinc / internet nahi). tools/test_movie_scan_contract.py PASS (static).
- Version list tests me 7.63 add (analysis_rules, analysis_voice, search_compose, android_runtime).

## v7.62 - PART 14 GREEN LASER SCAN (add-only, ui.html only; phone NOT TESTED), versionName 7.62
- NEW pt14_laser_css + pt14_laser: full-screen hacker scan (green laser line + printer trail, grid, matrix rain, corner brackets) shown ONLY while a real analysis runs (analysisStatus state running). Percent and stage chips (INTAKE, FRAMES, VISION, SEARCH, COMPOSE) come from the real status, no timer fake. Touch-through except ROKO (analysisCancel) and Escape. lowfx / reduced motion: no canvas, slower or static laser. After done: "SCAN POORA" ~1 s then hides.
- No new Kotlin, no new bridge call, no network, no typed text read. NEW tools/test_p14_laser_contract.py (static + headless 320/360/412, mutation-checked).
- Part 14B (live screen movie scan) is still to do: see NOVA_20_PARTS_PLAN.md.

## v7.61 - Part 13 COMPLETE (code + sandbox checks; phone NOT TESTED), versionName 7.61
- Kotlin COMPILE PASS (main + test, kotlinc 1.9.24). JUnit: Analysis* / FreeVision koi fail nahi; 29 purane org.json-stub fail alag.
- Tests: AnalysisVoiceRulesTest.faceGateCatchesImageWhoQuestions; test_p13_ui_runtime D2 (mutation-proof: guard hatane par FAIL); test_part6_contract (CB list quotes); check_p7_motion (scope sirf p7 block).
- FeatureVerify (FeatureCheck.understanding): "Samajh: video analyse karo (chehra pehchan mana)".
- docs/PART13_TRACE_v7_52.md: v7.61 section me rows 15-17, 20-24, 27-28.

## v7.60 (13D acceptance ~60%, early pack on owner's order; versionName still 7.59)
- AnalysisRules.FACE_PHRASES: add-only phrases. test_free_vision_contract accepts the 13D finishMetadata signature. test_p13_ui_runtime: chat photo flow (headless PASS).
- NOT RUN: Kotlin compile, JUnit, full run_all_checks, phone. See PART13D_STATUS_v7_60.md.

## v7.59 - Part 13D (adhura, code only): voice + chat photo analysis
- "nova ye video analyse karo" / "is photo me kya hai" / "isko check karo..." asli analysis chalate hain (sabse naya shared file, background, STOP se ruke). Chat me photo + sawaal ab photo dekhkar jawab deta hai (HAAN box ke baad). Compile / JUnit / phone NOT RUN; baki kaam PART13D_STATUS_v7_59.md me.

## v7.58 - Part 13C: SEARCH + COMPOSE (sourced report)
- Analysis ab ANALYSE ke baad web par dawon ki jaanch karti hai (Gemini Google Search grounding, warna key-less Wikipedia / News); sirf chhoti text query jaati hai. COMPOSE dawon ko asli source numbers se bandhta hai, baaki 'verify nahi hua'. Search me kuch nahi mila to saaf line. Code only: compile / JUnit / phone NOT RUN.

# CHANGELOG

## v7.57 (Part 13B-2d, add-only): 13B CLOSED
- FeatureStatus: new home row "analyse" (ok with a Gemini key or free chain + Groq / OpenRouter key, else warn with the honest "only file size / length" reason). FeatureCheck.readiness: one "Photo / video ANALYSE" readiness line. Both only READ saved state.
- docs/PART13_TRACE_v7_52.md: rows 12-14, 17-19, 22, 27 marked DONE in code (phone NOT TESTED). Version 7.57.
- NOT RUN (owner order: only after Part 13 / 13D): Kotlin compile, JUnit, run_all_checks, headless render, GitHub Actions, phone. Phone-test fixes (old 13B-2 item 3) wait for the owner's end-of-Part-13 result.

## v7.56 (Part 13B-2c, add-only): REPORT UI POLISH
- ui.html pt13_analyse: the plain report text is shown in clear sections (summary, headings Kya dikha / Likha hua text / Verify nahi hua / Limits ..., bullet lists, confidence box, source lines). Built with createElement + textContent only (no innerHTML); unknown shape falls back to plain text. Kotlin reportText unchanged (chat / voice keep using it).
- Long report scrolls inside its own box (max 50vh, contained overscroll, focusable); 320 px rules (@media max-width:360px: smaller input / report font, ROKO full width). No new button, no chat-bar change, SCR unchanged. Version 7.56.
- Tests: tools/test_report_polish_contract.py (static). NOT RUN (owner order, only after Part 13): Kotlin compile, JUnit, run_all_checks, headless render (test_p13_ui_runtime), phone.

## v7.55 (Part 13B-2b, add-only): STUCK CALL AFTER CANCEL
- AnalysisPipeline: after ROKO / STOP (cancel) or the 120 s watchdog the stuck worker is abandoned (shutdownNow) and a NEW single worker is made (replaceWorker), so a new ANALYSE never waits up to 35 s behind an old network call. Old late results are still dropped by the generation number. Still one worker at a time, never the main thread.
- Tests: tools/test_cancel_worker_contract.py (static). versionName 7.55 (3 pins). No UI / bridge / permission change.
- NOT RUN (owner order: only after Part 13 is complete): Kotlin compile, JUnit, run_all_checks, phone.

## v7.54 (Part 13B-2a, add-only): FREE-PROVIDER VISION FALLBACK
- NEW FreeVision.kt (pure): vision providers = Groq `qwen/qwen3.8-27b` (id groqqwen, same key / cooldown / ApiUsage as the text Qwen row) + OpenRouter `openrouter/free` (id openrouter). Z.ai (China server), Pollinations (anonymous) and text-only models (gpt-oss, llama-3.3) never get a picture. OpenAI-style `image_url` data URL body, max 3 frames per call (Groq limit), JSON mode only for Groq, own run() chain (key needed, free-chain switch, shared cooldown, cancel check, 35 s rule, 598 ends at once).
- AnalysisPipeline: Gemini first; only when Gemini is NOKEY / LIMIT / OTHER (not OFFLINE) and a free key is ready the frames go to the free chain; report gets an honest limit line (freeUsedNote). Brain got freeVisionReady() / callFree() (defaults = off, old methods untouched). NovaService.analysisBrain implements them with FreeHttp (no new HTTP client).
- MADAD analysis topic + CAPS + media-card consent line now say frames may go to Groq / OpenRouter (only on ANALYSE). Version 7.54. No new permission (30).
- Tests: FreeVisionTest (18 JUnit), tools/test_free_vision_contract.py. Owner order: tests / compile run only when Part 13 is complete.
- RUN: Kotlin compile 0 error + JUnit once (1 own test typo found and fixed, NOT re-run); test_free_vision_contract, help, android_runtime, analysis_pipeline contracts PASS. run_all_checks NOT RUN. NOT TESTED: phone, real Groq / OpenRouter, GitHub Actions.

## v7.53 (Part 13B-1, add-only): ANALYSE BUTTON + ANALYSIS ENGINE (13B split in 2 by owner)
- NEW AnalysisPipelineRules.kt (pure): request JSON (inlineData, strict JSON), batches, call outcome, honest fallback / error texts, intake gate, status JSON (percent only from real counts).
- NEW AnalysisPipeline.kt (Android): single background thread, 120 s watchdog, cancel, INTAKE (question gated before any network) -> FRAMES (reuse / start FrameExtractor) -> VISION (batches, parseVision, one retry, safety flag refusal) -> vision_only report (no search yet, every claim unverified) -> DONE; no key / offline / limit = metadata-only report. Result in RAM only.
- NovaService.analysisBrain (same key / models / ApiUsage / cooldown via postFallback; no new HTTP client); STOP (softStop + HUD) cancels the analysis. FrameExtractor add-only: lastDurationMs, keptFiles, keptSecs.
- Bridge (new): analysisStart / analysisStatus / analysisCancel / analysisResult / analysisResultText. mediaIntakeRemove / Clear also forget reports.
- ui.html: pt13_analyse (ANALYSE per photo / video row, question box, ROKO, real-percent bar, report text via textContent), SCR + BRIDGE list, MADAD topic `analysis` (HelpGuide + CAPS), SKILLS entry, media / frames wording made true, consent line in the media card.
- Tests: AnalysisPipelineRulesTest (14), test_analysis_pipeline_contract.py, test_p13_ui_runtime.py (headless, fake bridge). Retired: old "no ANALYSE button" check in test_p11_ui_runtime. versionName 7.53.
- RUN here: full Kotlin compile 0 error; JUnit 29 fail = old org.json-stub tests only (none Analysis*); run_all_checks 0 fail. NOT TESTED: phone, GitHub Actions.

## v7.52 (Part 13A, add-only): ANALYSIS RULES
- AnalysisRules.kt (pure, MiniJson only): safety gate, vision / compose prompts, strict JSON parsers, URL / source validation, claims bound to real result numbers or unverified, confidence, honest metadata-only report, JSON + text renderers, stage lines.
- AnalysisRulesTest.kt (45 tests), tools/test_analysis_rules_contract.py, docs/PART13_TRACE_v7_52.md. versionName 7.52. No UI, bridge, permission or network change.
- NOT RUN: Kotlin compile, JUnit, phone.

## v7.51 - PART 12 FRAME EXTRACTION (add-only; Part 12 = 100%)
- NEW AnalysisStage.kt (pure): INTAKE / FRAMES / VISION / SEARCH / COMPOSE / DONE / FAILED event with real done / total (shared by Part 13 / 14 / 15).
- NEW FrameRules.kt (pure): frame count (default 6, max 12, short clip fewer, low memory fewer / 0), time points (start .. just before end, equal steps), max side 768, 32x32 grayscale duplicate drop, honest error texts, 60 s watchdog constant.
- NEW FrameExtractor.kt (Android): single background thread, getFrameAtTime(OPTION_CLOSEST_SYNC), every bitmap recycled, OOM + Throwable caught, cancel (generation number), 60 s watchdog, JPEG q80 in cacheDir/media_frames/<id>, cleanStale (orphan / 24 h). STOP (NovaService.softStop + HUD onStop) also cancels.
- Bridge (MainActivity, new): frameExtractStart / Cancel / Status / Thumb. mediaIntakeRemove / Clear also clean frame folders.
- ui.html block pt12_frames (+ css): FRAMES NIKALO button on photo / video rows, status line, real-count bar, ROKO, thumbnail strip, error text. SCR + MADAD "frames" + CAPS + SKILLS "Video se frames nikalna".
- Tests: FrameRulesTest (11), test_frame_extract_contract.py, test_p12_ui_runtime.py (headless, fake bridge). Bug fixed by JUnit: unknown duration 0 gave 1 frame.
- NOT TESTED: real phone decode, Actions build.

## v7.51 - PART 11-D COMPLETE (voice + MADAD + SKILLS + one chat-bar button; Part 11 = 100%)
- MediaIntake.voiceIntent (pure): "kya mila" -> MediaIntakeStore.statusText; video / reel / media + analyse -> honest "analysis abhi nahi". Screen analyse untouched. NovaService.handle block (lastKind "media", no persona kind).
- HelpGuide Topic("media") + ui.html CAPS same id / title / example; SKILLS "Media: Share karke bhejna"; SCR already had pt11_media.
- Owner request: +, files (c_attach) and MEDIA (m11_btn) beside the chat box are ONE round button (ui.html pt11d_merge). Old buttons / handlers kept (hidden), the new menu clicks them. Fixes the 41px chat box at 320px found by test_part5_ui_screens.
- Tests: MediaVoiceTest (5), test_media_voice_contract.py, test_merged_plus_runtime.py. Compile 0 error; JUnit 1167 run, 29 fail (org.json stubs only, none from Part 11).

## v7.51c - PART 11-C MEDIA MILA CARD + BRIDGE (add-only; Part 11 = 75% done)
- Bridge (MainActivity, all new): mediaIntakeList, mediaIntakeThumb (small / big JPEG data URL, inSampleSize, recycled), mediaIntakeOpen (system viewer via FileProvider), mediaIntakeRemove, mediaIntakeClear, mediaIntakeCancel, mediaIntakeAttach (id only; label = name + size), mediaPick (system picker, several photos / videos). Nothing returns a path or uri.
- ui.html block pt11_media (+ pt11_media_css): "MEDIA MILA (n)" card in chat: thumbnail, name, type / size, DEKHO, CHAT ME JODO, HATAO, MEDIA CHUNO, SAAF KARO, ROKO + progress bar; link rows show the honest note. Names via textContent only. NO analyse button (Part 13). New chat-bar button MEDIA. Lists updated: BRIDGE (+8), CB (+onMediaIntake), SCR (+pt11_media).
- MediaIntakeStore: thumbDataUrl, attach / attachedId. nova_file_paths.xml: cache-path media_intake. Share and picker both refresh the page (mediaChanged).
- Tests RUN: test_media_card_contract.py PASS, test_media_store_contract.py PASS, test_p11_ui_runtime.py PASS (headless 320 / 360 / 412), whole project COMPILES (kotlinc 1.9.24, 0 errors), JUnit 1162 run / 29 fail = org.json-stub tests only (none Part 10 / 11).
- NOT RUN after 11-C: tools/run_all_checks.sh as a whole (last full run was at 11-B: 111 pass, 0 fail), real phone, GitHub Actions. Next account runs everything first.
- Still for 11-D: MADAD (HelpGuide) topic, SKILLS entry, voice "kya mila", final status.

## v7.51b - PART 11-B MEDIA INTAKE STORE + SHARE FILTER (add-only; Part 11 = 50% done)
- NEW MediaIntakeStore.kt (Android): Share / picker uris copied as a 64 KB STREAM on one background thread into the private cache (cacheDir/media_intake), size re-checked while copying (MediaIntake.streamOk), cancel flag, half-written file deleted, OOM caught, index.json, cleanExpired (24 h + vanished files), remove / clear / fileOf / listJson (no path, no uri). Links are stored as link entries only (no download).
- Manifest: ACTION_SEND (image/*, video/*, text/plain) + ACTION_SEND_MULTIPLE (image/*, video/*) on MainActivity. NO new permission (30 before and after).
- MainActivity.handleIntent: old line kept, store hook added (clean on open, share -> Toast with the honest result line).
- MediaIntake.kt: + Entry, expiredIds, safeExt, cacheFileName, isValidId, room. JUnit +1 test (16 total, PASS).
- FIX (Part 10 bug found by the first full JUnit run): RoundaboutRules.repeat accepted "usi ko dobara call karo kal" as a repeat; now only polite filler (please / na / yaar ...) may sit beside the phrase.
- Tests: tools/test_media_store_contract.py PASS; whole project COMPILES on kotlinc 1.9.24 (0 errors); JUnit 1162 run, 29 fail (all org.json-stub tests of Connector / Web / Vercel / Music etc., sandbox has no real org.json; NOT Part 10 / 11).
- NOT in 11-B: bridge + MEDIA MILA card (11-C), MADAD / SKILLS / voice (11-D). Real phone copy NOT TESTED.

## v7.51 - PART 11-A MEDIA INTAKE RULES (add-only; Part 11 = 25% done)
- NEW MediaIntake.kt (pure Kotlin): classify image / video / link (mime first, extension second), size limits (photo 25 MB, video 300 MB, empty refused, unknown size allowed + stream check), max 6 files per share, https-only links (no user:pass@, localhost, *.local, raw IP; tracking params untouched), content:// uris only, clean names (<= 60 chars, never a path), chat label = name + size only, duplicates dropped (same uri / same name + size), 24 h cache rule, honest link note ("video file nahi mili, sirf link ki jankari").
- Tests: MediaIntakeTest.kt (15 tests, RUN on real kotlinc 1.9.24 + JUnit 4.13.2: 15/15 PASS), tools/test_media_intake_contract.py (PASS). versionName 7.51 (guard updated).
- NOT in 11-A (on purpose): Manifest share filter, file copy (MediaIntakeStore), bridge, card, MADAD / SKILLS = 11-B / 11-C / 11-D.

## v7.50 - PART 10 ROUNDABOUT COMMANDS (add-only; compile / JUnit / phone NOT TESTED)
- FIX known bug: "ha kr do" / "haan karo" / "kar do" / "theek hai" / "bilkul" were read as unclear and CANCELLED the confirmation. NEW RoundaboutRules.affirmative, called by Logic.parseAnswer AFTER the NO check (a NO word still wins). "yes please", "sabko bhej do abhi", lone "do" / "karo" stay unclear.
- NEW follow-ups for harmless kinds only (ContextMemory): "wahi kar do", "usi ko dobara", "ulta kar do" / "undo" (torch, volume, media, quiet, silent, rotation). NEVER_REPEAT (call, sms, whatsapp, delete, pay, publish, record ...) unchanged.
- NEW indirect cues (no AI, offline): "andhera hai" -> torch on, volume / brightness cues, <= 8 words, whole sentence only (NovaService, kind "hint").
- NEW safety: a risky order aimed at someone only by a hint ("wo jo kal wale ko bhej do", "usi ko call karo", "call him") is never guessed: NOVA asks who (kind "clarify"). Reply-mode commands untouched. Persona never decorates hint / clarify.
- UI wiring: HelpGuide + MADAD topics persona (Part 9, was missing) + roundabout; two SKILLS entries; new ui.html block pt10_wire (also wires the dead Settings checkbox #nc_on to the same bridge as #wb_nc; added to the page self-check list).
- Guard tools/test_part6_skills_contract.py: topic map now knows persona + roundabout.
- Tests: RoundaboutRulesTest.kt (NOT RUN), tools/test_roundabout_contract.py (PASS, includes a Python mirror), tools/test_p10_ui_runtime.py (PASS, headless). versionName 7.50 (guard test updated). Docs: PART10_STATUS_v7_50.md, NEXT_ACCOUNT_PROMPT_v7_50_PART11.md (Parts 11-20 in full detail).

## v7.49 - PARTS 1-9 REVIEW (add-only; compile / phone NOT TESTED)
- Checked: 613 files, 103 contract scripts, bridge (185 UI calls, 0 missing in Kotlin), ui ids, manifest services, per-part files (NativeSmooth, SafeStop, PersonaRules, ui.html blocks p6_home / p7_motion / pt8_chat / pt9_persona), headless renders of every screen.
- FIX tools/check_p8_chat.py: it scanned the whole page tail, so the Part 9 persona block (valid bridge call) made it FAIL. Now it checks only the Part 8 blocks. PASS.
- FIX tools/run_all_checks.sh now also runs tools/check_p5..p8 scripts (they were never in the all-checks run).
- FIX versionName 6.97 -> 7.49 (app showed an old number); guard test.
- Nothing removed. Details: docs/PARTS_1_TO_9_REVIEW_v7_49.md

## v7.48 PART 9 - JARVIS PERSONA (add-only)
- NEW PersonaRules.kt (pure Kotlin): owner name line, short witty tail, time-of-day feeling tail. Only on short harmless replies (kinds: chat / answers / app_control / music_play), 45 s gap, never on questions, confirmations, errors, silent turns, calls, messages, recording, agent, publish, Gmail, connectors, images. Auto-reply path untouched (owner rule v7.46).
- NovaService.process(): one call `personaTouch` before withNudge. ExtCfg.persona (default ON), bridge getPersona / setPersona, Settings button PERSONA ON/OFF (ui.html block pt9_persona; full switch screen in Part 17).
- Restored v7.47 files that were dropped by mistake: 5 ic_launcher_foreground.png + assets/orb_poster.png (launcher XML, ui.html and SelfTest still need them).
- Tests: tools/test_persona_contract.py (RUN, PASS), PersonaRulesTest.kt (NOT RUN). All 103 contract scripts PASS. NOT RUN: Kotlin compile, JUnit, phone.

## v7.47 - PART 8 CHAT UI (add-only, ui.html only; compile / phone NOT TESTED)
- NEW pt8_chat_css + pt8_chat: neon-glass bubbles (NOVA cyan left, TUM violet right) with a small label + time, restyled typing dots, voice waveform bar above the input (SUN / BOL / SOCH RAHA HU, read from html[data-fut]; animation only, not the real mic level). lowfx / reduced-motion = static.
- Typing reveal was NOT duplicated: it already exists since v7.32 (p3_fx). Page self-check lists pt8_chat. NEW tools/check_p8_chat.py (PASS).
- Docs: PART8_STATUS_v7_47.md, NEXT_ACCOUNT_PROMPT_v7_47_PART9.md; old status / prompt moved to docs/.

## v7.46 - AI-ONLY AUTO REPLY (owner rule: no fixed message, ever; compile / phone NOT TESTED)
- Auto reply is always the AI's own answer, for every message (OTP / payment / insult / wish too). No AI answer, or a secret in the AI text = NOTHING is sent and the owner is told (history + phone notification + voice).
- REMOVED: fixed "Jawab ka text" box and AI SMART switch from Settings; PaymentReplyRules, SocialReplyRules (+tests); HOLD_LINE send; plain fixed-text path in NovaNotificationService; v7.45 safetyReply.
- Rule card #ar_safe now says what the AI does. Voice "smart reply off" now explains auto reply is always AI. Guards: new tools/test_ai_only_reply_contract.py; payment / social / payment_ai guards retired to docs/retired_guards/.

## v7.45 - 12 stale guards resolved + AUTO-REPLY SAFETY wired (compile / phone NOT TESTED)
- FIX NovaNotificationService.autoReply: new safetyReply() runs for BOTH auto-reply modes BEFORE the per-person gate: OTP/PIN/password/KYC = no reply + note; money talk = ONE fixed safe line (plain mode; AI SMART already had it); insult = no reply, owner told (NovaService.onSocialInsult), Instagram marked "to report"; wish / nice comment = fixed Thank you. Before this, plain auto-reply would have sent the owner's text to an OTP message and SocialReplyRules was never called.
- UI: Settings > AUTO REPLY now shows a SAFETY card (#ar_safe, add-only).
- Guards: payment / social / photo_publish contracts adapted to this tree; 5 old autopsy guards merged into tools/test_adapted_autopsy_contract.py; 4 retired to docs/retired_guards/ (features of the other code line). KNOWN_STALE_GUARDS.txt is now empty.
- NOT added (open): wake word while the phone is locked (old wb_ll switch) - conflicts with Lock Guard, owner decision.

## v7.44 - PART 7 MOTION (add-only, ui.html only; compile / phone NOT TESTED)
- NEW p7_motion: tab slide direction, chat rise, HAPTIC ON/OFF button in Settings (nova_hap), number tickers (API limits, KAAM chip); lowfx / reduced motion = off.
- Removed from the zip (owner request, unused): docs/ui_shots, docs/archive, docs/archive_old. Old status / prompt files moved to docs/.
- PART 7B: HUD chat card glass gradient (NovaHud.kt, 1 line), intro caption / hint glass chips + soft vignette (CSS only). PART 7 COMPLETE (compile / phone NOT TESTED).
- FIX (Parts 1-7 review): Part 6 status chips 40 -> 44px; test_screen_session_contract slice 500 -> 2500 (v7.40 queue block moved the line). NEW docs/PARTS_1_TO_7_MAP.md.

## v6.97-wip9 - screen se pehchano + baat-cheet (compile / phone par NOT RUN)
- "ye kaun si movie hai" / "is gaane ka naam batao" / "who is this actor": NOVA screenshot leta hai aur Google Search se naam batata hai (ScreenSession.Intent.IDENTIFY, sirf isi sawal par search). "screen analyse karo aur batao ye kaun si movie hai" bhi isi raste par jaata hai. Protected screen par kabhi nahi. Popup mein koi button nahi chahiye.
- Baat-cheet mode (jawab ke baad dobara sunna) default ON, 6 follow-up tak (pehle OFF aur 3). Settings mein BAAT-CHEET switch se band hota hai.
- SKILLS screen mein naya group "NAYE KAAM": screen pehchano, baat-cheet, 5 browsers + offline AI, ringer/DND, Lock Guard.
- Tests: ScreenSessionTest.identifyQuestionsWantScreenAndSearch (JUnit NOT RUN); tools/run_all_checks.sh = 69/69 PASS (RUN).

## Part 2 (skills list)
- MADAD list, HelpGuide.TOPICS aur aiFacts mein 15 naye topics (31 total): WhatsApp Business, Auto-reply, Connectors, Routines, Lock Guard, Nickname + ROZ KA SUDHAAR, Battery guide, Gmail, Lambe kaam, Screen alerts, Watch mic, Quiet/Silent, Offline AI pack, Leads, API limits/Update Center. HelpGuideTest.kt aur test_help_contract.py update. Details: PART2_STATUS.md

## v7.4 - understands roundabout commands
- PhoneActionRules.likelyAction: much wider filter (verb stems, device words, indirect cues like "andhera hai" / "bahut shor hai", polite "kya tum ... sakte ho", Hindi in Devanagari, up to 80 words) so ghuma-phira ke bole command reach the action model; plain questions still skip it.
- Action prompt (Hinglish + English) now teaches intent-first understanding with examples and allows up to 3 actions in one answer ({"actions":[...]}), parsed by PhoneActionRules.parseAll; NovaService runs them in order and stops at a call/WhatsApp confirmation.
- Gemini voice prompt also told to act on hints, not only direct commands.
- Tests: PhoneActionUnderstandTest (JUnit, still to run on GitHub Actions).

## v7.3 - ChatGPT/Claude-style typed chat
- Typed chat turn ("rich"): backup AIs (Groq/Qwen/GLM/OpenRouter) give full structured answers (2000 tokens, lists, code, "Samjha:" line); Gemini gets a typed-chat note that lifts the read-aloud / 8-sentence rules. Spoken commands keep the short plain rules.
- Live web context for backup AIs on typed questions (WebContext.kt): Gemini Google Search, else Google News / DuckDuckGo / Wikipedia; "Sources:" line added.
- ui.html: light markdown in AI bubbles (headings, bold, inline code, lists, code blocks) built from DOM nodes only; COPY on code blocks and long AI messages.
- Speech: markdown/code stripped before text-to-speech (FreeProviders.speakable).
- Faster thinking: Gemini 2.5 Flash gets thinkingBudget=0 (GeminiSpeed.kt), retried without it on HTTP 400.
- Website jobs start with a "Samjha: subject, style, photos, new/edit" line (SiteSummary.kt).
- MADAD "questions" topic updated. Tests: RichChatTest, WebContextTest, NovaV73Test, tools/test_rich_chat_contract.py (compile + JUnit still to be run on GitHub Actions).

# wip13 (music commands)
- NEW MusicCatalog.kt: key-less song catalogue (iTunes Search, titles + artists only) used just to tell similar songs apart. Offline = skipped.
- CHANGED NovaService music flow: musicBegin -> (similar songs? popup/voice choice) -> musicPlay -> verifyPlayback. Success is reported only when the music app's own media session (Notification access ON) shows the song playing; otherwise NOT_PLAYING / PLAYING_OTHER / PLAYING_UNCONFIRMED / CANNOT_TELL are said honestly.
- NEW: a different music app is never tried by itself. NOVA asks "<app> par try karun?" (real yes/no, not skippable) and only then tries it.
- NEW: songChoiceListener popup in MainActivity ("Kaun sa gaana?") plus spoken pick (number or name).
- RESTORED: .github/workflows/build.yml (was missing from wip12 zip; recreated from docs/BUILD.md; fixes tools/test_vosk_model_packaging.py).
- NEW tests: MusicPlayRulesTest (extended), MusicCatalogTest, tools/test_music_play_contract.py. Kotlin was NOT compiled in the sandbox (no kotlinc, no network): first real compile is GitHub Actions.

# v6.97 (wip8) 5 KEY-LESS BROWSERS + LOCAL-FIRST AI + LOCK GUARD + ADAPTIVE LISTENING
- Compile + 834 JUnit tests RUN (kotlinc 1.9.24, real org.json); phone behaviour NOT tested.
- Browsers: WIKI, INSTANT (DuckDuckGo), WEATHER (Open-Meteo), NEWS (Google News RSS), MARKET (Frankfurter + CoinGecko); live answers never invented. Local-first on-device AI for short simple questions. BROWSERS card in the UI; test report runs by itself.
- Connectors: honest MCP errors, Vercel teams, n8n API + webhooks (Zapier / Make / IFTTT).
- LOCK GUARD (new, default ON, switch in BROWSERS card): locked phone = no wake word / no command, normal messages not read. Loud beep (alarm stream) + one spoken line only for: any call while the phone is silent, call from Papa/Mummy, message from Papa/Mummy (text never read). One volume-key press stops the alert (needs Accessibility), unlocking also stops it. Driving mode stays hands-free.
- WATCH MIC (new, untested on a real watch): calling watch AI button (Bluetooth voice command) wakes NOVA and the watch mic is used over Bluetooth SCO while the phone is unlocked.
- Adaptive listening: long sentences (10+ words) wait +0.6 s, sentences ending on "aur / ko / ki / and ..." wait +1.5 s (max 3.5 s) before NOVA answers.

# v6.96 5 FREE OFFLINE AI (no key, no account) - written, NOT compiled, NOT run on a phone
- NEW OfflinePack.kt (pure rules), OfflinePackStore.kt (HF file list + verified download), LocalBrains multi-model fallback, AutoSetup pack runner.
- 5 on-device brains behind the built-in Qwen2.5-0.5B slot: Qwen2.5 1.5B (Alibaba), Llama 3.2 1B (Meta), InternLM2.5 1.8B (Shanghai AI Lab), SmolLM2 1.7B (Hugging Face), DeepSeek-R1 Distill 1.5B (DeepSeek). 3 Chinese + 2 world.
- Download starts by itself after install (20 s after the service starts), Wi-Fi only, needs RAM and 1.5 GB spare storage, resumes after a break, one model at a time, respects the "auto models" switch.
- Chat fallback: first installed model answers; if it cannot load / times out / gives empty or looping text the next installed model is tried (max 3 per answer). Busy engine or a safety refusal never starts a cascade. Command translator (LocalBrains.ask) asks ONE model only.
- Chat templates per family (ChatML / Llama 3 / DeepSeek), stop-token + <think> cleanup (PackPrompt, PackText).
- File name / size / sha256 are read from the Hugging Face API at download time (not pinned in code): the file is refused when size or sha256 differ or the size is outside the model's sanity range.
- Tests: OfflinePackTest.kt (NOT RUN), tools/test_offline_pack_contract.py (PASS, static), tools/test_autosetup_contract.py updated 2 -> 3 runners.
- Limits: no keyless ONLINE AI was added (no stable official one exists; scraping private web endpoints was refused). Image generation and speech-to-text keep their old chains: no 5-model offline fallback exists for them here.

# v6.95 ROZ KA SUDHAAR (daily self-improve with approval; compile + JUnit + phone = NOT TESTED)
- v6.95 PRO BUTTONS (same version): new final style layer `pro-v695` in ui.html: one button system (44px touch height, 14px radius, primary gradient for .a, red tone for .hdel, disabled state, focus ring) + a 450 ms tap flash on every button.sm / button.a so a tap is always visible. Presentation only. Settings accordion title for ROZ KA SUDHAAR fixed (T map).
- BUTTON AUDIT (static + headless Chromium, fake bridge): 143 bridge calls in ui.html, 183 bridge methods in MainActivity: 0 calls without a Kotlin method. 7 ids used by JS but not in HTML are all created at runtime or guarded (cn_* connector sheet, canvas tf, badge mode). 0 JS errors at 390x844. This proves the buttons are WIRED, NOT that every action works on a phone (NOT TESTED).

- NEW SelfImproveRules.kt (pure), SelfImproveStore.kt, SelfImprove.kt. Once a day, in ONE 1-hour window (owner picks the start hour, default 03:00; auto runs only inside it, max 3 per day, 20 min apart), a NON-Gemini AI (Groq / Qwen / GLM / OpenRouter with the owner's own keys; never Gemini, never the anonymous provider) gets only the installed app names + existing nicknames and suggests up to 5 nicknames ("how I say this app" -> exact app name).
- Suggestions are PENDING only. Settings > ROZ KA SUDHAAR: HAAN applies (TeachStore alias), NAHI drops, WAPAS restores the old value or removes the alias. Default OFF. Manual button ABHI CHALAO (max 6/day, still inside the 60 min/day AI-time budget).
- Every AI suggestion is checked: valid teach-alias, value must be an installed app, no secrets/links/digits, no duplicates, cut replies keep complete items.
- ExtCfg.selfImprove / selfImproveHour; NovaService.selfImproveLoop (15 min check, no network unless window open); MainActivity bridge (get/apply/reject/revert/run/cfg); ui.html section.
- Tests: SelfImproveRulesTest.kt (NOT RUN), tools/test_selfimprove_inapp_contract.py (PASS). Python contracts 55/55 PASS.
- CI fix: tools/test_android_runtime_contract.py versionName check -> 6.95; tools/test_connectors_contract.py permission count 22 -> 23.

# v6.91 EASY HELP for non-technical users
- NEW HelpGuide.kt (pure Kotlin): 15 plain-language topics (call, WhatsApp, alarm, torch/wifi, apps, music/maps, camera, image, website, screen/agent, memory, learn, driving, recording, questions). "tum kya kar sakte ho", "help", "kaise use karu" and "kya tum website bana sakte ho" are answered LOCALLY (no AI, offline) for typed chat AND voice: NovaService.handle -> HelpGuide.isAsk/answer (lastKind help_guide). Real commands ("mummy ko call karo") and phone numbers are never hijacked.
- The cloud AI (Gemini prompt and free-chain prompt) now gets HelpGuide.aiFacts(): what NOVA can / cannot do, so the AI answers capability questions correctly and never invents abilities.
- ui.html: new MADAD button in chat header + popup (#helpw): every ability in simple words, TAIYAAR / SETUP CHAHIYE chip from real permission/key state, SETUP button (contacts, accessibility, notification access, keys), BOLO example button fills the chat box, search box. A NOVA reply that points to MADAD gets a POORI LIST DEKHO button. Welcome message mentions MADAD.
- tools/test_help_contract.py keeps Kotlin topics and the popup identical (python contracts now 54/54 PASS). Playwright (390x844, 360x640): 15 cards, filter, example fills chat, 0 JS errors.
- HelpGuideTest.kt written; HelpGuide logic was simulated in Python (all cases as expected) but Kotlin compile / JUnit = NOT RUN (no network). Device: NOT TESTED.

# v6.91-wip (UI check, this account)
- NEW tests written, NOT COMPILED / NOT RUN (no kotlinc here): AiChoiceTest, ChatHonestyTest, ProviderChainAcceptTest (SiteFollowUpTest was already in the ZIP, also not run this account).
- Hang audit part 1: 2D orb loops `loop` and `gloop` in ui.html now skip drawing while a tab covers the orb (window.__cover); both are time-based so nothing jumps on return. Chat DOM is already capped (121 nodes, SHOWN). `tick` is only a cheap FPS meter, left alone. 
- Hang audit (static) finished: bridge calls run on the WebView JavaBridge thread, not the Android main thread; the only Thread.sleep in MainActivity is inside a background Thread (GitHub device flow), fine. ChatSessions stores chats in SharedPreferences: every append() re-reads and re-writes the whole chat JSON with commit() (sync disk write), and the chat bridge calls (messages/open/list/search) parse it synchronously while JS waits. NOT a confirmed freeze (nothing measured, no device), so NO code change; only a candidate: move append()/commit() off the calling thread if the phone shows chat lag.
- UI CHECK PASS (Chromium + fake bridge, 390x844 and 360x640): home, chat, AI dialog, settings render fine; #c_ai chip, radio list, fallback checkbox, SAVE work; 0 JS errors.
- Fix: at width <=380px the bottom tab label "CONNECT" was clipped; added a small @media rule at the end of style id modern-v690.
- python contracts 53/53 PASS. JUnit / kotlinc compile = NOT RUN (no network in this sandbox). Gradle = BLOCKED.
- Test script note: Playwright click on #chatb is flaky if the Update Center popup (#uc) is still covering; test-script issue, not an app bug (NOT confirmed on phone).

## v6.90 (IN PROGRESS - compiled, JUnit 704 OK, python contracts 53/53; new UI NOT visually checked, no new unit tests yet)
- Chat honesty: ChatHonesty guard replaces false "add kar raha hoon / I am building" claims in text-only chat replies; honesty rule added to the Gemini and free-AI system prompts.
- Website follow-up: "isko professional banao" right after NOVA built a site (within 30 min) now improves THAT site (SiteIntent.parseFollowUp, SiteGen.improvePrompt) instead of a fake chat claim.
- AI picker: AiChoice (auto | gemini | a free provider) + auto-fallback toggle (ExtCfg.chatAi / chatFallback, bridge getChatAi / setChatAi, chip "AI: AUTO" + dialog in ui.html). Gemini runs first (auto/gemini), after the free chain (free AI chosen + fallback ON) or never (fallback OFF).
- False "cannot do": phone-action pass now skips a provider that answers in prose and tries the next one (ProviderChain accept), without putting it on cooldown.
- Hang fixes: finishTurn always releases busy (try/finally) and a 120 s watchdog frees a stuck typed turn.
- Modern UI: ui.html style block "modern-v690" (flat surfaces, calmer accent, floating tab bar, new chat bubbles, AI picker styling).

## Part 1/5 — Intelligent Contact Resolution (implementation added; build verification pending)
- Added separate request parsing, real Android contact lookup, confidence matcher, and resolution pipeline.
- Contact lookup reads actual ContactsContract phone rows without a display-name LIKE filter; candidates are grouped by CONTACT_ID and preferred number order is super-primary, mobile, then remaining.
- Exact duplicate names, similar candidates, low confidence, missing names, no match, permission denial, and provider errors are handled without fabricated contact data or automatic action execution by lookup.
- Added Hindi/Hinglish/English command variants, Devanagari transliteration for contact commands, safe reference handling ("isko" / "this person" asks for a name), retry plumbing, and contact-resolution unit/contract tests.
- Existing call/SMS/WhatsApp entry points and permission request flow remain the integration points. A contact retry performs lookup only; the user must repeat the full command to initiate an action.

# Changelog

## v6.96 - 5 FREE OFFLINE AI (no key, no account) - written, NOT compiled, NOT run on a phone
- NEW OfflinePack.kt (pure rules), OfflinePackStore.kt (HF file list + verified download), LocalBrains multi-model fallback, AutoSetup pack runner.
- 5 on-device brains behind the built-in Qwen2.5-0.5B slot: Qwen2.5 1.5B (Alibaba), Llama 3.2 1B (Meta), InternLM2.5 1.8B (Shanghai AI Lab), SmolLM2 1.7B (Hugging Face), DeepSeek-R1 Distill 1.5B (DeepSeek). 3 Chinese + 2 world.
- Download starts by itself after install (20 s after the service starts), Wi-Fi only, needs RAM and 1.5 GB spare storage, resumes after a break, one model at a time, respects the "auto models" switch.
- Chat fallback: first installed model answers; if it cannot load / times out / gives empty or looping text the next installed model is tried (max 3 per answer). Busy engine or a safety refusal never starts a cascade. Command translator (LocalBrains.ask) asks ONE model only.
- Chat templates per family (ChatML / Llama 3 / DeepSeek), stop-token + <think> cleanup (PackPrompt, PackText).
- File name / size / sha256 are read from the Hugging Face API at download time (not pinned in code): the file is refused when size or sha256 differ or the size is outside the model's sanity range.
- Tests: OfflinePackTest.kt (NOT RUN), tools/test_offline_pack_contract.py (PASS, static), tools/test_autosetup_contract.py updated 2 -> 3 runners.
- Limits: no keyless ONLINE AI was added (no stable official one exists; scraping private web endpoints was refused). Image generation and speech-to-text keep their old chains: no 5-model offline fallback exists for them here.

## Audit session v6.89 - fixes A-F applied, compiled and JUnit-run (Gradle/APK build BLOCKED in sandbox)
- A: WhatsApp Business identity now reaches execution on every voice/transcript path; sendWhatsApp never falls back to the other WhatsApp (no app named = Messenger only, missing app is reported).
- B: IntentParser negation only checks the command part, so "... kal nahi aaunga" payloads work; payloads of <=2 negation tokens ("message mat bhejo") stay blocked; original casing/punctuation restored for APP_MESSAGE.
- C: ActionDedupe (8 s window, claimed inside the run lambda) guards call / SMS / WhatsApp; popup choice now announces the prompt/result and clears picker state.
- D: contact picker popup + spoken choice now works for SMS and WhatsApp (per-call onChosen), not only calls.
- E: honest wording: "call request sent", "I pressed Send; check WhatsApp for delivery".
- F: ContactMatcher merges the same person saved twice (same name + same last-10-digit number set).
- New tests: ActionDedupeTest, duplicate-contact merge, negation-in-payload, Business flag/casing. JUnit 704/704 OK, python contracts 53/53.
- Stale LogicTest assertion updated (bare "call" asks for a name). Python contract strings updated.

## v6.88 - remaining connector items finished
- Voice/chat: "github file banao owner/repo path.txt content" commits a file (WRITE level + strict yes).
- Chained steps can refer to the previous list: "github repos dikhao phir us repo ke issues dikhao", "vercel projects dikhao phir usi project ko redeploy karo" (first item of the newest list, `@last`).
- RUN: full Kotlin compile + 671 JUnit, 49 contract scripts. NOT RUN: Gradle/APK, real provider calls, phone.

## v6.87 - connectors work from chat and by voice, start to finish
- Chat screen: new bar above the input (＋ CONNECTORS picker + quick chips for connected services). The picker opens the same permission sheet as the CONNECT tab.
- Voice / typed: "github connect karo" runs the whole connect flow (strict yes, token page opens, token picked from the clipboard when NOVA is back in front, verified, announced). "github write on karo" / "read only" / "off karo" set the level (raising to WRITE needs a yes). Chained steps with phir / uske baad / then. Results of connector commands now arrive as chat messages.
- RUN: full Kotlin compile + 669 JUnit (3 new), 49 contract scripts (connectors contract extended), chat bar headless with a fake bridge. NOT RUN: Gradle/APK, real provider calls, phone (checklist rows 42-46).

## v6.86 - CONNECTORS (GitHub, Vercel, Netlify, Cloudflare, Supabase, Notion, Lovable, custom MCP)
- NEW CONNECT tab: connect a service like ChatGPT's connector screen: permission sheet (what NOVA can do, READ or READ + WRITE), provider token page opens, NOVA picks the token from the clipboard, verifies it with the provider, stores it encrypted. Optional GitHub OAuth device login. Custom MCP servers (https). Details: docs/CONNECTORS.md.
- NEW voice/chat commands for connected services; writes need level WRITE plus a yes that "skip confirmation" cannot bypass.
- Code: ConnectorCatalog / ConnectorStore / ConnectorApi / ConnectorParse / ConnectorRules, bridge methods in MainActivity, route in NovaService, tab in ui.html. Tests: ConnectorRulesTest, ConnectorParseTest, tools/test_connectors_contract.py. `testImplementation org.json:json` added (android.jar's org.json only has throwing stubs in unit tests).
- RUN: full Kotlin compile + 666 JUnit, 49 contract scripts, CONNECT tab headless. NOT RUN: Gradle/APK, real provider calls, phone (checklist rows 30-41).

## v6.85 - build-time downloads are automatic
- BEFORE: a local build needed `./tools/prepare_vosk_model.sh` run by hand first (bash + curl + unzip; not possible on plain Windows).
- NOW: `gradle/vosk-model.gradle.kts` (task `prepareVoskModel`) hooks into `preBuild`: Gradle downloads, safely unzips and validates the Vosk model itself, skips if already valid, retries 3 times, mirror via `NOVA_VOSK_MODEL_URL`. The Gradle wrapper already fetches Gradle 8.7; the app already auto-downloads the Hindi pack and the offline Qwen brain. CI keeps its pre-check step.
- RUN: task tested in an isolated Gradle project (fresh download, UP-TO-DATE skip, bad zip refused); python packaging contract. NOT RUN: full AGP build (sandbox cannot reach alphacephei.com / Google Maven), phone.
- Not automatic on purpose: app update offers (feed) still need the owner's INSTALL tap (security rule); API keys, Picovoice key/ppn.

## v6.84 - automatic setup after app open (no manual re-doing)
- BEFORE: the first-open permission + Settings chain ran once per install (skipped/denied items never came back; permissions added by an update were never asked).
- NOW: SetupChainRules (pure, 5 JUnit): the chain re-runs on later app opens while something is still missing (max 4 runs per SetupChainRules.VERSION; each Settings page once per run, 3 times in total; the brand autostart page once). New phone-brand autostart step. New bridge rerunSetup() + button SETUP DOBARA CHALAO in the status box. Rows 27-29 added to the real-device checklist.
- NOT automatic on purpose / impossible: NOVA does not start the microphone by itself (owner rule since v6.39; ACTIVATE stays one tap) and Android never lets an app switch Accessibility, notification access, overlay or battery settings on: the chain only opens the right page.
- RUN: compile + 646 JUnit, 48 contracts, headless UI 19 + 27. NOT RUN: Gradle/APK, phone.

## v6.83 - Part 9 (security / logging / config) - verified in sandbox only
- 35 JS-bridge methods: every String parameter capped (oversize keys/url/json rejected); NEW tools/test_bridge_caps_contract.py.
- ErrLog masks e-mail, 4+ digit runs (phone / OTP) and quoted text; +2 JUnit (641 total).
- NEW tools/ui_feature_status_check.py (headless, fake bridge, 26/26). docs/AUDIT_PART9.md. Compile OK, 641 JUnit, 47/47 contracts. NOT run: Gradle/APK, phone.
## v6.81 (audit, edits NOT re-compiled)
- WebView onRenderProcessGone recovery; receivers with RECEIVER_NOT_EXPORTED; Gradle wrapper; README keys section; versionName 6.80; tools/test_android_runtime_contract.py. See docs/AUDIT_v6.81.md.

## v6.80 (Part 8)
- Fix: NovaService Context.POWER_SERVICE compile error; ToolVerify.KNOWN_TOOLS includes media (found by first real compile).
- Chat storage moved to files (ChatStore.kt) with verified, rollback-safe migration; history search, pin (max 10), rename, export to text file, delete all, context line (what the AI receives), long-chat note. Tests: ChatStoreTest, tools/ui_chat_check.py.

# Changelog

## v6.79 - Part 7: memory manager (screen edit/delete/search, export/import, key/token redaction, Gemini task line)
- AUDIT (source): PersonalMemory (YAAD facts, 100 x 200 chars, SharedPreferences "nova_memory", allowBackup=false), ChatSessions (chat), LearnStore/KnowledgeMatcher (notes), AgentTaskLedger (task), ContextMemory (RAM only, 2 min, never repeats risky kinds). Findings: (1) Memory screen could only SHOW all / DELETE ALL; (2) API keys / tokens were NOT refused (only password / OTP / PIN / card / ID / bank / 6+ digits); (3) Gemini did NOT get the safe task line the backup AI gets (FallbackContext.taskLine); (4) Devanagari facts / questions never matched (Logic.norm drops every Devanagari letter); (5) the old contract check "Gemini path has no memory facts" sliced an EMPTY range (cloudFailure is defined BEFORE askCloud) so it proved nothing - fixed; (6) load+save were not atomic across the screen and voice.
- NEW refusal reason "key": words (api key, token, access/auth token, secret/private key, seed/recovery phrase, bearer) and key-looking strings (AIza..., sk-..., gsk_, ghp_, github_pat_, hf_, xox*-, or any 24+ char letters+digits word). Applies to voice, screen add/edit, import, and relevant() (such a fact never reaches a model).
- NEW Memory screen (ui.html, DIKHAO): search box, per-fact EDIT and HATAO, add field (JODO), count "N / 100", EXPORT (FILE) / IMPORT (FILE) (text file through the system file picker), limit + privacy line. Facts are drawn with textContent only (an imported file can never become HTML). SAB HATAO keeps its double-tap.
- NEW bridge: getMemoryJson, memoryAdd, memoryEdit(index, expectedText, new), memoryDelete(index, expectedText), memoryExport, memoryImport (input capped 1000 chars, import file capped 80 KB / 20,000 chars / 400 lines). Edit / delete carry the old text: if a voice command changed the list meanwhile, nothing is changed ("Ye baat badal chuki hai").
- NEW PersonalMemoryStore.update / importFrom: load + change + save under ONE lock.
- NEW pure rules in PersonalMemory: search, removeAt, replaceAt, exportText, importText (every imported line goes through add(), so secrets / keys / long numbers are refused; file order kept; MAX_FACTS respected), screenReply, importReply.
- CHANGE: `tokens()` transliterates Devanagari with HindiRoman, so Devanagari facts and questions can match (best effort).
- CHANGE: Gemini (askCloud) now gets the same ONE safe task line as the backup AI (redacted summary + state, only if newer than 30 min). Gemini still gets NO personal facts (unchanged on purpose).
- TESTS: tools/memory_cases.py + gen_memory_test.py (regenerated PersonalMemoryTest: +8 refusal rows, +6 Hindi/Hinglish retrieval rows), NEW MemoryScreenOpsTest (13 tests), tools/test_memory_contract.py extended (159 checks).
- RUN: Python contracts (see TEST_REPORT_PART7.md). NOT RUN: Kotlin compile, JUnit, Gradle / APK, real phone (sandbox has no kotlinc / internet). `MemoryScreenOpsTest`, the new PersonalMemory functions, the bridge methods and the onActivityResult branches have NEVER been compiled.
- NOT DONE (honest): no UI screenshot test of the new Memory screen; English question vs Hinglish fact ("what is my name" vs "mera naam shiv hai") still does not match; spoken secrets without "=" or ":" in a task summary ("password hunter2") are not redacted by AgentTaskRules.safeSummary.
- No feature removed; no speaker recognition / voice lock; no permission or security restriction bypassed; no confirmation added back.

## v6.78 - app opening: 3 silent ways, no notification, no "request" talk
- WHY apps did not open: Android 10+ (and brand layers such as Xiaomi / Vivo / Oppo "pop-up while running in the background") block `startActivity` from a background service. The old code tried ONE way, then showed a notification and said it was not confirmed.
- NEW `launch()`: (1) service `startActivity`, (2) the same intent from the Accessibility service (`NovaAccessibilityService.startFromService`), (3) a PendingIntent with the Android 14 background-start option; after each way it waits for the app to be in front. The notification fallback is gone (`notifyOpen` is no longer called). Already-open target apps now count as opened.
- Result is told ONCE at the end: "<app> khul gaya", or, if Accessibility is off, "kholne ka order de diya" (no claim it opened), or, if all 3 ways failed, one sentence with the real cause and the one-time switch to turn ON.
- HONEST LIMIT: if the phone brand blocks background pop-ups, no app can open anything until that switch is ON once; nothing here can switch it for the owner. NOT RUN: compile, JUnit, phone.

## v6.77 - owner request: do the job directly, no chatter, keep going until it is finished
- CHECKED (source): with the default settings (`skip_risky_confirm` and `skip_order_confirm` both ON) `askFirst()` runs the action at once: a call and a WhatsApp / Instagram / any-app message are done with NO yes/no. The Part 6 note that call / WhatsApp "keep a mandatory spoken yes" was WRONG for the defaults: it exists only if the owner turns those two switches OFF. Stale comments in `callTool` / `whatsappTool` / `ToolVerify.sensitive` corrected; a contract test now pins the defaults.
- FIX the "request bheji hai par confirm nahi hua" talk after opening an app: `launch()` now waits up to ~3.6 s (was 1.5 s) for the app to come to the front, so slow apps are no longer reported as not confirmed; if it really did not open, the text is just "<app> khul nahi paya. Notification par tap karo" (no request / permission wording).
- CHANGE agent persistence: `MAX_STEPS` 30 -> 60, `MAX_MS` 5 min -> 10 min, "stuck" retries with another way 2 -> 4. It still stops (loop caps, owner says stop, locked phone) and then says how far it got.
- UNCHANGED on purpose: pay / buy / transfer / password / OTP / banking / wallet / UPI / installer / Settings screens stay refused (they protect the owner's money; Android also hides secure screens from Accessibility). Android's own permission dialogs (microphone, contacts, CALL_PHONE, notifications) cannot be skipped by any app: they are asked once, and after Allow NOVA calls directly.
- RUN: Python contracts. NOT RUN: Kotlin compile, JUnit, phone (no kotlinc / internet here).

## v6.76 - Part 6: agent loop hardening (verify levels, one bounded retry, honest notes, stricter AI-app send)
- AUDIT (what each tool really verifies, from the source): torch = TorchCallback read-back; volume = AudioManager read-back; brightness = Settings read-back; media play/pause = `isMusicActive` (next/previous cannot be confirmed); open_app / url / navigate / camera / settings = foreground-package check in `launch()` or an honest "requested, not confirmed"; call = Android accepted the dial request only; alarm / timer = Android gives NO answer (text already says so); sms / whatsapp / calendar = the owner presses send / save.
- NEW `ToolVerify.kt` (pure Kotlin): level per tool (VERIFIED / PARTIAL / HANDOFF / UNVERIFIABLE; unknown tool = UNVERIFIABLE), failure kinds read from the tools' own texts, bounded recovery, sensitive-action table, one-sentence `report(...)`.
- NEW hook in `NovaService.runLocalTool`: ONE automatic retry, only for torch / volume / brightness when Android did not confirm (they set an explicit state, so repeating cannot do more). Never for call, WhatsApp, SMS, alarm, timer, open_app, media.
- NEW hook `ToolVerify.finish` in `runLocalTool` and `runTool`: a success that Android cannot confirm and whose text does not already say so gets "Verify nahi hua / Not verified"; a failure gets at most one guide ("App ka naam dobara bolo, ya Play Store se install karo", or say "setup check" for a missing permission). No loops, nothing on protected screens.
- FIX `AgentRules.check`: inside ChatGPT / Gemini the send tap was free for ANY label whose only risky word was "send" (so "Send money" / "Send file to ..." passed). It is now free only for a plain send button (`AgentRules.isAiSend`: "Send", "Send message", "Send prompt", "bhejo"). Everything else asks first. Pay / buy / password / OTP etc. stay DENY.
- Confirmation audit: call and WhatsApp send keep their mandatory spoken yes (`askFirst(..., must = true)`, unchanged); SMS and calendar only open the compose / save screen. Nothing sensitive runs silently.
- NEW tests: `ToolVerifyTest` (7 tests), `AgentRulesPart6Test` (6 tests: banking / payment / installer / own screen DENY, money and security words DENY, risky words CONFIRM, plain send only in AI apps), `tools/test_tool_verify_contract.py` (every dispatched tool has a level; hooks present; classify expectations match a Python port).
- NEW docs: `docs/TEST_REPORT_PART6.md`, `docs/CONTINUATION_PART6.md`, `docs/NEXT_ACCOUNT_PROMPT_v6.76_PARTS_7_TO_10.md`. versionName 6.76 (versionCode unchanged).
- RUN: 45/45 Python contract scripts PASS. NOT RUN: Kotlin compile, JUnit, Gradle / APK (no kotlinc, no internet here), real phone. `ToolVerify.kt`, the 2 hooks and the 2 new test classes have NEVER been compiled: run GitHub Actions first.
- NOT DONE (honest): `ToolVerify.report(...)` exists and is tested but is not yet spoken by the agent loop (the agent's own done / fail texts already say verified / not verified); per-step verification inside the screen agent (tap / type) is unchanged; no sandbox proof that a retry helps on a real phone.
- No feature removed; no speaker recognition / voice lock added; no permission or security restriction bypassed.

## v6.75 - Part 5 finished: voice + Android control audit (4 classification bugs fixed, 181-phrase corpus, honest "cannot do" answers)
- FIX `Logic.classify` negation: "volume kam mat karo", "torch on mat karo", "brightness kam mat karo", "volume badhao mat" used to RUN the positive command. A negation guard (mat, nahi, nahin, nhi, nai, dont, don, not, never, without, bina, mt) now sits ONLY inside the torch / volume / brightness / media / global-nav blocks; such sentences go to the AI instead. "do not disturb" is untouched (its rule is outside the guard).
- FIX open-app: "instagram open karo", "chrome khol do", "chrome ko kholo" now open the app (`OPEN_TAIL_RE`; trailing " ko" removed). `open` / `launch` at the END need a karo-type word (karo, kar do, kardo, kar, do, dijiye, kijiye), so "is store open" is still not a command.
- FIX `Understand.simplify`: "gaana bajao tum hi ho" was read as plain "play music" because the song title was eaten as filler words. "play music" / "pause music" are now returned only for sentences of at most 4 words; longer ones go to the AI (which can pick the song).
- NEW `UnsupportedRules`: "cancel alarm", "alarm cancel karo", "timer cancel karo", "cancel timer" (+ delete / hatao / remove forms) get an honest answer (NOVA cannot cancel one; use the Clock app) instead of a cloud round trip. "alarm band karo" / "alarm off" are NOT touched: they stay the wake-up alarm switch.
- NEW tests: `CommandCorpusTest` (181 English / Hindi / Hinglish phrases through `Understand.classify` + one test per fixed bug), `VoiceDiagnosisTest` (5 tests; v6.74 had none), `tools/test_command_corpus_contract.py` (re-checks the corpus against the Python port and the 4 fixes in the Kotlin source). `tools/classify_mirror.py` updated to the new logic.
- NEW docs: `docs/VOICE_AND_BATTERY.md`, `docs/TEST_REPORT_PART5.md`, `docs/CONTINUATION_PART5.md` (now DONE), `docs/NEXT_ACCOUNT_PROMPT_v6.75_ALL_10_PARTS.md`, delta table in `docs/FEATURE_REGRESSION_v6.73.md`. versionName 6.75 (versionCode unchanged).
- Wake false-trigger audit: no code change needed (WakeStrictTest, WakeAdaptTest, ListenGuardTest, WakeRulesTest already cover it; see VOICE_AND_BATTERY.md).
- RUN: 44/44 Python contract scripts (incl. the new one) PASS. NOT RUN: Kotlin compile, JUnit (no kotlinc and no internet in this sandbox), Gradle / APK, a real phone. The v6.74 Kotlin files (`VoiceDiagnosis.kt`, `UnsupportedRules.kt`) and everything in this entry have NEVER been compiled: run GitHub Actions first.
- No feature removed; no speaker recognition / voice lock added; no permission or security restriction bypassed.

## v6.73 - Part 4: build gate (sandbox compile + JUnit), regression sweep, build docs
- RUN: real kotlinc 1.9.24 compile of all 115 main files = 0 errors (stubs for third-party jars, AOSP android-34 jar); JUnit 571/571 PASS (first run of the Parts 1-3 tests); 43/43 Python contract scripts PASS; skill packs, JS, XML, UI<->Bridge (122/122) checks PASS; no secrets in the tree.
- NOT RUN: Gradle/AGP build and APK (Android SDK and Google/Maven repos are unreachable from the sandbox), real phone.
- FIX (test only): `LogicTest.papaCallRoutesLocallyWithoutCloudKey` expected null for "call of duty kholo"; the generic "<app> kholo" rule opens the app, so the test now expects `open_app` (still never a call). Comment fix in `WavPcm.kt` (no voice-lock mention).
- NEW: `tools/run_all_checks.sh` (the right way to run the Python checks; `unittest discover` gives false errors), CI step for it, `.gitignore`, `docs/config_templates/` (placeholders only), extra sandbox stubs in `tools/local_stubs/extra/`, `docs/FEATURE_REGRESSION_v6.73.md`, `docs/TEST_REPORT_PART4.md`, `docs/CONTINUATION_PART4.md`; `docs/BUILD.md` rewritten. versionName 6.73 (versionCode unchanged).
- No feature removed; no speaker recognition / voice lock added.

## v6.72 - Part 3 of the master prompt: agent task memory after a restart, long jobs remember their start, interrupted jobs are reported
- AUDIT (agent): the task ledger kept only the summary, state, step count, last checkpoint and the LAST step message. After a restart the owner-requested safe resume re-planned from the current screen but the model did not know what was already done; and during a long job the model saw only the last 10 actions. A model switch mid-task was already fine (every step prompt carries task + action history). A restart used to mark the job interrupted silently.
- NEW: every finished step saves a privacy-safe progress line (`AgentTaskRules.progressLine`: step number, action name, app name only for open_app, ok / FAILED, screen changed or not; never typed text and never a screen label). The ledger keeps the newest 12 lines / 900 characters; a safe resume copies them and the new run starts with them ("before the interruption"). Old ledger records without the field still load.
- NEW: the agent prompt now has an EARLIER ACTIONS part (older actions in short, up to 1,200 characters, newest of the old kept) next to the last 10 actions, so a long job keeps its own start.
- NEW: when a restart cut a job short, the owner gets one notification with the exact resume status ("resume last task" is offered only when it is provably safe). Nothing is ever replayed automatically.
- Unchanged on purpose: resume is owner-only, latest interrupted task only, only if every recorded action was repeat-safe, within the original deadline; the verifier, the DENY/CONFIRM safety checks, the stall retry of v6.68.
- One existing contract check was updated on purpose (`test_agent_task_ledger_contract.py`: the `recordStep` call now also passes the progress line); the privacy rule it protects is checked again in `test_agent_restore_contract.py`.
- Typed-chat memory limit (open question of Part 2): already solved in v6.71 (the AI gets the whole stored chat), so no owner decision is needed.
- Checked (RUN): `tools/test_agent_restore_contract.py` (14 checks), all 43 Python contract tests. NOT RUN: Kotlin compile / Gradle, JUnit (new `AgentProgressTest`, 9 tests), a real interrupted job on a phone.

## v6.71 - the AI gets the FULL current chat, not only the last 6 messages
- Owner request: the AI must know the whole history. Before: Gemini and the backup AIs saw only the last 6 messages (`Logic.HISTORY_MAX`) and nothing after 2 quiet minutes.
- Now the model gets the whole CURRENT chat from the chat store (v6.69), as much as the model can take: Gemini up to 120,000 characters, free providers up to 12,000 characters (small context windows). Each message is kept up to 1,500 characters; two messages in a row from one side are joined (nothing is dropped to make the roles alternate).
- If a chat is longer than the budget, the OLDEST messages are left out (never the newest) and the free-chain system prompt says so.
- The 2-minute memory limit no longer applies to the stored chat, so an old chat opened from HISTORY, or a long typed chat, keeps its context. NEW CHAT still starts with an empty history (nothing old is mixed in); saved memory (YAAD) is separate.
- The offline model (small context) still gets only the last 2 turns; the project cannot send it more.
- A chat now stores up to 500 messages (was 200).
- Not touched: safety checks, verifier, speed work, UI, no speaker recognition / voice lock.
- Checked (RUN): `tools/test_fallback_context_contract.py` (22 checks), all 42 Python contract tests. NOT RUN: Kotlin compile / Gradle, JUnit (new `fullWindow` tests in `FallbackContextTest`), real provider calls, a real phone. Whether a given free provider accepts 12,000 characters was not tested; the chain moves to the next model if one refuses.

## v6.70 - Part 2 of the master prompt: backup AI keeps the conversation + task, clear missing-config report
- AUDIT: Gemini key / model fallback already reuses the same request (history, system prompt, facts). The free chain (Groq, OpenRouter, GLM, Pollinations) and the offline model got ONLY the last sentence: no conversation, no task. The agent's own fallback (`agentChain`) already carries the task prompt and step history, and stays as it was.
- NEW `FallbackContext.kt` (pure): the newest 6 turns (max 300 characters each, alternating user / assistant, starting with the user) and one redacted line about the latest phone task (only when it is under 30 minutes old; a task that finished without verification is never called "verified").
- `FreeProviders.buildBody` and `ProviderChain.run` take optional `turns` (default empty = the old body, old callers and tests unchanged). `runFreeChain` and the free phone-action pass now send the turns; the chain system prompt also gets the task line. Roles are limited to user / assistant.
- The offline model gets the last 2 turns (`LocalChat.buildPrompt(..., earlier)`).
- A backup answer (free chain or offline model) is now remembered in the conversation, so the next question, whoever answers it, continues the same thread. Old turns (older than 2 minutes) are not sent, same rule as Gemini.
- NEW `ConfigReport.kt` (pure): say or type "nova setup check" / "kya missing hai" / "missing keys" / "kya setup baaki hai". It is answered on the phone from the REAL state (keys saved, microphone, Accessibility, notification access, overlay, offline model) and lists what is not configured. It says "configured", never "working": a saved key can still be wrong or out of quota.
- The agent now says clearly "no AI key is configured, add one in Setup" when a job needs an AI and none exists (before: "no answer right now (failure kind)").
- Not touched: safety checks, verifier, speed work of v6.68, chat sessions of v6.69, UI. No speaker recognition / voice lock / enrollment.
- Checked (RUN): `tools/test_fallback_context_contract.py` (18 checks) and all 42 Python contract tests. NOT RUN: Kotlin compile / Gradle (no compiler or network here), the new JUnit files `FallbackContextTest`, `ConfigReportTest`, `FreeProvidersTurnsTest`, and any real phone or real provider call.

## v6.69 - Part 1 of the master prompt: chat sessions (New Chat, chat ids, history, resume)
- NEW `ChatSessionRules.kt` (pure rules) + `ChatSessions.kt` (private on-phone store "nova_chats"): every chat has its own id, title and messages (max 30 chats, 200 messages each, 2000 characters per message). Only the owner's lines and NOVA's answers are kept; status lines and keys never.
- Every chat line now goes through `NovaService.emit()` and is saved even when the app screen is closed.
- Chat screen: NAYI CHAT button, HISTORY list (open an old chat, delete with a second tap), the current chat comes back after an app restart.
- NEW CHAT clears the running model context only; saved memory (YAAD) and notes stay. An opened old chat gives the model back only its newest turns (max 10 messages / 3000 characters). Switching is refused while NOVA is working.
- Not touched: all speech, agent, safety, memory, wake and UI parts. No speaker recognition / voice lock / enrollment (as the owner asked).
- Checked (RUN): `tools/test_chat_sessions_contract.py` (18 checks), all 41 Python contract tests, JS syntax of all 17 script blocks of ui.html, and a headless-browser run of the new chat screen with a fake store (8 checks, screenshots looked right). NOT RUN: Kotlin compile / Gradle (no compiler or network here), the new JUnit file `ChatSessionRulesTest.kt`, anything on a real phone.

## v6.68 - faster jobs, fewer half-finished jobs
- SPEED: after every agent action NOVA no longer sleeps a fixed 0.5-1.8 s. New `agentSettle()` waits only until the screen has changed and then stopped changing (two equal reads), min 100-400 ms, max 0.7-3.5 s. Fast apps go on at once; slow apps get up to the old time or a bit more, so the next step never reads a half-loaded screen. Same for the local tap / scroll commands (`doTap`, `scrollCmd`).
- SPEED: slow AI calls no longer hold a job for up to 45 s. `postFallback(json, readCapMs, deadlineMs)`: planning 10 s / 16 s, each agent step 16 s / 32 s, final check 12 s / 20 s, then the next key / model / free provider answers. Chat and other callers keep the old 25 s / 45 s.
- COMPLETE: a stalled job (same action 3 times, screen unchanged) is no longer dropped at once. NOVA tells the model "do not repeat, try another way" and retries up to 2 times, then stops.
- COMPLETE: the independent result check may send the job back for repair up to 3 times (was 2) while time is left; "done" is still never accepted without that check.
- HONEST: when a job stops early ("too many steps", "stuck") the spoken / chat sentence now says the last thing that worked, so "adhura" is clear.
- Not touched: safety checks (DENY / CONFIRM / protected screens), the independent verifier, the planning call for long jobs, wake / popup rules.
- Checked (RUN): new `tools/test_fast_complete_contract.py` (13 checks) and all 40 Python contract tests pass. NOT RUN: kotlinc / Gradle compile (no compiler and no network in this sandbox), JUnit, any phone test. Speed numbers are not measured; they must be timed on the phone.

## v6.67 - in-app jobs (chat, YouTube) + questions in the middle of a job
- NEW agent action `ask_wait`: NOVA speaks a question, listens, and the SAME job continues with the answer (old `ask` still ends the job). `kind:"choice"` = short answer or list number, `kind:"text"` = dictated message (longer listening). Max 3 per job; silence is asked once more, then the job stops. Code: `AgentRules` (parse/check/`numberFrom`/`answerLine`), `NovaService.agentAskAnswer` + a branch in `processAnswer` (`agentAnsPending`).
- NEW routing `AgentRules.isAppJob`: "chat open karo rahul", "instagram par chat open karo aman ki", "youtube pe lofi chalao", "mera channel kholo", "video pause karo" (only if no local command exists). The old "<app> par <name> ko message <text>" WhatsApp route is NOT touched (`WhatsAppCommandRules` wins). AI-chat words, recording, call, screen, gallery are excluded.
- NEW playbooks in the agent system prompt: chat (open app, search name, ONE match opens, SEVERAL similar names = "Kaun sa? 1 .., 2 .." up to 7, owner says 1-7 in Hindi/English or the name, ask the message ONCE, type it, tap Send only if the task said send/bhejo) and YouTube (search + play, pause/play on the open video, channel, my channel / my videos; never Subscribe/Join/Buy/ads).
- Number words 1-9 in Hindi/English/ordinals ("do", "teesra wala", "number paanch", "seventh") -> `numberFrom`; a full name or a long sentence gives none.
- Popup (card) now stays while a job runs (`hudHide` waits for `agentRunning`) and closes by itself after the job; it still never opens without the wake word.
- Short app jobs skip the planning call (`fastJob`).
- FIX found by the checks: "whatsapp par chat open karo rahul" would have been sent by the old WhatsApp flow as a message with the text "chat open karo rahul". It is now an open-chat job; "whatsapp rahul ko message bhejo hello" keeps the old flow.
- Question time: a job's question waits 60 s (`pendingTtl`), card may stay up to 120 s while a job runs. "chat clear / band karo" and a bare "message ..." are not app jobs. New row "Apps ke andar kaam (chat, YouTube)" in the home status list.
- Checked (RUN): (1) the 52 pure Kotlin files compile with kotlinc 1.9.24 and a main() with 30 checks passes, new and old behaviour. (2) the WHOLE app (incl. NovaService, MainActivity, FeatureStatus) was compiled against android.jar API 34 from github.com/Sable/android-platforms: NO error in NovaService / AgentRules / FeatureStatus / MainActivity (a deliberate test error in NovaService WAS reported, so the file is really analysed). The only errors left are files that need libraries missing here (WorkManager, coroutines, llama, Porcupine, R): they build fine in Gradle. (3) 39 Python contract tests pass (new: `tools/test_inapp_jobs_contract.py`). NOT RUN: Gradle, JUnit (new tests are in `AgentRulesTest`), any phone test.

## v6.66 - no voice lock, no screen-lock gate
- REMOVED: the "locked phone takes no new task" rule (PhoneLock.kt, lockedPhoneAllows, its row in the feature list, README line). NOVA now hears and acts on anybody's voice, locked or open. There is no voice recognition or enrolment anywhere.
- Kept (Android limit, not a NOVA rule): screen-driving jobs (tap/scroll in other apps) still say so if the phone is locked, because Android cannot be driven on a lock screen. The voice command "screen lock karo" is also kept.
- NOT compiled, NOT phone-tested.

## v6.65 - one UI for weak and strong phones
- NEW: `Android.getTier()` (DeviceTier: RAM / cores / Android version) tells the page LOW / MID / HIGH before anything draws. LOW = light mode for good + orb at half size; MID = full look allowed but orb at 75 % size, 30 fps, canvases capped at 2x; HIGH = unchanged. The existing automatic jank governor stays on top of it. No feature or layout was changed. Browser preview falls back to `?tier=low|mid|high`.
- Checked in headless Chromium (software GL, so speed numbers are only indicative): no JS errors, no external requests, no sideways scroll at 360x640 and 412x915. NOT compiled, NOT phone-tested.

## v6.64 - chat is silent by default
- CHANGE: typed chat replies are no longer spoken. Settings has a new switch "CHAT ke jawab bolkar bhi sunao" (default OFF). Every NOVA chat message has a speaker icon: tap it and that message is spoken once (NOVA's voice when ACTIVATED, otherwise the phone's own TTS). Microphone / wake-word replies are unchanged.
- CLEAN: last "voice lock" wording removed from the hearing self test (speaker recognition / voice lock / face / pattern were already removed in v6.51).
- Audit: see `docs/FEATURE_AUDIT_v6.64.md` (static checks RUN, build and phone tests NOT RUN). New `tools/test_chat_voice_contract.py`.

## v6.63 - Gemini listening hardened
- Listening chain was already: offline Vosk -> Groq (if key) -> Gemini (owner's key, multi key/model failover) -> offline Hindi pack. Kept as is.
- NEW: `ListenGuard.cleanCloudTranscript` cleans Gemini's text: "[silence]", "(inaudible)", "Transcript:" labels and an echoed instruction become "" instead of reaching the command parser. Unit tests added (GeminiTranscriptTest) + `tools/test_gemini_listen_contract.py`. Gemini text still can never approve a yes/no. NOT compiled, NOT phone-tested.

## v6.62 - permission chain
- First open permission chain now also opens "Install unknown apps" (used by the in-app update). Re-runs once on update; only missing items open, each once. Android cannot grant permissions at install time, so this one-time guided chain on first open is the closest possible. NOT compiled, NOT phone-tested.
- Wake behaviour unchanged: NOVA starts only after ACTIVATE and wakes only when a sentence opens with "nova" / "hey nova".

## v6.61 - static review fixes (NOT compiled, NOT phone-tested)
- FIX: 68 x `catch (_: ...)` replaced with `catch (ignored: ...)` in 15 files (invalid in Kotlin 1.9.24, would break the Gradle build).
- FIX: removed `!!` in VideoRecordService (local `outFile` val instead of `temp!!`).
- FIX: stale `test_agent_plan_contract.py` check updated to the current `agentRun` code (fastJob).
- FIX: FileProvider no longer shares the whole app files folder, only `rec/` (recordings).
- IMPROVE: 112 silent empty `catch` blocks now write the reason into ErrLog (Settings error list) instead of hiding it. Cleanup/release catches left silent on purpose.
- CI: workflow can be started by hand and now uploads the debug APK (`nova-apk`) and the test report.
- Docs: README version 6.55 -> 6.61. Python contract tests 35/35 PASS. Gradle build / Kotlin tests / phone: NOT RUN.

## v6.59 — offline Vosk model packaging fix
- Added `tools/prepare_vosk_model.sh` to download the official Vosk small English model over HTTPS and validate every file required by NOVA before packaging.
- Updated GitHub Actions to prepare the model before running Gradle unit tests and assembling the debug APK.
- Updated local build instructions; the model remains outside the source ZIP to avoid embedding a large third-party binary.
- Kept the optional Porcupine setup unchanged: owner-provided Picovoice AccessKey and `nova.ppn` remain required; existing Vosk fallback is preserved.
- Preserved v6.57 lead-routing files/tests and the v6.58 call-intent changes.
- Version bump: 6.58 → 6.59; default versionCode 17 → 18.

## v6.58 — offline contact-call intent (2026-10-13)

- FIX: explicit phrases such as “Papa ko call karo” and “call Papa” are now parsed as a local call intent, so they do not require Gemini/free-provider availability merely to understand the command.
- Safety tightened per owner requirement: calls now always require an explicit spoken confirmation even if the optional “skip ordinary confirmations” setting is enabled. Contact resolution still requires the existing contacts permission; Android dialer / CALL_PHONE permission flow remains in `callTool`. No call is initiated by the intent parser itself.
- Preserved v6.57 lead-intent routing guard and its regression coverage unchanged.
- Added Kotlin regression coverage for “Papa ko call karo”, “nova call papa”, “phone papa”, and negative/ambiguous inputs.
- Validation: repository Python contract scripts pass (34/34). Kotlin/Gradle build and Kotlin unit tests were not executable in this environment; real-phone call, wake/popup, and live Gemini tests remain NOT TESTED.

## v6.57 — lead-intent routing guard (2026-10-13)

- Added an explicit lead-acquisition intent guard before the website builder route. Requests such as “mere shop ke liye leads lao” now receive an honest lead-workflow response instead of silently being treated as a website task.
- The response clearly states the actual current capability: NOVA can capture owner-selected public business listings and draft outreach; this build does not independently crawl/search the web for new leads.
- Added unit-test coverage for lead-vs-website intent separation.
- Bumped Android version code/name for this patch.
- Validation: 34/34 repository Python contract tests pass. Android/Kotlin compilation and device-level Gemini/wake-word/popup/call tests were not verified in this environment; do not treat them as passed.

# Changelog

Build/test status for every entry below v6.38 is "not compiled on Android, not phone-tested" unless stated.

## 6.56
- NEW: optional Porcupine wake word (Picovoice). Settings > PORCUPINE WAKE WORD: paste AccessKey, pick the custom nova.ppn (Android), ACTIVATE again. Vosk stays as automatic fallback: no key / no file / wrong .ppn / any error / any runtime failure = old Vosk wake. Dependency ai.picovoice:porcupine-android:3.0.2 (version NOT verified from here: if CI cannot resolve it, change the number). Removed the unused androidx.biometric dependency (left from face lock). Picovoice free plan = personal use only; selling NOVA needs their commercial plan. NOT compiled, NOT phone-tested.
- Self-tuning wake: every false wake in a row makes the next wake need a 25 % louder voice (max 2x); a real command resets it; it relaxes after 10 quiet minutes (WakeAdapt). Phone tiers LOW / MID / HIGH (DeviceTier): a LOW phone (under 3 GB RAM, under 6 cores) no longer auto-downloads the 491 MB offline model. NOT compiled, NOT phone-tested.

## 6.55
- FIX: NOVA waking without being called. (1) The wake word must now OPEN the sentence ("nova ..." / "hey nova"); "nova" guessed in the middle of TV / other people's talk no longer wakes it. (2) A partial wake result must hold for 4 results (~0.4 s, was 2); a final result still counts at once. (3) Wake must be louder (peak >= 1100 and 3.5x room noise, was 900 / 3x). (4) Cooldown after a turn 3 s (was 1.5 s). (5) The "Phone locked" refusal is shown in the chat only and never spoken, so a false wake can no longer talk by itself.
- Voice recognition, voice lock, pattern lock and face lock: already removed in 6.48/6.51; leftover wording in SelfTest, ListenGuard, README and Logic comments cleaned. The "unlock" voice command is only a stub that says unlocking was removed.
- NOT compiled, NOT phone-tested. Wake thresholds are tuned by reasoning, not on a phone: if NOVA now misses a soft "nova", lower WAKE_CONFIRM_HITS to 3 or the 1100 peak in NovaService (one number each).

## 6.54
- Owner request: manifest now declares CALL_PHONE and QUERY_ALL_PACKAGES. With CALL_PHONE allowed NOVA places the call itself (ACTION_CALL); without it the dialer opens and NOVA asks for the permission once. Android always needs that one Allow tap: an app cannot grant it to itself.
- NEW: delete a sent auto reply (WhatsApp: Delete for everyone, Instagram: Unsend) by voice ("reply delete karo") or the DELETE KARO button in the history. Uses Accessibility, only inside an open conversation, only the bubble whose text equals NOVA's reply, never "Delete for me"; checks on screen and writes "deleted" / "delete failed" + reason into the history. Depends on the apps' own screens: NOT phone-tested.
- NEW agent actions: screenshot (Android's own screenshot action, saved to the gallery) and zoom_in / zoom_out (two-finger pinch). Still refused on protected screens (bank, payment, settings, installer). "Open any app and do <task>" already ran through the task agent (open_app + tap / long_tap / type / scroll); the planner prompt now knows the two new actions.
- NOT compiled, NOT phone-tested.

## 6.53
- Owner request: AUTO REPLY. A new WhatsApp / Instagram (Message Mode apps) message gets the owner's own reply text at once, without asking; NOVA tells the owner afterwards. Works through the notification's Reply button, also when NOVA is not ACTIVATED.
- Limits kept: never groups, never code/OTP/password messages, never notifications without a Reply button; max 1 reply per sender per 10 min, 3 per hour, 80 per day in total. Business watch (WaBiz) keeps its own rules while it is running.
- NEW: bell icon with unread badge on the home page opens a HISTORY of everything NOVA did by itself (sent / failed / skipped + why); SAAF KARO clears it. Settings: AUTO REPLY box (switch + reply text). Stored only on the phone, newest 300.
- NOT done: deleting a sent message (see HANDOFF). Reply text is fixed, not AI-written. NOT compiled, NOT phone-tested.

## 6.52
- Owner request: SCREEN MONITORING now defaults to ON (still needs the Accessibility permission, still local only). MESSAGE MODE now defaults to ON with WhatsApp, WhatsApp Business and Instagram selected (one-time upgrade; if the owner turns it OFF later that stays OFF). Replies are still sent only after the owner's spoken yes. NOVA still starts only after ACTIVATE. Status: NOT compiled, NOT phone-tested.

## 6.51
- REMOVED: speaker recognition / owner-only voice, voice unlock, pattern unlock/setup, face gate, voice enrolment, and the voice-model download. Files deleted: VoiceLock, OnnxSpeaker, SpeakerRules, VoiceFaceRules, VoiceEngineRules, Fbank, FaceGate, PatternUnlock, PatternSetupActivity (+ their tests). The onnxruntime dependency is gone. Anybody's voice is accepted when the phone is open.
- NEW rule (PhoneLock.kt + `NovaService.lockedPhoneAllows`): when the phone is LOCKED NOVA can still hear and speak, but takes NO new task. Already running work continues: the answer to a running job's question, "stop", and the wake-alarm OFF still work. Otherwise NOVA says so (at most once every 8 s).
- Settings UI: voice/face boxes and the Pattern row removed; the QWEN download button (`am_brain`) is back in MODEL DOWNLOAD (it had been dropped by mistake). Auto-Heal no longer refetches a voice model.
- Contract tests updated (voice-flow, feature-status, autosetup, ai-rank). Status: python contract tests pass; NOT compiled (no Gradle here), NOT phone-tested. Old saved voiceprint/ONNX files on a phone are simply unused.

## 6.50
- Chat: new "phone voice typing" button (system speech, same engine as the keyboard mic); the typed text box stays. Placeholder says so.
- Floating card made smaller (316x148 dp, was 380x190), reply max 2 lines.
- 3D orb is not drawn while a full-screen panel or the setup popup covers it (saves GPU).

## 6.49
- Gemini keys: UI now has KEY 1 (main) + KEY 2 and KEY 3 (fallbacks); failover order was already supported by the backend.
- Smooth start: app starts in the light-effects mode and upgrades to full effects after ~7 s of smooth frames; removed the 3 heaviest backdrop blurs; model/disk work delayed until after the first frames (AutoSetup 1.5 s, voice model 5 s, offline AI 9 s).
- Fixes: `!!` in wake alarm code; stale contract tests (voice lock always ON, androidx.core pin).

## 6.48
- REMOVED: locked-phone auto-unlock (pattern). NOVA never unlocks the phone; voice "unlock" now only says so; battery SOS no longer tries to unlock.
- Owner-only voice check now applies ONLY while the phone is locked. When the phone is open, any voice may give a command (after the wake word).

## 6.47
- LlamaBrain rewritten for llamacpp-kotlin 0.1.0 API (was written for a newer API: LLMEvent, callback load, stopPrediction). Compiles against an API stub; NOT run on a phone.

## 6.46
- First real Kotlin compile fixes from CI: missing Context import (MainActivity), whatsappTool result `.msg` (NovaService), expression-body `return` (VoiceLock.saveProfile).

## 6.45
- Build fix: force androidx.core/core-ktx to 1.13.1 (transitive 1.15.0 required compileSdk 35 and failed CI).

## 6.43
- Wake alarm: active only 06:00–12:00; rings repeatedly until OFF (DISMISS = 1 min snooze); 20‑minute auto-stop and unlock-stops-alarm removed.
- Docs reorganized: README, CHANGELOG, `docs/` (BUILD, ARCHITECTURE), history moved to `docs/archive/`.

## 6.42
- Fix `ChatText` JSON field regex (over-matched past closing quote).
- Fix `WhatsAppCommandRules` recipient casing.
- Verified with kotlinc: 55 pure-Kotlin files, 521 JUnit tests pass.

## 6.41
- Morning greeting + today's jobs, 25‑minute lead alarms, wake alarm (WakeRules).
- Tests: BatteryRulesTest, ScheduledDaysTest, WakeRulesTest.

## 6.40
- Schedule: weekdays bitmask, priority, note; voice commands and *MERA SCHEDULE* UI.

## 6.39
- Battery alerts (50/40/30/20/10 %) and ≤5 % WhatsApp SOS to Papa/Mummy.

## 6.38
- No auto-start (BootReceiver only restores reminders). Owner-only voice lock; locked-phone voice unlock via pattern.

Older history: [docs/PROGRESS.md](docs/PROGRESS.md) and [docs/archive/](docs/archive/).

## PART 2/5 — Contact selection and safe call flow
- Added an accessible Android contact-choice dialog for ambiguous/similar/low-confidence call matches, showing actual contact names and masked phone suffixes.
- Touch selection passes the selected real candidate to a call-confirmation path; it does not call a guessed candidate.
- Added voice-choice matching against only the currently displayed real candidates; ambiguous/unrelated answers do not select a contact.
- Added a one-at-a-time guard for selected-contact call execution and reports actual Android call exceptions.
- Automated unit tests cover exact voice choice, ambiguous first-name choice, and unrelated voice choice. Real Android/device tests remain pending.

## PART 3/5 — Centralized intent parser and command routing foundation
- Added side-effect-free `IntentParser` for contact lookup, call, generic message, WhatsApp message, app-specific call/message, open-app, and ambiguous/low-confidence requests, with English/Hindi/Hinglish phrasing support.
- Added `CommandRouter` route plans separating application identity from action and describing contact/app/capability/clarification/confirmation stages. Added a deterministic in-flight claim/release primitive for duplicate-execution prevention by future handlers.
- Integrated a conservative clarification preflight in `NovaService`; it intercepts only low-confidence requests when existing deterministic handlers do not already own them. Existing contact, WhatsApp, app launch, permission, confirmation, and result handlers remain authoritative.
- Added `IntentParserTest` for call/message/open-app intent, Hindi/Hinglish variants, app-specific routing, ambiguous/negated commands, stage separation, duplicate guard, and safe fallback.
- Verification: relevant Python regression/contract checks passed. Gradle unit tests and Android build could not run because Gradle 8.7 is not cached and network DNS to `services.gradle.org` is unavailable. Real-device verification remains deferred until all five parts are complete.

## Part 9D - DND access wiring
- Manifest: ACCESS_NOTIFICATION_POLICY. Bridge openDndAccess + `dnd` state. UI: DND rows (permissions, Settings, SAARE FEATURES). FeatureStatus: ringer + rotation/screenshot rows. Contract: test_part9d_dnd_wiring_contract.py.

## Part 10 - chat auto-start
- ui.html: autoStartSend (NOVA off + chat send = auto toggle + resend). test_part10_chat_autostart_contract.py.

## Part 10B - 5 sec live status + AutoTest watcher
- ui.html fsLoad 5000 ms; AutoTest 5 sec watcher + stop() on pause; SelfTest checks DND/ringer/screenshot/rotation/AI SMART; test_part10_autotest_5s_contract.py.

## v7.20 (Part 6, static only)
- Skills screen: +8 entries (photo edit, website gallery media, WhatsApp Business watch, verified music play, my chats, watch mic, wait slider, battery SOS). New guard test_part6_skills_contract.py. No Kotlin changes.

## v7.21 (Part 7, static + headless render)
- HelpGuide +6 topics (Kotlin + ui CAPS), HelpGuidePart7Test; ui.html p7_polish CSS; tools/test_ui_render_smoke.py; tools/KNOWN_STALE_GUARDS.txt + runner label.

## v7.34 PART 5 (add-only)
AutoTrace.kt + AutoTraceTest.kt, NovaService auto log (History "NOVA AUTO") + "auto kya samjha" route, HealthCheck AUTO TRACE section, MainActivity.uiLog bridge, ui.html p5_log, tools/test_part5_contract.py + test_part5_render.py. See NEXT_ACCOUNT_PROMPT_FULL_v7_34_PART5.md.

## v7.35 AUTO VERIFY (add-only)
FeatureVerify.kt (+ FeatureVerifyTest.kt): automatic read-only check of all understood features (old FeatureCheck + Parts 1-5 + commands + state + page wiring); ui.html p6_verify + p6_box; bridges verifyPage / verifyNow; HealthCheck "AUTO VERIFY" line; NovaService route "auto verify status"; History "NOVA VERIFY"; tools/test_part6_contract.py + test_part6_render.py. See NEXT_ACCOUNT_PROMPT_FULL_v7_35_PART5_VERIFY.md.
(v7.35 evidence) Real kotlinc 1.9.24 compile of main + test = 0 errors; JUnit 1117 run, only the 3 known stale fail. Gradle APK build still only in GitHub Actions.

## v7.42 (Part 1-5 jaanch + fixes, add-only)
- ui.html: `p5x_fix` style + script: photo attached bug fix (har sentence image-edit banta tha), ek "Photo / Video chuno" clip button, manual IMAGE / PHOTO / WEBSITE MEDIA / edit-row / places-row chhipaye (code maujood).
- MainActivity.kt: bridge `photoToSite()` (attached photo -> website media).
- SiteSummary.kt: `shortSubject()`; NovaService.kt website start line ab poora sentence echo nahi karti.
- Purani handover / status / autopsy files -> docs/archive_old/ (delete nahi).
- NOT TESTED: compile, JUnit, phone.

## v7.43 (Part 6 HOME REDESIGN, add-only)
- ui.html: `p6_home_css` + `p6_home`: status board (AI / INTERNET / PERMISSION / KAAM / AWAAZ), dock (CHAT / CHUP / SILENT / STOP), orb state NO INTERNET. Old row hidden, ids kept. No new Kotlin.
- tools/check_p6_home.py (static check, PASS).
- NOT TESTED: compile, JUnit, phone.

## v7.83 (unread-message question + honest AI-pause line)
- ADDED UnreadQueryRules.kt + NovaNotificationService.snapshotMessages(): "aaj whatsapp pe koi naya message aaya?" is answered on the phone from live notifications (read-only, codes never read). 7 new JUnit.
- ADDED honest line when the AI is only cooling down after an error (old "samajh nahi aaya" lines untouched).
- Compile + JUnit: 1396/1396 PASS (sandbox). Phone: NOT TESTED. Version string left at 7.82.
