# v7.127 - sharp Ultron, popup mouth, faster thinking + speaking (versionName stays 7.114)

DONE:
1. Home Ultron photo: assets/ultron_hero.jpg 440x340 -> 1320x1020 (3x Lanczos + denoise + sharpen, same colours). Sharper edges, but the source is only 440 px, so no new detail can appear. For true HD the owner must send a bigger Ultron picture. Mouth position still correct (same aspect).
2. Popup mouth: NovaHud.kt draws a red-glow mouth on the round Ultron head while the state is speaking (MouthMath.kt, rhythm from time, ticker already redraws every frame).
3. Faster thinking:
   - FreeProviders.kt: ProviderChain.run/runHedged new `hedgeStaggerMs` + `rich` support. Long typed answers now ask 2 AI providers; the 2nd one starts only if the 1st is silent after 2.5 s (little extra quota).
   - NovaService.agentChain (agent steps) also hedged (2nd provider after 2 s).
   - Voice answers and action planning were already asked to 2 providers at once (unchanged).
4. Faster speaking: UltronChunks.kt + NovaService.startUltron: the reply is cut at sentence ends; only the FIRST piece (<= 130 chars) is made into Ultron audio before NOVA starts talking, the rest follows piece by piece. STOP / new reply cuts it; if a later piece fails the normal voice reads the rest.
5. Tests added: UltronChunksTest, MouthMathTest (JUnit, NOT RUN here). Python guards: all tools/test_*.py PASS (the browser ones need ~150 s) ALL pass (test_perf_governor_contract was stale vs v7.124 and is updated: SHARP may reach level 4 after real stalls).

NOT DONE / NOT TESTED: Gradle compile (no Android SDK here), phone test of popup mouth, chunked Ultron voice (gaps between pieces?), real speed gain on the phone.
Owner test: 1) ask a long question by typing: answer should come sooner. 2) Ask something with a long spoken answer: first words should start quickly. 3) Popup while NOVA speaks: mouth moves on the small head. 4) Home Ultron: sharper, mouth moves when speaking.
- v7.128: perf_governor test updated to the v7.124 rule; all tools/test_*.py PASS.
