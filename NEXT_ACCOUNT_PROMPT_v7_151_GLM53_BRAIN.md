# NOVA NEXT ACCOUNT PROMPT (after v7.151 GLM 5.3 Flash brain)
Owner: Hinglish, not technical, Android phone only (Chrome + GitHub web + Termux, no PC). Repo: https://github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant (branch main). Package com.nova.assistant. versionName stays 7.138. Never touch CHANGELOG.md (test_part20 needs `## v7.13x` on top) or build.yml. This prompt continues NEXT_ACCOUNT_PROMPT_v7_150_FINAL.md (read it too: 10-step status + open items).

## RULES (compulsory, owner will not repeat)
Add-only, nothing removed. Everything real and connected to the UI, nothing half-done. Honest evidence: "NOT TESTED" for phone, "NOT CONFIRMED" for build, no fake PASS. After every part give the source ZIP + a full next prompt (this rule too). One small step at a time, short messages, Hinglish.

## WHAT v7.151 DID (details: PART_STATUS_v7_151_GLM53_BRAIN.md)
- New provider OPENROUTER_GLM (id openrouterglm, model z-ai/glm-5.3-flash:free, same OpenRouter key). FreeProvider got brainOnly + minTokens (4000). FreeProviders.forJob(providers, brain) drops it for short jobs and puts it FIRST for brain jobs (rich chat, agentChain brain=true, SiteGen, CloudTaskText, FreeVision after Groq). ProviderChain.run/runHedged got `brain`. FreeHttp: 45 s read for brainOnly. Rich chat hedge stagger 2.5 s -> 6 s.
- Tests: FreeProvidersTest +4, FreeVisionTest, 3 python contracts updated. OWNER_PHONE_TEST_LIST_v7_151_GLM53.md added.
- Full GLM 5.3 is paid (not added). Z.ai own site free status for 5.3 Flash unclear (not added to ZAI_MODELS).

## OPEN ITEMS (in order)
1. Push ZIP to GitHub, get ONE green build; fix every compile error fully (new code never compiled: FreeProviders brain/forJob, FreeVision.GLM, NovaService agentChain). Then smoke job AUTO_TEST_STATUS.md.
2. Owner runs OWNER_PHONE_TEST_LIST_v7_151_GLM53.md and OWNER_PHONE_TEST_LIST_v7_150.md.
3. If GLM 5.3 gives 404 / 400 on OpenRouter: model id may have changed; fix the id in ONE place (FreeProviders.OPENROUTER_GLM + FreeVision.GLM). If it is too slow: lower the 6 s stagger in NovaService runFreeChain.
4. Older open items 3-7 of the v7.150 prompt (watch questions, samajh nahi aaya, hang, light popup toggle, Instagram blocked).
