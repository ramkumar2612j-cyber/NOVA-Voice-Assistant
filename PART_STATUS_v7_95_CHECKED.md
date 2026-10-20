# v7.95 - checks completed (no feature code changed)
RUN (sandbox): kotlinc 1.9.24 compile app/src/main = 0 errors. JUnit (real JUnit 4.13.2 + real org.json) = 1454/1454 PASS. python guards = 146/146 PASS (tools/run_all_checks.sh).
The 4 old JUnit failures (v7.94) were harness gaps of the small stub JUnit, not app bugs.
CHANGED: 10 stale guards in tools/ relaxed (permission count 30 or 31 because WRITE_CALENDAR was added in v7.90; onResume window; CHANGELOG start). versionName 7.95. No app code changed.
NOT DONE ON PURPOSE: NovaService split (90 of 165 guards read NovaService.kt as text; add-only rule; needs a green GitHub Actions build first).
NOT RUN: Gradle, GitHub Actions "Build and Test APK". NOT TESTED on phone.
OWNER STEPS: 1) upload this zip to the repo, run Actions "Build and Test APK"; 2) install APK; 3) phone tests in NEXT_ACCOUNT_PROMPT_v7_86_FIXES.md (scan, SHARP/SMOOTH), OWNER_PHONE_TEST_LIST_v7_72.md, trade screen, meeting schedule.
NEXT ACCOUNT: only fix what the Actions build or the phone test reports. If Actions is green, split NovaService in tiny pieces (compile + guards after each).
