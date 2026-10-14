# Audit v6.81 (after Part 8) - honest status
## Real build facts
- Real Gradle / APK build: NOT POSSIBLE in the sandbox (Maven / Google / Gradle hosts blocked by egress policy, HTTP 403). Not run. Needs GitHub Actions (.github/workflows/build.yml) or the owner's PC.
- Local Kotlin compile + JUnit (tools/local_compile_check.sh, stubs for androidx/vosk/work): compile OK, 639 JUnit PASS (run BEFORE the v6.81 edits below; NOT re-run after them).
## Fixed in v6.81 (source edited, NOT re-compiled after the edit)
1. MainActivity: WebViewClient.onRenderProcessGone added (Android kills the WebView on low-RAM phones; before, the whole app crashed). Rebuilds the page once per 5 s, else closes. NOT TESTED on a phone.
2. NovaService: context receivers now go through registerSysReceiver() with RECEIVER_NOT_EXPORTED on Android 13+ (targetSdk 34 rule). Uses android.content.Context fully qualified (this file has no Context import).
3. app/build.gradle.kts versionName 6.80.
4. Gradle wrapper added (gradlew, gradlew.bat, gradle/wrapper/*, Gradle 8.7). Generated offline; never executed against the real distribution.
5. README: "Keys kahan daalni hain" section.
6. New tools/test_android_runtime_contract.py (8 static checks, 8/8 PASS).
## KNOWN FAIL to fix first (1 line)
tools/test_part1_voice_flow.py line 30 still greps `(?<!un)registerReceiver\(batteryLowRx`; the code now calls `registerSysReceiver(batteryLowRx, ...)`. Change the regex to `registerSysReceiver\(batteryLowRx` (expect count 1). run_all_checks.sh result at this moment: 45 pass, 1 fail (this one).
## Audit findings (no change made)
- Manifest: 7 exported components (launcher activity, boot receiver, 4 system-bound services + recognition stub) - expected. allowBackup=false. All PendingIntents carry IMMUTABLE/MUTABLE. FGS types match permissions. No network_security_config (default: cleartext blocked at targetSdk 34 - OK).
- Permissions possibly over-broad (LIST only): QUERY_ALL_PACKAGES, REQUEST_INSTALL_PACKAGES, SYSTEM_ALERT_WINDOW, USE_FULL_SCREEN_INTENT, WRITE_SETTINGS - owner requested several; do not remove without asking.
- JS bridge: 159 methods; ~30 String parameters have no explicit .take() cap (saveKeys, cancelSchedule, saveGithubToken, openPublished, setFeedUrl, saveSettings, teachAdd, ...). Severity LOW: the page is a local asset, navigation and sub-requests are blocked (hardenWebView). Fix in Part 9 by capping each (id <= 40, keys <= 400, json <= 20000, url <= 300).
- Config status panel already exists (SAARE FEATURES box, FeatureStatus.json via getFeatureStatus). Part 9 item (2) is therefore mostly DONE; verify it lists missing keys + permissions.
- Not verifiable here: androidx/WorkManager/Vosk/Porcupine/llama real APIs (local stubs only), manifest merge, R8, device behaviour.
