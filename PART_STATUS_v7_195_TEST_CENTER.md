# PART STATUS v7.195 - TEST CENTER + test-script repair

## Kya bana (sach, saboot ke saath)
- ui.html: TEST CENTER card (Settings > TEST REPORT ke upar). Ek button teen kaam chalata hai: Android.selfTest() -> Android.verifyNow() -> Android.fullReport(). Ek status chip + kharab items + poori report + COPY/SHARE. Purane onSelfTestDone / onFeatureVerify / onAutoTest chain hote hain (replace nahi).
- 26 purane tools/test_*.py theek kiye. Wajah: full rename ke baad `com/jarvis/assistant` path, hardcoded version lists, ek variable (`jarvis`) ka overwrite, ek stale exported-count (8 -> 9, LauncherActivity), barge-in ka purana stop-grammar pin (v7.187 ne open vocabulary kiya).
- IMPORTANT: build.yml "Source contract checks" step run_all_checks.sh chalata hai; koi naya FAIL = exit 1 = APK nahi banta. Ye 26 fail pehle APK rok sakte the.
- versionName 7.195. App ka koi Kotlin code nahi badla.

## Test level
| Test | Level | Result |
|---|---|---|
| tools/audit_dead_ui.py | static | 0 dead controls |
| tools/test_test_center_contract.py | static | PASS |
| Test Center card, nakli bridge, headless Chromium | machine (phone nahi) | PASS (self -> verify -> full, chip sahi, copy ok) |
| 152+ contract scripts | machine | dekho CI log / TEST_STATUS |
| Kotlin compile | NOT RUN | sandbox me kotlinc nahi |
| Gradle build / unit tests | NOT RUN | GitHub Actions chahiye |
| Phone par Test Center | NOT RUN | owner test |

## Jo NAHI bana
1. Jarvis ka khud GitHub se APK banwana (workflow dispatch + wait + download + install tap) abhi nahi bana. Pehle se hai: branch push + run poll (GitHubUpgradePublish.kt), APK install (UpdateManager). Beech ka dispatch + release-asset download jodna baaki hai.
2. "Profit" ki guarantee koi code nahi deta. Payment / OTP / password HARD DENY.

## Zip ke waqt khule items
- test_native_smooth_contract.py: sequential CI-jaise run mein FAIL, akele PASS (timing). test_perf_governor_contract.py: parallel run mein ek baar FAIL, akele PASS.
- run_all_checks.sh poora nahi dekha (119 PASS tak). Kotlin compile NOT RUN. Phone test NOT RUN.
