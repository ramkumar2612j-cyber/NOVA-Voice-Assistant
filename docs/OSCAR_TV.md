# OSCAR TV — JARVIS se TV control (v7.219, sach-much ka haal)

> Ye file **sach** batati hai: kya kaam karta hai, kya nahi karta, kaise pata karein, aur kya test hua hai.
> JARVIS kabhi ye jhooth nahi bolta ki koi feature kaam karta hai jab wo test na hua ho.

## 1. Pehle ye pata karo: OSCAR kaunsa TV hai? (2 minute)

TV ke **Settings** kholo aur dekho:

| TV Settings me kya likha hai | Matlab |
|---|---|
| "Device Preferences" > "About" me **Android TV** ya **Google TV** dikhe | **Best case** — JARVIS ka TV module isike liye bana hai |
| "About" me **Fire TV** dikhe (Amazon) | Fire TV Edition — niche dekho (§4) |
| **webOS** (LG), **Tizen** (Samsung), **My Home Screen** (Panasonic) | Android TV protocol NAHI chalega — niche §4 |
| Kuch nahi likha / smart nahi | Plain TV — sirf IR wala option (§4) |

**Owner ko bas itna batana hai:** TV Settings > About (ya Device Preferences > About) me
"Build" / "Android version" / "Google TV" me kya likha hai + TV ka brand (Sony/Xiaomi/Mi/Samsung/LG/...).
Wahi bhejoge to agla step exact bolenge.

## 2. Kya ban chuka hai (CODE PRESENT — aur kya test hua)

| Cheez | Status |
|---|---|
| `TvProto.kt` — TV ka asli remote protocol (protobuf messages) | **IMPLEMENTED + unit tests PASS** (wire format AOSP polo.proto + remotemessage.proto se — wahi protocol jo Google TV app use karti hai) |
| `TvCert.kt` — pairing ke liye self-signed certificate (RSA 2048) | **IMPLEMENTED + compile PASS** (Android par cert API nahi hoti isliye DER haath se banaya) |
| `TvRemote.kt` — TLS pairing (TV par 6-anka code) + key/app bhejna | **IMPLEMENTED, compile PASS. Asli TV par NOT TESTED** (sandbox me TV nahi hai) |
| `TvRules.kt` — voice phrases | **IMPLEMENTED + unit tests PASS** |
| App me Settings > TV LINK box (IP + PIN) | **IMPLEMENTED, phone par NOT TESTED** |
| JarvisService me "tv ..." commands | **IMPLEMENTED, phone par NOT TESTED** |

## 3. Kaam kaise hoga (Android TV / Google TV wale OSCAR ke liye)

1. TV aur phone **ek hi Wi-Fi** me hon.
2. TV par **Settings > Network & Internet** se TV ka IP pata karo (jaise 192.168.1.15).
   (Kai TV me: Settings > Device Preferences > About > Status)
3. JARVIS app > Settings > **TV LINK (OSCAR)** me IP daalo > **PAIR** dabao.
4. TV par ek **6-anka code** dikhega — wahi PIN box me daalke **PIN BHEJO** dabao.
5. "juda hua" dikhe to ho gaya! Bolke dekho:
   - "tv netflix kholo" / "tv youtube chalao" / "tv hotstar kholo" (15+ apps)
   - "tv volume badhao" / "tv volume 3 bar kam karo" / "tv mute karo"
   - "tv home" / "tv back" / "tv upar jao" / "tv ok"
   - "tv band karo" (sleep) / "tv on karo" (wake — sirf TV jaag jaye to)
   - "tv pause" / "tv agla video"

**Kya NAHI hoga (honest):**
- TV band ho to "tv on karo" network se nahi jaayega — band TV par Wi-Fi bhi band hota hai
  (kuch TV me Wake-on-LAN hota hai, ye abhi add nahi kiya — mangoge to agle part me).
- TV par "jarvis" bolke wake word NAHI sunege — TV hardware me mic wake-word support rare hai;
  wake word phone hi sunege, TV ko phone se command jayega. Ye limitation hai, jhooth nahi.
- Voice/video search TV par: "tv search" dabata hai, thoda kaam karega, par text typing full nahi.

## 4. Agar OSCAR Android TV NAHI hai — options (sab free)

| TV type | Kya kar sakte ho |
|---|---|
| **Fire TV** (Amazon) | ADB network se control hota hai (Settings > My Fire TV > Developer Options > ADB debugging ON). Agla part me fire TV module ban sakta hai — owner bolega to. |
| **LG (webOS)** | Open-source "luna" API se control hota hai (TV Settings me "LG Connect Apps" ON karke). Possible hai, agla part. |
| **Samsung (Tizen)** | Samsung ka "SmartThings" API chahiye (free par account lagta hai). Thoda jyada kaam. |
| **Plain TV (koi OS nahi)** | Phone me IR blaster ho (kuch Xiaomi/mi phones me hota hai) to IR codes se on/off/volume possible. JARVIS me IR module abhi nahi hai. |
| **Koi bhi TV** | Set-top box (Android TV wala) jod do — sabse sasta ₹2-3k ka Chromecast wala/Google TV stick — phir sab kaam karega. |

## 5. Technical details (developers ke liye)

- Protocol: **Android TV Remote protocol v2** — mDNS `_androidtvremote2._tcp`, pairing **port 6467** (TLS + client cert + POLO handshake + 6-hex PIN secret), commands **port 6466** (TLS + same cert).
- Reference implementations jisse wire format liya gaya (open source): AOSP `google-tv-pairing-protocol` (polo.proto), AOSP TvRemote (remotemessage.proto), `github.com/tronikos/androidtvremote2`. Field numbers copy kiye gaye hain, invent nahi.
- Secret: `sha256(clientMod || clientExp || serverMod || serverExp || pin[2:] bytes)` — pehla byte `pin[0:2]` hex se match hona chahiye.
- Key press: `RemoteMessage{remote_key_inject{key_code, direction=SHORT(3)}}`; app: `remote_app_link_launch_request{app_link="market://launch?id=<pkg>"}`.
- Framing: har message aage uske varint length se.
- Phone me kuch nayi permission NAHI chahiye (INTERNET pehle se hai).

## 6. Test kaise hua (honest log)

- `TvProtoTest` (JUnit, sandbox): **PASS** — varint golden bytes, framing round-trip, har pairing message ke field numbers, secret-hash algorithm (valid PIN branch, invalid PIN reject, 6-hex shape), key/deeplink encoding.
- `TvRulesTest` (JUnit, sandbox): **PASS** — har phrase mapping, repeat count, app ids, galat phrases reject.
- `tools/test_tv_contract.py` (contract): static wiring + docs.
- **NOT TESTED**: asli TV ke saath pairing/commands (sandbox me TV hardware nahi hai). Pehla asli test owner karega — steps §3 me.
