# NOVA - NEXT ACCOUNT PROMPT (v7.21 = PART 7 of 10). Paste FIRST + attach the part zip. Full detail: NEXT_ACCOUNT_PROMPT_FULL_v7_21.md
Rules unchanged: owner NOT a programmer, Android phone only, Hinglish, SHORT, ONE step at a time, add-only, PASS / FAIL / NOT TESTED with evidence, never touch .github/workflows/build.yml, do NOT push until owner says so. Only branch `main`.

## PART 7 DONE (static + headless Chrome render, no phone)
1. 6 HelpGuide topics added (Kotlin + CAPS equal, 40 topics) + JUnit HelpGuidePart7Test (not run).
2. ui.html add-only p7_polish CSS (dark color-scheme, chip scroll hint, tap sizes, card entrance, lowfx list painting, wide screens).
3. NEW real render test tools/test_ui_render_smoke.py: 4 phone sizes x 5 tabs, 0 JS errors, 0 overflow.
4. Old 11 failing guards labelled in tools/KNOWN_STALE_GUARDS.txt (kept).
Light theme NOT built (dark-only app).

## EVIDENCE
RUN: run_all_checks.sh 86 PASS / 11 FAIL (all known stale), render smoke PASS, ui + help + part6 contracts PASS, node --check OK. NOT RUN: Kotlin compile, JUnit, Gradle, APK. NOT TESTED: phone.

## NEXT (Part 8)
Real-logic audit round 2 (see FULL prompt section 6).
