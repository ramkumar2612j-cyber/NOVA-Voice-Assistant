# NOVA v7.119 - Part B (lite series: mic loop allocation)

Base: v7.119 Part A zip. versionName stays 7.114 on purpose.

## Kya kiya
1. `PreRollRing.kt` (pure Kotlin): 5 reused arrays, oldest dropped, `size / arrayAt / lengthAt / clear`.
2. NovaService mic loop: `pre.add(buf.copyOf(n))` + `removeAt(0)` hata kar `pre.add(buf, n)`. Ab har 100 ms par naya array nahi banta.
3. Dono readers (gate replay, wake-capture replay) ab `lengthAt` use karte hain (reused array chunk se lamba ho sakta hai).
4. `WakeGateRules.replayCount` (sab purane chunks, sabse naya chhodkar) ko haath nahi lagaya.
5. NEW: PreRollRingTest (10 JUnit), tools/test_v7119_partb_contract.py (10 static checks), OWNER_PHONE_TEST_LIST_v7_119.md, CHANGELOG entry.

## Evidence level
- written: YES
- statically checked: test_v7119_partb_contract.py + test_v7119_gate_contract.py RUN, PASS.
- Kotlin compile: RUN (kotlinc 1.9.24 + android-34 jar, tools/local_compile_check.sh) -> main code 0 errors (NovaService included).
- JUnit: PreRollRingTest + WakeGateRulesTest = 18/18 PASS, but on a tiny Kotlin JUnit stub, not real JUnit. Full harness: 1579 pass, 33 fail in OTHER test files (ConnectorParseTest, WebBrowsersTest, WebSourcesTest, TradeEngineTest, VercelPublishTest, MusicCatalogTest, ...). Not compared with the original zip, so the cause is not proven; none touches the mic / wake code.
- GitHub Actions: NOT RUN. Phone: NOT RUN.

## Jo badla nahi
versionName, thresholds (300 / 1.8 / 2 s), Logic.wakeVerified, WakeGuardRules, WakeAdapt, ui.html, "never ask" defaults.
Naya voice feature nahi, isliye HelpGuide / CAPS / SKILLS entry ki zarurat nahi.

## Phone par kya dekhna
OWNER_PHONE_TEST_LIST_v7_119.md (wake, pehla shabd, temperature, lambi chat scroll). Phone par heat/lag kam hua ya nahi: andaaza, naapa nahi.
