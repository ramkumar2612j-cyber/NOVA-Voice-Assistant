# STEP 10 OF 10 (v7.150): FINAL AUDIT + SMOKE PATCH + PHONE TEST LIST
Owner order 30 Oct. Add-only. CHANGELOG.md and build.yml NOT touched. versionName stays 7.138.

## What this step did
1. tools/emulator_smoke.sh (add-only, 2 blocks): new rows "No native crash (30 s)" and "No native crash (taps)" (logcat "Fatal signal"), and INFO rows "ANR reason (30 s/taps)" with the Reason / PID / Load lines of an ANR (also saved as anr_reason_*.txt). Old rows unchanged.
   Evidence: `bash -n` OK; run against a FAKE adb that prints an ANR + "Fatal signal 11": the new rows show FAIL / INFO with the right text. Not run on a real emulator (needs GitHub).
2. Final audit (files): AgentScore, DirectRoute, LearnedRouteRules/Store, VisionTapRules, ScreenIdRules, MainStallWatchdog, StallRules, BlockedRules/Snapshot, WatchDiag, HudTheme all exist in app/src/main and each has its test file where it is pure Kotlin.
3. Contract scripts: 162 total. 161 PASS (test_part4_fx_render needs playwright, NOT run). Under parallel load test_part6_smooth_contract and test_part5_ui_screens fail (timing / sizing under load); both PASS when run alone.
4. OWNER_PHONE_TEST_LIST_v7_150.md written (steps 1-9). Settings toggle for the light popup NOT added (owner has not said he likes the light popup yet).

## NOT DONE / NOT PROVEN
- Android build for v7.144-150: NOT CONFIRMED. Phone: NOT TESTED. Watch root cause: NOT proven.
- Kotlin files that need Android (NovaHud, NovaService, ExtCfg, FeatureVoice, MainActivity) are not compiled in this sandbox.
