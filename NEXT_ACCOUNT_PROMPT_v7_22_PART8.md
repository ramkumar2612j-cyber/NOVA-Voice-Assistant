# NOVA - NEXT ACCOUNT PROMPT (v7.22 = PART 8 of 10). Paste FIRST + attach the part zip. Full detail: NEXT_ACCOUNT_PROMPT_FULL_v7_22.md
Rules unchanged: owner NOT a programmer, Android phone only, Hinglish, SHORT, ONE step at a time, add-only, PASS / FAIL / NOT TESTED with evidence, never touch .github/workflows/build.yml, do NOT push until owner says so. Only branch `main`.

## PART 8 DONE
1. Whole Kotlin tree REALLY compiled (0 errors) + JUnit really run: 1077 tests, 3 fail (stale other-line, tools/KNOWN_STALE_JUNIT.txt).
2. Real gap fixed: voice "screenshot lo" now routes to the screenshot action (Logic.kt, add-only).
3. New Part8RulesTest.kt (8 tests PASS) + tools/test_part8_contract.py.
4. Connectors reviewed statically: no bug found, not tested live.
EVIDENCE: run_all_checks 87 PASS / 11 FAIL (known stale), render smoke PASS. NOT RUN: Gradle, APK, phone.
NEXT (Part 9): final integration (see FULL prompt).
