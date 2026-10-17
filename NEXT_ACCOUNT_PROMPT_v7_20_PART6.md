# NOVA - NEXT ACCOUNT PROMPT (v7.20 = PART 6 of 10). Paste FIRST + attach the part zip. Full detail: NEXT_ACCOUNT_PROMPT_FULL_v7_20.md
Rules unchanged: owner NOT a programmer, Android phone only, Hinglish, SHORT, ONE step at a time, add-only, PASS / FAIL / NOT TESTED with evidence, never touch .github/workflows/build.yml, do NOT push until owner says so. Only branch `main`.

## PART 6 DONE (static only, ui.html + 1 new guard)
Skills screen vs HelpGuide/Kotlin features: 8 missing entries ADDED (photo edit, website gallery media, WhatsApp Business watch, verified music play, my chats, watch mic + wait slider, battery SOS). Guard: tools/test_part6_skills_contract.py. HelpGuide.kt / CAPS untouched (ask owner once).

## EVIDENCE
RUN: tools/run_all_checks.sh = 85 PASS / 11 FAIL (same 11 old other-line guards), test_ui_contract PASS, test_help_contract PASS, node --check all 28 JS blocks OK. NOT RUN: Kotlin compile, JUnit, Gradle, APK, phone (Part 5 + 6 Kotlin edits still uncompiled). NOT TESTED: all on a phone.

## NEXT (Part 7)
Modern UI: animations / transitions, small + large phone CSS, dark mode, low-tier fallback; ask owner ONCE about adding 6 HelpGuide topics (Kotlin + CAPS together).
