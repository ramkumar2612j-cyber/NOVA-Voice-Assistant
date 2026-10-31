# STEP 5 OF 10 (v7.145): screen pehchan for games and videos
Owner order 30 Oct. Add-only. (CHANGELOG.md is not touched: tools/test_part20_contract.py only accepts a "## v7.13x" top entry.)

## What it does
- "nova ye kaun sa game hai" / "ye video ka naam batao" (also Devanagari). `ScreenIdRules.kt` (pure Kotlin) + `ScreenIdRulesTest` (12 tests). Movies / series stay on MovieScanRules untouched.
- GAME: if Android itself calls the app in front a game (store category / game flag), the answer is its own app name at once: HIGH, no AI, no internet, no wait. NOVA only looks, it never plays or taps in a game.
- Everything else (video, a game Android does not mark): the same capture + vision + web search + confidence as the movie scan (`MovieGuessPipeline.run(..., kind)`, default null = movie scan unchanged), with the app name + window title (`NovaAccessibilityService.otherWindowTitle`) placed in front of the screen text.
- Rules kept: no face / person name ever (parseVision drops it), HIGH needs the name written on screen AND 2 web sources, no web result = LOW = "verify nahi hua", protected screens (bank / password / OTP) stop the scan before anything leaves the phone.

## Evidence
- ScreenIdRules + ScreenIdRulesTest compiled with kotlinc 2.0.21 and run (12/12 pass).
- NovaService / NovaAccessibilityService / MovieGuessPipeline edits: syntax-level kotlinc check only (95 error kinds, identical to the baseline; no new kind). Real Android compile = GitHub "build".
- 105 contract scripts that mention MovieGuess / MovieScan / ScreenVideo / NovaService / NovaAccessibility: 105 PASS.
- Phone: NOT TESTED (needs a game app in front, then "nova ye kaun sa game hai"; and a video / reel for "ye video ka naam batao").

## v7.146 add-on (UI wiring for Steps 1-5, 30 Oct)
- Added 3 help topics (HelpGuide.kt + MADAD list in ui.html, equal): agentscore (Step 1), screenid (Step 5), smartroute (Steps 2-4: direct route, learned routes, vision tap). 64 topics.
- Added 3 matching entries on the Skills screen (skRegistry) and mapped them in tools/test_part6_skills_contract.py.
- Evidence: test_help_contract PASS (64 equal), test_part6_skills_contract PASS, render smoke PASS (MADAD rows = 64). All 159 contract scripts PASS (test_part4_fx_render skipped: needs playwright; test_part6_smooth_contract and test_part5_ui_screens failed once under 8-way parallel load, PASS when run alone).
- NOT DONE / NOT TESTED: Android compile (GitHub build), phone test of every step. Kotlin edit here is only a few Topic(...) lines.
