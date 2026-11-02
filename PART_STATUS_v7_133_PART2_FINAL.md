# v7.133 PART 2 FINAL - remaining Part 2 items written
Source: NOVA-v7_133-PART2.zip. This session had NO kotlinc and NO network: everything below is WRITTEN ONLY. COMPILE: NOT TESTED. JUNIT: NOT RUN. PHONE: NOT TESTED.

Done in code (all NOT COMPILED):
- Verb-first call (Logic.kt): "phone karo papa ko", "call karo papa", "call lagao mummy ko".
- TradeRules: stop loss / usd inr joined, "aaj market kaisa hai", closeAsk ("trade close karo" opens MT5, never a PLACE request); NovaService.runTradeAnalysis closeAsk branch.
- ReelStopRules.kt (new) + NovaService.stopReels; CalendarQueryRules.kt (new) + CalendarRead.dayText/nextText; both wired in handle() before ReelRules.
- VoiSpark: VoiSparkSpeaker.generation() + speak(startGen); VoiSparkApi.findAudio prefers "audio" keys, skips callback/avatar/image keys.
- JUnit added: VoiceRouteAuditTest (4 new), VoiSparkApiTest (1 new). Expected by hand-trace to pass; never executed.
- Docs: CHANGELOG, OWNER_PHONE_TEST_LIST_v7_133.md, NEXT_ACCOUNT_PROMPT_v7_133_PART3.md updated.

Still open: test_part4_fx_render (sandbox-only suspect); full tools/run_all_checks.sh run to the end; real compile + 1644+5 JUnit; phone tests.
Risk to watch at compile: NovaService (new vals calQ / reelStop, stopReels uses media().msg), TradeRules (var n), VoiSparkApi (urlPri captured var).
