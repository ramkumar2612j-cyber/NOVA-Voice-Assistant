# NOVA next part (paste this as the first message and attach NOVA-Voice-Assistant.zip)

Owner: not a programmer, Android phone only (Chrome + GitHub website). Reply in Hinglish, short, ONE step at a time, tap-by-tap with exact github.com links. Full files in chat or full ZIP. Say clearly what is NOT TESTED.
Compile only on GitHub Actions: github.com/skfashion142/NOVA-Voice-Assistant, workflow "Build NOVA from zip".

## DONE (v6.95, folder nova_part4_work)
- v6.92 app search rules, v6.93 Gmail (no login), v6.94 automation: all written, NOT compiled, NOT phone tested.
- v6.95 PRO BUTTONS: modern button layer + tap flash, button wiring audit (0 missing bridge methods).
- v6.95 ROZ KA SUDHAAR: non-Gemini AI, 1 hour per day window, proposals need HAAN, WAPAS undoes. Default OFF.
- CI fix: two stale Python contract checks (versionName, permission count 23).

## STATUS TABLE
- Python contracts 55/55: PASS (run locally)
- Kotlin compile: NOT TESTED
- JUnit (incl. SelfImproveRulesTest): NOT TESTED
- Phone: NOT TESTED for 6.91-6.95

## PENDING
0. PART B (next, one step): more free AIs in the chain with auto fallback. Candidates (OpenAI-style endpoints, NOT verified offline, each needs its own free key slot): Cerebras, Mistral, GitHub Models, NVIDIA NIM. Touch points: FreeProviders.kt (entry + ordered()), the keyOf `when` blocks in NovaService.kt (4 places), SelfImprove.kt, SiteGen.kt, AutoSetup.kt, CloudTaskText.kt, ApiUsage.kt, ProviderRanker.kt, AiChoice.kt, FeatureStatus/FeatureCheck, ui.html key inputs, and the tests that pin the exact provider list (FreeProvidersTest, ProviderRankerTest). Refactor the repeated `when` into ONE function first.
1. Build result of 6.95 from GitHub Actions; fix exact errors first.
2. Hinglish+Hindi+English mixed understanding.
3. Separate Groq key for self-improve (currently reuses the main Groq key).
4. Scheduled voice tasks (2 pm WhatsApp message).
5. Silent automatic fallback on failure + phone notification alert.
6. NOVA own issue-check report.

## ARCHITECTURE NOTES
- Self-improve can only add TeachStore aliases (no code, keys, contacts, settings). Providers: FreeProviders.ordered() filtered by SelfImproveRules.allowedProviders.
- Window: ExtCfg.selfImproveHour; NovaService.selfImproveLoop checks every 15 min; SelfImprove.run decides.
