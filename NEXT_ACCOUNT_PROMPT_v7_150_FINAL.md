# NOVA NEXT ACCOUNT PROMPT (after STEP 10, v7.150) - 10-STEP PLAN FINISHED IN CODE
Owner: Hinglish, not technical, Android phone only (Chrome + GitHub web + Termux, no PC). Repo: https://github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant (branch main only). Package com.nova.assistant. versionName stays 7.138; versionCode = 2000 + run number. Never touch CHANGELOG.md (test_part20 needs `## v7.13x` on top) or build.yml (sha pinned in test_part5_contract).

## RULES
Add-only, nothing removed. Honest evidence: say "NOT TESTED" for phone/watch, "NOT CONFIRMED" for build. Report progress every 5%. After every part deliver a source ZIP + a FULL next prompt. Termux: short sed/patch only; long pastes corrupt. Prefer zip upload.
Sandbox: kotlinc 2.0.21 compiles only Android-free Kotlin (JUnit shim + reflection runner); contract scripts tools/test_*.py (xargs -P 8, timeout 150; test_part6_smooth_contract and test_part5_ui_screens are load-flaky, rerun alone); test_part4_fx_render needs playwright.

## 10-STEP STATUS (all DONE-CODE; none confirmed on a phone)
1 v7.141 AgentScore | 2 v7.142 DirectRoute | 3 v7.143 LearnedRoute | 4 v7.144 VisionTapRules | 5 v7.145 ScreenIdRules | 6 v7.146 MainStallWatchdog | 7 v7.147 Kya band hai | 8 v7.148 WatchDiag (root cause NOT proven) | 9 v7.149 Light popup (opt-in, voice only) | 10 v7.150 final audit + tools/emulator_smoke.sh patch (native crash "Fatal signal" + ANR reason rows) + OWNER_PHONE_TEST_LIST_v7_150.md.
Audit result: 161 of 162 contract scripts PASS (2 only fail under parallel load, PASS alone; part4_fx_render not run). Android build for v7.144-150 NOT CONFIRMED. Phone NOT TESTED.

## OPEN ITEMS (in order)
1. Push this ZIP to GitHub, get ONE green build. Fix any compile error fully (NovaHud, NovaService, ExtCfg, FeatureVoice, MainActivity were never compiled here). Then read the smoke job AUTO_TEST_STATUS.md (new native crash / ANR reason rows).
2. Owner runs OWNER_PHONE_TEST_LIST_v7_150.md and reports PASS / FAIL per number.
3. Watch: owner must answer 3 questions in STEP8_STATUS_v7_148.md before any fix.
4. "samajh nahi aaya" on clear speech: need the "🎤 text" chat line + whether a Groq/Gemini key is set. Slow thinking: "nova feature status" row "Samajhne ki speed".
5. Hang after fresh install (cause unproven). Use "nova atakne ka hisaab batao" / error log frames; fix only the .commit() calls the watchdog names.
6. Light popup: chat typing box still dark; Settings toggle missing. Add only if the owner likes the look.
7. Instagram false "blocked" fix and auto Jarvis/Hindi voice: phone NOT TESTED.
