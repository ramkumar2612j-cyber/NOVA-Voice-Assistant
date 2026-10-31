# AUDIT v7.152 — NOVA full check (owner order: "ye complete kr ke zip do")

Scope: full source read of the v7.151 ZIP (273 Kotlin files, ~64,000 lines; ui.html 6,072 lines; manifest; tools 198 scripts; skillpacks; docs). History files read first: NEXT_ACCOUNT_PROMPT_v7_150_FINAL.md, NEXT_ACCOUNT_PROMPT_v7_151_GLM53_BRAIN.md, PART_STATUS_v7_151_GLM53_BRAIN.md, STEP10_STATUS_v7_150.md, STEP8_STATUS_v7_148.md, both OWNER_PHONE_TEST_LISTs. ADD-ONLY. CHANGELOG.md / build.yml / versionName (7.138) NOT touched.

## 1. TEST EVIDENCE (real runs, this sandbox)

- Contract scripts: **161 of 162 PASS**. `test_part4_fx_render` NOT RUN (needs playwright, not installed here). The two load-flaky ones (`test_part6_smooth_contract`, `test_part5_ui_screens`) were re-run ALONE: PASS.
- 11 tests first failed only because of working directory (relative paths), re-run from project root: all PASS. No product bug.
- Project's own dead-UI audit `tools/audit_dead_ui.py`: 0 dead controls, 0 bridge gaps, 0 missing help topics, 0 retired-guard leftovers.
- Kotlin compile: NOT DONE (no kotlinc in this sandbox — same as all earlier parts). Android build: NOT CONFIRMED.

## 2. GLM 5.3 BRAIN WIRING — VERIFIED in code (v7.151 work is correctly in place)

| Requirement | Where | Status |
| --- | --- | --- |
| Provider `OPENROUTER_GLM` (id openrouterglm, z-ai/glm-5.3-flash:free, same OpenRouter key, brainOnly, minTokens 4000) | FreeProviders.kt | OK |
| `forJob` drops brain for short jobs, puts it first for brain jobs | FreeProviders.kt + FreeProvidersTest | OK |
| agentChain brain=true | NovaService.kt (~5403) | OK |
| Rich chat hedge stagger 2.5s -> 6s | NovaService.kt (~3503) | OK |
| SiteGen brain first | SiteGen.kt (~290, forJob(..., true)) | OK |
| CloudTaskText brain=true, budget 90 s | CloudTaskText.kt (~43) | OK |
| FreeVision: Groq -> GLM -> OpenRouter router | FreeVision.kt providers() | OK |
| ApiUsage row "GLM 5.3 Flash (brain)" | ApiUsage.kt ROWS | OK |
| FeatureStatus "GLM 5.3 (brain)" in backup-chain row | FeatureStatus.kt | OK |
| LIMITS meter row visible in ui.html | ApiUsage snapshot feeds LIMITS screen | OK |

## 3. FINDINGS (ranked)

### P0 (crash / hang)
None found in static reading. No new `.commit()`-on-main or unbounded thread introduced by v7.151. Hang/ANR root cause stays evidence-gated (MainStallWatchdog data needed from the phone — open item).

### P1 (broken feature / open item that was promised and missing)
1. **Light popup: Settings toggle missing** (open item 6 of v7.150). The theme exists (HudTheme + ExtCfg.lightPopup + voice order) but the owner had no visible switch. -> FIXED in v7.152: Settings checkbox `s_light` (opt-in, default OFF) wired through getSettings/saveSettings.
2. **Light popup: three-dots chat typing box always dark** (known TODO). `NovaHud.openChat()` hard-coded the dark Ultron gradient even with the light popup ON. -> FIXED: box, border, hint and text colours follow HudTheme light palette when the light popup is ON (dark unchanged).
3. **Status question flips the theme**: "popup light hai?" (a question) matched `HudTheme.parse` as an ORDER and silently turned the light popup ON. -> FIXED: only a real order word (karo / kar do / on / off / wapas / badal ...) switches; questions fall through to the new honest status answer.

### P2 (polish / self-knowledge)
4. **GLM 5.3 model id lived in TWO files** (FreeProviders.OPENROUTER_GLM + FreeVision.GLM). If OpenRouter changes the id, the fix must be in ONE place (v7.151 prompt said so itself). -> FIXED: FreeVision.GLM now derives url + model list from FreeProviders.OPENROUTER_GLM.
5. **NOVA did not know its own brain**: no HelpGuide/MADAD topic for the GLM 5.3 Flash brain, and no spoken status for the popup theme. -> ADDED: HelpGuide topic `aibrain` (+ same topic in the MADAD CAPS list, kept equal by test_help_contract), MADAD rows for the brain and the popup theme, FeatureVoice status key `popuptheme` ("popup light hai ya dark?"), FeatureStatus row `popuptheme`.

### P3 (documented, NOT fixed — evidence or owner answer needed; no guessing per the rules)
6. **"samajh nahi aaya" on clear speech**: needs the "🎤 text" chat line + whether a Groq/Gemini key is set (test list item 12 already asks). No blind change.
7. **Watch connection**: root cause NOT proven; owner must answer the 3 questions in STEP8_STATUS_v7_148.md (watch model / what happens on press / what "nova watch ka haal batao" says). WatchDiag instrumentation is already in place to capture it.
8. **Hang after fresh install**: cause unproven; rule is fix only what the watchdog names. "nova atakne ka hisaab batao" + error-log COPY must be used first.
9. **Instagram false "blocked" and auto Jarvis/Hindi voice**: phone NOT TESTED; needs the owner's repro.
10. **OpenRouter GLM 5.3 free availability / speed**: NOT TESTED (network is not reachable from this sandbox). If it 404s/400s the id is now in ONE place (finding 4); if slow, lower the 6 s stagger in NovaService.

### Permissions / battery spot-check
- No new permission needed by anything added in v7.152 (Settings toggle + colours + status answers only).
- No main-thread network added; chat-box change is view-only code inside the existing overlay path.

## 4. PLAN DONE IN v7.152 (this ZIP)

Part 2 fixes: findings 1, 2, 3, 4. Part 3/4 self-knowledge: finding 5 (+ Settings row visible in Hinglish). Part 5: no new big feature — rule was "only after 1-4 are solid", and phone proof for v7.144-151 is still pending, so the safe upgrades are the self-knowledge and honesty items above. New files: PART_STATUS_v7_152_FINAL.md, OWNER_PHONE_TEST_LIST_v7_152.md, NEXT_ACCOUNT_PROMPT_v7_152.md, tools/test_v7152_light_popup_contract.py, JUnit additions (HudThemeTest isOrder, FreeProvidersTest single-source, FeatureVoiceTest popuptheme).
