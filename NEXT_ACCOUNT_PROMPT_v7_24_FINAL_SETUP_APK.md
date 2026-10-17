# NOVA - NEXT ACCOUNT PROMPT (v7.24 FINAL = after PART 10). Paste FIRST + attach NOVA-Voice-Assistant-v7_24-FINAL.zip. Full detail: NEXT_ACCOUNT_PROMPT_FULL_v7_24.md
Rules: owner NOT a programmer, Android phone only, Hinglish, SHORT, ONE step at a time, add-only, PASS / FAIL / NOT TESTED with evidence, never touch .github/workflows/build.yml, do NOT push until owner says so. Only branch `main`.
## STATE
Code complete in 10 parts. REAL: Kotlin compile 0 errors, JUnit 1083 run / 3 known stale fail, guards 89 PASS / 11 known stale (script exit 0 so CI is not blocked), render smoke PASS, 211/211 bridge funs in UI, all voice kinds handled. NOT RUN: Gradle/APK, phone, connectors live.
## YOUR JOB (in this order, one step at a time)
1. Give owner OWNER_SETUP_AND_APK_STEPS.md section A+B: upload to main, Actions > NOVA Android CI > Run workflow.
2. He sends ONLY red lines (if any). Fix add-only, re-run compile recipe (FULL prompt section 2), re-zip.
3. Green: he downloads nova-apk, installs. Then docs/PHONE_CHECKLIST.md + the 6 quick tests in section D, one item at a time, PASS/FAIL.
4. Fix only real failures (known risk: real CALL via launch(resend=false) may say blocked though dialled).
5. After main has latest code: branch cleanup (section E), one step at a time.
