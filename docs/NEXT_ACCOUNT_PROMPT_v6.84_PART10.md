# NEXT ACCOUNT PROMPT - NOVA v6.83 -> finish Part 10 (Parts 1-9 done in source)
Paste this whole file in the new chat with the latest NOVA-Voice-Assistant.zip.
OWNER RULE: NOVA does every job directly, no yes/no/permission chatter. Only pay/buy/password/OTP/bank screens stay refused. Every feature REAL, no demo/fake success. Hinglish short replies. Zip name exactly NOVA-Voice-Assistant.zip. Owner is a non-programmer Windows user: exact copy/paste steps, no giant code dumps. Never say DONE/VERIFIED without evidence.
## STATE (all run in the sandbox, nothing on a phone)
- `bash /ABS/tools/local_compile_check.sh /workdir` -> compile OK, 641 JUnit PASS. (android.jar: raw.githubusercontent.com is blocked; use the sparse git clone of github.com/Reginer/aosp-android-jar, android-34/android.jar into the workdir. The script needs an ABSOLUTE path.)
- `bash tools/run_all_checks.sh` -> 47/47. `python3 tools/ui_chat_check.py <out>` -> 19/19. `python3 tools/ui_feature_status_check.py <out>` -> 26/26.
- Part 9 DONE: see docs/AUDIT_PART9.md (bridge caps, ErrLog redaction, secret scan clean, permission list, SecureStore/UpdateManager/GitHub findings).
- Real Gradle/APK build still impossible in the sandbox. Owner must run GitHub Actions (.github/workflows/build.yml) or `./gradlew testDebugUnitTest assembleDebug` and send the log if red.
## PART 10 (to do)
1. docs/FINAL_TEST_REPORT.md with the regression table: per area JUnit class/test counts (run each class: voice 71, wake 18, phone control 68, calls/SMS/WhatsApp/music 33, timers/scheduled 11, driving/battery 24, chat sessions 40, AI fallback/providers 115, memory/learning/routines 114, agent 45, recording 19, image/site 12, updates 8, safety/logging/config 48, HUD 15 = 641). Mark each PASS (logic only) / NOT TESTED on device. No feature "complete" unless implemented AND verified.
2. versionName bump (app/build.gradle.kts, currently 6.80), CHANGELOG (v6.83 entry exists), README build instructions (GitHub Actions + `./gradlew`), docs/ROADMAP/PROGRESS update.
3. docs/REAL_DEVICE_CHECKLIST (step / expected / PASS-FAIL box) incl. Part 8: chat export file, old-chat migration, pin/rename/search, WebView recovery; plus Part 9: key save with overlong text, feature-status buttons open the right Settings screen.
4. Remaining limitations, nothing hidden. Final ZIP + continuation notes.

## UPDATE (v6.83 final): Part 10 docs were written in the same session (docs/FINAL_TEST_REPORT.md, docs/REAL_DEVICE_CHECKLIST_v6.83.md, README/BUILD, versionName 6.83). Remaining: only the owner-side real build + phone checklist; fix whatever the first CI log / checklist shows.
