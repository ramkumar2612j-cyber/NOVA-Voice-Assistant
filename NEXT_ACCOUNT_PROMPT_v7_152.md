# NOVA NEXT ACCOUNT PROMPT (after v7.152 FINAL CHECK) — reads NEXT_ACCOUNT_PROMPT_v7_150_FINAL.md + v7_151 + AUDIT_v7_152.md + PART_STATUS_v7_152_FINAL.md
Owner: Hinglish, not technical, Android phone only (Chrome + GitHub web + Termux, no PC). Repo: https://github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant (branch main). Package com.nova.assistant. versionName stays 7.138; versionCode = 2000 + run number. Never touch CHANGELOG.md (test_part20) or .github/workflows/build.yml (sha pinned in test_part5_contract).

## RULES (compulsory)
Add-only, nothing removed or weakened. Everything real and wired to the UI; every feature in MADAD/HelpGuide + known to NOVA (aiFacts/SourceMap/FeatureStatus) in simple Hinglish. Honest evidence only: NOT TESTED (phone), NOT CONFIRMED (build), NOT COMPILED (Kotlin here). After every part: full source ZIP + status + FULL next prompt. Short Hinglish messages, one step at a time.

## WHAT v7.152 DID (details: PART_STATUS_v7_152_FINAL.md + AUDIT_v7_152.md)
- Full audit of v7.151: all 163 contract tests PASS in this sandbox (test_part4_fx_render also PASS — playwright available). Dead-UI audit 0 gaps. GLM 5.3 brain wiring verified end-to-end (unchanged, correct).
- Fixes (all add-only): (1) Settings LIGHT POPUP switch (s_light, opt-in, default OFF, getSettings/saveSettings); (2) light-popup chat box now follows the light theme (dark unchanged); (3) popup-theme QUESTION ("popup light hai ya dark?") answers state via FeatureVoice.popuptheme instead of flipping the setting — all old order phrasings still switch (HudTheme.isQuestion guard in NovaService); (4) GLM 5.3 model id single-source (FreeVision.GLM reuses FreeProviders.OPENROUTER_GLM).
- Self-knowledge: HelpGuide topic "aibrain" (67 topics, CAPS + Skills registry synced), MADAD rows "GLM 5.3 Flash brain" + "Light popup (halka card)", FeatureStatus row "popuptheme".
- Tests added: tools/test_v7152_light_popup_contract.py; HudThemeTest +2, FeatureVoiceTest +2, FreeProvidersTest +1, part6 skills mapping line. Kotlin JUnit NOT COMPILED here (no kotlinc in this sandbox).

## OPEN ITEMS (in order)
1. Push ZIP to GitHub, get ONE green build; fix every compile error fully (NovaHud openChat + NovaService route + MainActivity settings changed: never compiled here). Then smoke job AUTO_TEST_STATUS.md.
2. Owner runs OWNER_PHONE_TEST_LIST_v7_152.md (light popup 1-5, brain 6-10, self-knowledge 11) and the older lists (v7.150, v7.151).
3. If OpenRouter GLM 5.3 gives 404/400: fix the model id in ONE place — FreeProviders.OPENROUTER_GLM (FreeVision now follows it automatically). If too slow: lower the 6 s stagger in NovaService runFreeChain (line ~3503).
4. "samajh nahi aaya" on clear speech: still needs the "🎤 text" chat line + whether a Groq/Gemini key is set (v7.150 list item 12). Do not guess.
5. Watch: owner must answer the 3 questions in STEP8_STATUS_v7_148.md before any fix. WatchDiag is in place to capture the answer.
6. Hang after fresh install: fix ONLY the .commit()/frames the MainStallWatchdog names ("nova atakne ka hisaab batao" + error-log COPY).
7. Instagram false "blocked" + auto Jarvis/Hindi voice: phone evidence needed.
8. Part 5 upgrades (reminders, smart reply, speed) only after build green + phone tests PASS on items 1-11.
