# NEXT ACCOUNT PROMPT (after v7.221 JARVIS EVERYWHERE — 5 parts FINAL)

Owner Hinglish bolta hai, Android phone se GitHub (Chrome) par kaam karta hai, ek-ek step, exact github.com links. Wo JARVIS ka "boss"; naam sirf "Jarvis". Pehle padho: `PART_STATUS_v7_217_221_JARVIS_EVERYWHERE.md`, `ANDROID_TEST_STEPS_v7_221.md`, `MISSING_FEATURES.md` (§G pending list).

Rules wahi purane: add-only; HARD DENY (paisa/bank/password/OTP/PIN/CVV) kabhi nahi; sach likho (NOT RUN jo chala nahi); har zip me ye prompt + status file; koi file delete nahi (is session me bhi 0 deletes hue).

## Ye session (v7.217-221) me kya hua — ek nazar
1. Upload ka HomeHud.kt COMPILE NAHI hota tha (v7.216 ka bug — GitHub build fail hota) — FIX (block body + import). 16 pre-existing contract fails bhi FIX (stale 7.2xx version pins, help topics 82 vs 83, theme cascade, exported pin, arc7215 selector). Ab contract 197/197.
2. PART 1 CLOUD BRAIN: `backend/brain_server.py` + phone `JarvisCloud.kt` + desktop `cloud.js` + Settings CLOUD BRAIN box. Backend tests 14/14 (real HTTP).
3. PART 2 SYNC: `SyncRules.kt` (LWW; phone/desktop same fixtures), link me stat/mem types, "laptop ko bolo X", laptop task worker, memory sync (cloud + link-only). Desktop 69/69.
4. PART 3 TV: REAL Android TV Remote v2 (AOSP spec se — TvProto/TvCert/TvRemote/TvRules, ports 6466/6467, POLO pairing, 6-hex PIN). 47 JUnit PASS. Real TV NOT TESTED — `docs/OSCAR_TV.md` me owner ke liye identify+steps+alternatives.
5. PART 4 WEBSITE: `website/` (index + admin, static, Pages workflow). Headless render PASS.
6. PART 5 AUTOMATION: `daily-health.yml` + `tools/daily_health.py`, `tools/security_scan.py`, `tools/backup.sh`, `docs/DEPLOY.md` + `docs/BACKUP_ROLLBACK.md`. versionName 7.221.

Sandbox RUN: contract 197/197, kotlinc 0 errors, JUnit 2081/2081 (REAL org.json + JUnit — pehli baar), desktop 69/69, backend 14/14, website render PASS, security clean. NOT RUN: GitHub Actions APK / phone / TV / live deploy.

## Agla kaam (priority order)
1. **GitHub Actions "JARVIS Android CI" chalao** (push karke) — ab compile green hai to APK banana chahiye. Red aaye to log dekh ke fix (AutoFixRules khud bhi try karega).
2. Owner `ANDROID_TEST_STEPS_v7_221.md` ke items ek-ek karke chalaye — PASS/FAIL TEST_STATUS.md me.
3. **OSCAR ka TV type pata karo** (`docs/OSCAR_TV.md` §1 — owner ko 2 min ka kaam): Android TV/Google TV hai to TV LINK pairing test; nahi hai to batana kaunsa hai (Fire TV/webOS/Tizen/plain) — uska module agla part.
4. Backend deploy (docs/DEPLOY.md Option B Render — free) + website Pages ON + admin se pairing code → phone + laptop dono jodo.
5. Daily health workflow chala ke dekho; secrets (JARVIS_BRAIN_URL + PASSWORD) set karo to admin panel me reports aane lagenge.
6. Ultron voice default OFF karna abhi bhi PENDING hai (owner ne TEST_STATUS #21 me FAIL likha tha) — owner haan bole to ek line ka switch change hai.

## Technical notes (agle account ke liye)
- LWW tie-break: (ver, ts, deviceId) tuple — Kotlin/JS/Python teeno me SAME (test_cloud_brain_contract + test_device_sync_contract fixtures enforce karte hain). Badla to teeno jagah badlo.
- Link envelope format NAHI badla (stat/mem naye types hain, purana cmd/ask/reply jaisa hi envelope) — test_laptop_link_contract untouched pass hota hai.
- Bridge list me naye naam: saveCloudLink/hasCloudLink/clearCloudLink/cloudStatus/saveTvLink/tvSetPin/tvStatus/clearTvLink — caps contract ke saath.
- brain_server.py ka admin password ab console par print NAHI hota — `backend/state/admin_password.txt` me jaata hai (chmod 600). Deploy par ADMIN_PASSWORD env se override hota hai.
- tools/local_compile_check.sh ab json.jar + JUnit jars `/home/z/my-project/work/kotlinc_work/` se uthata tha (Maven Central se download kiye the) — naye sandbox me dobara download karne padenge (script khud git+java se try karta hai; Maven direct bhi chalta hai).
- HomeHud.kt jaisi expression-body-return wali galti dobara na ho: har naya Kotlin file `local_compile_check.sh` se compile hona chahiye pehle.
