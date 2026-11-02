# v7.153 WIFI KIT — status (imaandaari se: kya RUN hua, kya NAHI)

Owner order: "sab add kr do" — WiFi Kit ke 5 legal tools NOVA me jod diye. Koi bypass/crack feature NAHI hai (wo pehle hi mana kiya gaya tha aur nahi banega).

## Kya bana

Naye files (3 + 2 test):
- `WifiKitRules.kt` — PURE Kotlin (koi android import nahi): scan-reply builder, security/band/bars classification, best-switch (+8 dB gain), guard cooldown decision, Wi-Fi QR payload make/parse (escape support), doctor ke imaandaar jawab, command grammar. 12 command kinds (`wifi_scan` ... `wifi_forget`).
- `WifiKit.kt` — Android layer: WifiManager scan (Location permission ke bina khali), ConnectivityManager validated flag, captive-portal 204 probe (apne thread par), join = API 28- WifiConfiguration / API 29+ WifiNetworkSuggestion (honest reply: system approve karega), Settings.Panel ACTION_WIFI, zxing se QR PNG bana/read, Share->NOVA QR auto-detect, Guard (60 s tick, ExtCfg switch, default OFF).
- `WifiKitStore.kt` — owner ke apne networks (max 12): naam chhoti JSON file me, password `SecureStore.saveSlot` (Keystore AES-256-GCM) me. Forget = naam + slot dono saaf.
- `app/src/test/.../WifiKitTest.kt` — 30 pure tests (classification, scan reply, best switch, guard cooldown, QR round-trip + escapes, doctor, grammar + jo match NAHI hona chahiye + forbidden-words guard).
- `tools/test_wifi_kit_contract.py` — poori wiring + LEGAL LINE contract.

Changed files (10 + 13 contract scripts): Logic.kt (classify me `WifiKitRules.parse(text)` purane wifi on/off rule se PEHLE), NovaService.kt (runCommand me `in WifiKitRules.KINDS -> WifiKit.handle(...)`, onCreate/onDestroy me Guard start/stop), AndroidManifest.xml (5 naye permissions + neverForLocation flag), MainActivity.kt (Location permission round, wifiGuard get/save, shareImageUris + QR hook dono entry points), ExtCfg.kt (wifiGuard default OFF), SetupChainRules.kt (VERSION 3), FeatureStatus.kt (wifi row), HelpGuide.kt (wifi topic), ui.html (s_wguard checkbox + collect/fill + CAPS wifi entry + Skills screen me "WiFi Kit" card), app/build.gradle.kts (zxing core 3.5.3, versionName 7.153).

Contract script updates (purane pins jo naye badlaav ke saath aage badhe — koi guard kamzor NAHI kiya):
- 8 tests: permission-count pin (30,31) -> (30,31,35,36) [5 nayi permissions]; versionName pin me '7.153' juda (test_capability_learning, test_part1_approval, test_part2_playbook, test_part3_alternatives, test_part4_website_operator, test_part5_login, test_part6_site_learning, test_part7_brain_learned).
- test_part6_skills_contract: MAP me wifi -> Skills screen me naya "WiFi Kit (scan, switch, QR, doctor)" card.
- test_part20_contract: CHANGELOG top prefix list me v7.14/v7.15.
- check_p7_motion: v7.152 ka light-popup change `bg.colors = if (lightBox)...` tha, par guard purana literal `bg.colors = intArrayOf(` mangta raha — DELIVERED v7.152 zip me ye test ALREADY FAIL tha (audit ka 163/163 claim is script par galat tha). Ab dono branches check hote hain (light branch + dark glass colour bana rahe).

## Voice commands (kuch examples)

| Bolo | Kya hota hai |
|---|---|
| "nova wifi scan karo" / "nearby wifi dikhao" | paas ke networks: signal X/4, band, WPA2/WPA3/FREE |
| "kaunsa wifi laga hai" | abhi ka connection: naam, signal, internet chal raha/verify nahi |
| "best wifi pe jao" | aapke saved networks me strong wale pe switch |
| "wifi switch karo jiofiber" | us naam wale network pe (password saved ho to khud, warna panel + honest reply) |
| "free wifi pe connect karo" | sach me khula network join (na mile to saaf mana) |
| "wifi qr banao" / "jiofiber wifi ka qr banao" | QR PNG + share sheet |
| "wifi qr scan karo" | QR photo NOVA se share karne ka tarika batata hai (share karte hi padh leta hai) |
| "wifi password jiofiber ka 12345678" | password encrypted save (8-63 chars rule) |
| "wifi theek karo" / "internet kyun nahi chal raha" | doctor: WiFi band / no network / captive portal / unverified / kamzor signal / sab theek |
| "wifi guard on karo" | optional auto-switch (sirf aapke saved networks, 10 min cooldown) |
| "wifi password jiofiber bhool jao" | saved password + naam hata do |

Jo jaan-boojhkar NAHI match hota: "wifi on/off karo", "wifi kholo", "wifi settings kholo" — ye purane Android-settings page route par hi chalte rahenge. "wifi scan mat karo" jaise negated orders bhi match nahi hote.

## Evidence — IMAANDAARI SE

- RUN (sandbox): poora `tools/run_all_checks.sh` = **168 passed, 0 failed, 0 known stale, exit 0** (isme naya `test_wifi_kit_contract.py` bhi PASS hai).
- **NOT COMPILED**: is sandbox me kotlinc / Android SDK nahi hai — WifiKitRules.kt / WifiKit.kt / WifiKitStore.kt / WifiKitTest.kt pehli baar GitHub Actions build me compile honge (zxing core 3.5.3 Maven Central se aayega).
- **NOT RUN**: GitHub Actions APK build.
- **NOT TESTED**: asli phone — OWNER_PHONE_TEST_LIST_v7_153.md par owner khud bolega.
- Known honest limits: Android 10+ par system apna confirm popup dikha sakta hai (suggestion API); saved-but-unknown-password networks pe direct switch possible nahi (panel khulta hai); QR read camera-live scan nahi hai — photo share karne ka tarika hai; scan ke liye Location permission zaroori (Android ka rule).
- LEGAL LINE: password-protected network sirf owner ke password se judta hai; forbidden-words check contract me hai (crack/brute/wordlist/aircrack/reaver/wifite/pin attack/wps attack/handshake grab).
