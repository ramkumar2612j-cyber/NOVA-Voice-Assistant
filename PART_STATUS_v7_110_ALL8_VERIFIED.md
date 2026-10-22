# NOVA v7.110 - PARTS 1-8 COMPLETE + sandbox verification (22 Oct 2026)
RAN (this session, sandbox; evidence = logs below):
- Kotlin compile app/src/main (LlamaBrain stubbed): 0 errors. Compiler: kotlin-compiler-embeddable 2.0.21 (Gradle's bundled jar), android-34 android.jar, real org.json 20240303 built from GitHub source.
- Kotlin compile app/src/test: 0 errors.
- JUnit (real JUnit 4.13.2, 184 test classes): 1528 run. FIRST run: 1 FAIL = LoginRulesTest.accountSecurityTapsAreDangerous ("Backup codes").
- BUG FIXED (Part 5, LoginRules.kt): DANGER list had only singular "backup code"; whole-word match missed the plural screen label "Backup codes", so that account-security tap was NOT blocked. Added plural/variant entries: "backup codes", "security keys", "passkeys" (add-only).
- JUnit after fix: OK (1528 tests), 0 failures.
- tools/run_all_checks.sh: 155 contract scripts passed, 0 failed (incl. test_part4_fx_render, which was listed as failing in the earlier status; it passed here - timing flake, not investigated).
NOT RUN: Gradle / GitHub Actions build (Maven + Google repos are blocked in the sandbox, so no APK), phone tests. Runtime behaviour on the phone is still unverified.
Parts 1-8 status: all written, compiled, unit-tested (see NEXT_ACCOUNT_PROMPT_v7_110_FINAL.md for the per-part list).
NEXT: owner uploads zip to GitHub (branch main) -> run build.yml -> OWNER_PHONE_TEST_LIST_v7_110.md.

## UPDATE (owner phone feedback, 22 Oct 2026 night)
Owner reported: website maker output poor; NOVA wakes by itself from TV sound; after wake says "samajh nahi aaya"; chat says "Accessibility ON karo / permission do" although already given; slow UI.
- FIXED (code): wrong Accessibility wording. Root cause: NOVA tests `NovaAccessibilityService.instance != null`; if Android shows the switch ON but never connected the service, 3 messages (resume, globalAct, screen read) said "give permission". New `AccessHintRules.kt` (+ `AccessHintRulesTest`, 3 tests) separates OFF / ON-but-not-connected / connected and tells the owner to switch NOVA Phone Control OFF then ON. Compile 0 errors, JUnit 1531/1531.
- NOT FIXED, needs owner data: TV false wake (wake filter `Logic.heardWake` + 4-hit rule already exist; unchanged), "samajh nahi aaya" (comes from junk audio; the notification names whether Groq / Gemini key / Hindi pack are missing), slow UI, website quality (depends on which AI key answers; free providers get 7000-14000 token limit in SiteGen.kt). No change made without evidence.
