# NOVA NEXT ACCOUNT PROMPT (after STEP 9, v7.149)
Owner: Hinglish, Android phone only (Chrome + GitHub web + Termux, no PC). Repo: https://github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant (branch main only). Package com.nova.assistant. versionName stays 7.138; versionCode = 2000 + run number. Never touch CHANGELOG.md (test_part20 needs `## v7.13x` on top) or build.yml (sha pinned in test_part5_contract).

## RULES
Add-only. Honest evidence: say "NOT TESTED" for phone/watch, "NOT CONFIRMED" for build. After every step deliver a source ZIP + a FULL next prompt. Termux: short sed/patch only; long pastes corrupt (14 KB base64 failed). Prefer zip upload.
Sandbox: kotlinc 2.0.21 compiles only Android-free Kotlin (JUnit shim + reflection runner); contract scripts tools/test_*.py (run with xargs -P 8, timeout 150); test_part4_fx_render needs playwright.

## 10-STEP STATUS
1 v7.141 AgentScore DONE-CODE | 2 v7.142 DirectRoute DONE-CODE | 3 v7.143 LearnedRoute DONE-CODE | 4 v7.144 VisionTapRules DONE-CODE
5 v7.145 ScreenIdRules (any-screen identify) DONE-CODE (check ScreenIdRules.kt is in the repo) | 6 v7.146 MainStallWatchdog DONE-CODE | 7 v7.147 Kya band hai DONE-CODE
8 v7.148 WATCH DIAGNOSIS DONE-CODE (WatchDiag; root cause still NOT proven; needs owner answers in STEP8_STATUS_v7_148.md)
9 v7.149 LIGHT POPUP (HudTheme, opt-in, voice "nova light popup karo") DONE-CODE, phone NOT TESTED; chat box + Settings toggle still TODO
10 TODO final audit; patch tools/emulator_smoke.sh (ANR reason lines + native crash "Fatal signal"); owner phone test list; final full prompt.

## OPEN OWNER ITEMS
"samajh nahi aaya" on clear speech (need the "🎤 text" chat-line screenshot + whether a Groq/Gemini key is set); slow thinking ("nova feature status" row "Samajhne ki speed"); hang after fresh install (cause unproven: Vosk model re-copy?); Instagram false "blocked" fix, auto Jarvis/Hindi voice: phone NOT TESTED; GitHub build green for v7.144-148 NOT CONFIRMED.

## NEXT: STEP 10 = final audit + emulator_smoke.sh patch + owner phone test list + final full prompt. Also add a Settings toggle for the light popup if the owner likes it.
