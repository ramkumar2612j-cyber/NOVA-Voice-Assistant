# NOVA v7.110 - PART 8 of 8 (final tests, docs, zip)
- CHANGELOG v7.110 entry, `OWNER_PHONE_TEST_LIST_v7_110.md` (Parts 1-7, 11 phone steps), README pointer.
- Fixed a stale test: `test_part20_contract` wanted CHANGELOG to start with v7.7x-v7.9x; now also accepts v7.10x / v7.11x (it was failing on the original v7.109 zip).
- Full suite (tools/test_*.py, smooth tests skipped): 148 PASS expected, 1 FAIL: `test_part4_fx_render` (stars spring-back timing in headless chromium; identical failure on the untouched v7.109 zip; animation timing, not a Part 1-7 change; NOT investigated further).
- Kotlin compile, JUnit, GitHub Actions, APK, phone: NOT RUN. Treat everything runtime as unverified until the build log and phone list come back.
- Open owner question: 5 pack models still auto-download on Wi-Fi + charging (`ExtCfg.autoModels`); only 3B is tap-only.
