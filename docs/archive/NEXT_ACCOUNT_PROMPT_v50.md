# NOVA NEXT ACCOUNT PROMPT v50
Base: read NEXT_ACCOUNT_PROMPT_v49_VOICE_LOCK_HARD_TEST.md fully (rules, truth rule, hard rules, owner steps) - everything there still applies. Everything real and working, nothing half-done. Truth rule: never write DONE/WORKING/FIXED without an evidence level.

## What changed in 6.30 (Kotlin NOT compiled here; 27 tools/test_*.py pass in the sandbox; CI not yet run)
- Owner report (app 6.29 SAB TEST): "NOVA sunta hai par reply nahi deta, aur khud jaag jaata hai". Self test: voice lock ON, chup mode ON, "4 baar anjaan awaaz chup-chaap block hui" (HYPOTHESIS: some were the owner's own voice, short commands, STRICT limit 0.40 never proven). Gemini 429/503 in RECENT ERRORS (quota; fallback chain exists).
- ExtCfg.convo: default now OFF, new pref key convo_mode_v2 (old default-ON value of existing installs dropped). Cause of auto-listening (HYPOTHESIS): follow window listened up to 3x without wake word.
- Voice check now gets wake-word audio too (vpre ~1.7 s, newest 0.5 s excluded because already in pcm): NovaService wakePcm/vcheck. Threshold NOT changed.
- Silent reject (chup mode) still no speech/HUD/AI/cloud, but writes ONE local chat line with "Awaaz match d (seema t)" so the owner can see the numbers.
- Self test INFO "Auto-wake diagnosis": wake count, empty-wake count, wakes without wake word, conversation mode state.
## Still open
1. Real "Awaaz match" numbers (owner long command, second person) - needed to judge the 0.40 limit; use Settings > AWAAZ TEST to tune.
2. Unprompted speech audit (Message mode/Driving, reminders, alerts) not done.
3. Hard 5-minute self test items from v49 section 6.3 not done; in-app update skipped by owner.

## Also in 6.30: three GLM keys
- FreeProviders.ZAI2 / ZAI3 (ids zai2, zai3, key slots "zai2"/"zai3"), chain order Groq, Qwen, GLM, GLM#2, GLM#3, OpenRouter, Pollinations. When key 1 hits its limit (429 on both models -> cooldown) the chain tries key 2, then key 3. Key lookups added in NovaService (4 places), AutoSetup, CloudTaskText, SiteGen, ApiUsage rows, FeatureCheck. Settings: 2 extra inputs (ui.html), MainActivity saveZaiKeyN/hasZaiKeyN/clearZaiKeyN. Kotlin test glmKeysFailOverOneAfterAnother + FreeProvidersTest order updated (NOT compiled here, CI will tell).

## Also in 6.30: home status box, startup popup, smoothness (owner request 2026-10-12)
- FeatureStatus.kt + MainActivity.getFeatureStatus(): real state rows (permissions, service, TTS, keys, last Gemini error via ErrLog.latest). ui.html box #fstat on the home page with THEEK KARO buttons. It reports STATE only, it never runs a feature (SAB TEST KARO does that).
- Setup Center popup is back: ucStartup() once per launch (after the intro ends) only if a needed permission is missing; never on resume; it grants nothing (owner taps). tools/test_no_automatic_health_popup.py extended accordingly (v42's "no popup" decision was reversed on the owner's request).
- Smoothness: global backdrop-filter:none, lighter sheet keyframes, intro canvas DPR cap 1.5 and glow (shadowBlur via SBK) auto-off when EMA frame time > 22 ms. HYPOTHESIS: blur + canvas glow caused the stutter; not phone tested.
- NOT done: "fully automatic agent" (owner wish): needs a concrete list of tasks; hard rules (spoken yes for risky actions, no silent permission grants) stay.

## Auto-Heal (owner: "fully automatic agent, risky kuch nahi, tum banao")
- NovaService.heal() every 30 s (healRun): (1) speech engine not ready twice in a row -> initTts() again; (2) mic gives no audio for 60 s (not during video recording) -> one note in the home box, plus a note when it recovers; (3) voice lock ON, profile saved, speaker model missing -> AutoSetup.start (single runner). Notes via healNote()/healInfo(), shown in the home box (FeatureStatus rows "heal" and "wakediag"). It does NOT restart the mic service (Android may refuse a mic FGS from the background), does NOT grant permissions, does NOT flip user settings. Hard rules unchanged: risky actions still need the spoken yes (owner said nothing is risky, but these rules are in the project's hard rules, so they were kept).
- Kotlin not compiled, not phone tested.
