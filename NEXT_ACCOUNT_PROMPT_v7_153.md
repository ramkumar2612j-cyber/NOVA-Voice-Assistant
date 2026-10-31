# NEXT ACCOUNT PROMPT — v7.153 WIFI KIT (naye chat/account ke liye poora context)

## Tu kaun hai
Tum NOVA project ka agla AI assistant ho. NOVA ek Android (Kotlin, package `com.nova.assistant`) local-first voice assistant hai jo Hinglish/English bolti hai. Repo `nova_work/` me hai, version 7.153, owner ne GitHub Actions se APK banwata hai (`OWNER_SETUP_AND_APK_STEPS.md` section G). Pehle `docs/HANDOFF.md` aur `PART_STATUS_v7_153_WIFI.md` padho.

## v7.153 me kya hua (owner order: "sab add kr do" — WiFi ke 5 legal tools)
- **WiFi Kit** ban gaya: scan (paas ke networks: signal/band/security), best-switch (sirf OWNER ke apne saved networks, +8 dB gain), open-network connect (sach me khule network), Wi-Fi QR make/read (zxing core 3.5.3; read = QR photo NOVA se share), doctor (validated + captive-portal 204 probe), password store (SecureStore slots, max 12, encrypted), optional WIFI GUARD (default OFF, 60 s tick, 10 min cooldown).
- Files: NAYE `WifiKitRules.kt` (pure, 12 kinds), `WifiKit.kt` (Android), `WifiKitStore.kt`, `WifiKitTest.kt` (30 tests), `tools/test_wifi_kit_contract.py`. CHANGED: Logic.kt, NovaService.kt, AndroidManifest.xml (+5 permissions, NEARBY_WIFI_DEVICES neverForLocation), MainActivity.kt (Location round + wifiGuard + QR share hook), ExtCfg.kt, SetupChainRules.kt (VERSION 3), FeatureStatus.kt, HelpGuide.kt, ui.html (s_wguard + CAPS wifi), app/build.gradle.kts (versionName 7.153).
- **LEGAL LINE (kabhi mat todna)**: owner ne pehle "nearby wifi bypass" manga tha — MANA KIYA GAYA (unauthorized access = IT Act 2000/PECA crime). Owner ne "khud use ke liye" bola — tab bhi mana kiya, phir owner ne legal pack choose kiya. Koi crack/bypass/guess tooling kabhi mat jodna; `test_wifi_kit_contract.py` forbidden-words check build FAIL karta hai.

## Abhi ki imaandaari (v7.153)
- RUN (sandbox): contract scripts (run_all_checks.sh — final count `PART_STATUS_v7_153_WIFI.md` me).
- NOT COMPILED: sandbox me kotlinc/Android SDK nahi — 4 nayi Kotlin files pehli GitHub build me compile hongi.
- NOT RUN: GitHub Actions APK. NOT TESTED: phone (list: `OWNER_PHONE_TEST_LIST_v7_153.md`).

## Agla kaam (order me)
1. Owner ko zip do → GitHub push → Actions build GREEN → APK install.
2. Owner se phone test list v7.153 ke results lo (PASS/FAIL per number).
3. Known open items: Android 10+ suggestion popup ka asli behaviour, QR read kharab photo par, scan throttle (Android 9+ 4 scans/2 min).
4. Agla feature sirf owner ke bole par; add-only rule: purane behaviours mat todo, har naye feature ke saath contract test + JUnit + docs (CHANGELOG top, PART_STATUS, OWNER_PHONE_TEST_LIST, NEXT_ACCOUNT_PROMPT).
