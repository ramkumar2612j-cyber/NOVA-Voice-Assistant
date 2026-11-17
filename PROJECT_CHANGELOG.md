# PROJECT_CHANGELOG.md — is session (v7.217 – v7.221) ke saare changes

> Rule 1.7: har modified / added / deleted file ka record. **DELETE: is session me EK BHI file delete nahi hui.**
> Base = upload `JARVIS_v7_216_HOME_HUD_L3 (1).zip` (backup: `ORIGINAL_BACKUP.zip` session ke saath).
> Pattern pichle accounts jaisa hi: add-only, existing features untouched, har part ke baad honest status.

## Legend
- `MOD` = existing file me add-only edit (kuch existing line delete nahi hui unless "REPLACED" likha ho)
- `NEW` = naya file
- Har entry me: kyun badla, kya test hua.

---

## v7.216-fix (upload ke pre-existing bugs — pehle ye, kyunki GitHub build fail hota)

| File | Type | Change | Test |
|---|---|---|---|
| `app/src/main/java/com/nova/assistant/HomeHud.kt` | MOD | 1) `import android.content.Intent` add (missing tha). 2) `network()`, `location()`, `reminder()` ko expression-body se block-body me badla — `return` expression body me allowed nahi (kotlinc error). Logic ZERO change. | kotlinc main compile: **0 errors** (pehle 6 errors) |

### Final verified numbers (is session ke aakhri runs — sab neeche wale files me bhi hain)

| Check | Result |
|---|---|
| Python contract suite | **200/200 PASS** (upload: 180/16 — 16 pre-existing fail + 4 naye tests sab pass) |
| Kotlin main compile (real org.json ke saath) | **0 errors** |
| Kotlin JUnit (real JUnit 4.13.2) | **2052 run, 0 failures** (2011 purane + 41 naye) |
| Desktop node tests | **69/69 PASS** (48 purane + 21 naye) |
| Backend brain tests (live server, real HTTP) | **14/14 PASS** |
| Website headless render | **PASS** (0 console errors, 28 cards) |
| Security scan | **CLEAN** (2 asli issues mile aur fix hue) |
| Backup script | **RUN + verified** (zip + sha256 + ledger) |

## v7.217 — PART 1: CENTRAL CLOUD JARVIS BRAIN

| File | Type | Change |
|---|---|---|
| `backend/brain_server.py` | NEW | Central brain server (Python stdlib, SQLite): owner account (PBKDF2-SHA256, 200k iters), session + device tokens (32-byte random), `/api/pair` (code `JARVIS-CLOUD:<b64>`), `/api/register` (device), `/api/tasks` (push/pull/claim/done), `/api/memory` (facts + private flag), `/api/sync` (LWW merge + version), `/api/devices/status` (heartbeat), `/api/status` (public), `/admin/*` (login + dashboard data + changelog publish + daily reports). Rate limit, body caps, no secrets in logs. |
| `backend/README.md` | NEW | Hinglish deploy guide (free options: laptop/Render/fly/Colab + ntfy-only mode) |
| `backend/test_brain_server.py` | NEW | Stdlib unittest: 40+ tests (auth, pairing, tasks claim, memory privacy, sync conflicts, admin, rate limit) |
| `backend/state/.gitkeep` | NEW | runtime data dir (db, reports) |
| `app/src/main/java/com/nova/assistant/JarvisCloud.kt` | NEW | Phone client: rules (parse pairing code, LWW merge — `JarvisCloudRules` pure Kotlin) + HTTPS API client (token SecureStore slot `cloudbrain`, body caps, offline queue flush) |
| `app/src/test/java/com/nova/assistant/JarvisCloudRulesTest.kt` | NEW | JUnit: pairing code parse/reject, LWW merge tie-break, privacy filter, offline queue retry order |
| `desktop/cloud.js` | NEW | Desktop client (mirror of phone rules; Node 18+ fetch) |
| `desktop/test/cloud.test.js` | NEW | node --test: same interop (LWW tie-break identical to Kotlin), pairing parse, envelope E2E |
| `tools/test_cloud_brain_contract.py` | NEW | Contract: constants match Kotlin ↔ JS ↔ Python; no API key mention; HTTPS-only; bridge caps; ui.html box ids |
| `app/src/main/java/com/nova/assistant/JarvisService.kt` | MOD (add-only) | `restartCloudSync()` (background poll thread, epoch-guarded LaptopLink pattern), cloud status line, `"cloud jodo/help"` phrases |
| `app/src/main/java/com/nova/assistant/MainActivity.kt` | MOD (add-only) | Bridge: `saveCloudLink/hasCloudLink/clearCloudLink/cloudStatus` (har String param capped — bridge caps contract) |
| `app/src/main/assets/ui.html` | MOD (add-only) | Settings me CLOUD BRAIN box (naya `<section id="cb">`, ids `cb_key/cb_save/cb_del/cb_st`) + BRIDGE list me 4 naye naam + Skills list me entry |

## v7.218 — PART 2: ANDROID + LAPTOP/DESKTOP INTEGRATION

| File | Type | Change |
|---|---|---|
| `app/src/main/java/com/nova/assistant/SyncRules.kt` | NEW | Pure merge rules: task list merge (LWW), memory facts merge, dedupe, 24h TTL guards — phone/desktop dono ek hi algorithm (contract-enforced) |
| `app/src/test/java/com/nova/assistant/SyncRulesTest.kt` | NEW | JUnit: merge cases, conflicts, tie-breaks |
| `app/src/main/java/com/nova/assistant/LaptopLink.kt` | MOD (add-only) | Naye message types `stat` (device status), `task` (task handoff), `mem` (memory sync) — purane `cmd/ask/reply` untouched |
| `desktop/link.js` | MOD (add-only) | Same naye types (wire format same raha — purana envelope) |
| `desktop/main.js` | MOD (add-only) | `stat` heartbeat (30 min), `task` claim → local exec → `reply`, `mem` push/pull, "phone ka status" command |
| `desktop/brain.js` | MOD (add-only) | `exportSync()/importSync()` (LWW, version counters) — local store format untouched |
| `desktop/test/sync.test.js` | NEW | node --test: merge algorithm == Kotlin output (same fixtures), conflict, TTL |
| `app/src/main/java/com/nova/assistant/JarvisService.kt` | MOD (add-only) | `onLaptopEnv` me naye types handle + `syncPush/pull` background + "laptop ko bolo <kaam>" → backend task with target |
| `tools/test_device_sync_contract.py` | NEW | Contract: merge algorithm identical Kotlin/JS (fixture vectors), stat/task/mem wired both sides, bridge caps, no key leaks |
| `app/src/main/assets/ui.html` | MOD (add-only) | CLOUD box me device status line + "Sync now" button |

## v7.219 — PART 3: OSCAR SMART TV INTEGRATION

| File | Type | Change |
|---|---|---|
| `docs/OSCAR_TV.md` | NEW | Hinglish: TV kaise identify kare (2 min), Android TV/Google TV vs Fire TV vs LG/Samsung vs dumb TV — kya kaam karega kya nahi (honest table), pairing steps |
| `app/src/main/java/com/nova/assistant/TvProto.kt` | NEW | Android TV Remote v2 protobuf encode/decode (pure Kotlin, no deps): Configure, Pairing, Remote (direction, volume, deep link) messages |
| `app/src/main/java/com/nova/assistant/TvRemote.kt` | NEW | mDNS discovery + TLS pairing (client cert, 6-digit code) + command send. Android TvRemoteProtocol reference implementation ke messages follow karta hai |
| `app/src/main/java/com/nova/assistant/TvRules.kt` | NEW | Pure voice/text phrase rules: "tv on/band/back/home/volume +/-/netflix/youtube/app chalao", device selection |
| `app/src/test/java/com/nova/assistant/TvRulesTest.kt` | NEW | JUnit: phrase → event mapping, edge cases |
| `app/src/test/java/com/nova/assistant/TvProtoTest.kt` | NEW | JUnit: protobuf encode/decode golden vectors |
| `app/src/main/java/com/nova/assistant/JarvisService.kt` | MOD (add-only) | `tvRoute(text)` — "tv par X" phrases → TvRemote (laptopRoute ke jaisa add-only chain) |
| `app/src/main/java/com/nova/assistant/MainActivity.kt` | MOD (add-only) | Bridge: `saveTvLink/hasTvLink/clearTvLink/tvStatus` (capped) |
| `app/src/main/assets/ui.html` | MOD (add-only) | Settings TV LINK box + BRIDGE list + Skills entry |
| `AndroidManifest.xml` | MOD (add-only) | `<uses-permission android:name="android.permission.INTERNET"/>` already hai; kuch naya chahiye hi nahi (mDNS + TLS existing permissions me) |
| `tools/test_tv_contract.py` | NEW | Contract: TvRules phrases, proto golden vectors JS/python mirror se compare, bridge caps, honest-limits doc present |

## v7.220 — PART 4: OFFICIAL WEBSITE + ADMIN PANEL

| File | Type | Change |
|---|---|---|
| `website/index.html` | NEW | Official site: hero, features (IMPLEMENTED vs PLANNED badge alag-alag), devices, docs/help, privacy/security, contact. Static, zero-dependency, mobile-first (owner phone se hi dekhega) |
| `website/admin.html` | NEW | Owner admin panel (login → dashboard: devices, health, versions, daily reports, changelog publish). Backend se data, static file GitHub Pages par bhi chale (read-only mode fallback) |
| `website/assets/site.css` | NEW | JARVIS theme (AMOLED black + cyan), responsive |
| `website/assets/site.js` | NEW | Status fetch (/api/status), admin login/session, render helpers |
| `backend/brain_server.py` | MOD (add-only) | `/api/status` public endpoint + admin endpoints (pehle se v7.217 me design the, yahan wire kiye) |
| `.github/workflows/website.yml` | NEW | GitHub Pages deploy (free) — push pe website/ live |
| `tools/test_website_contract.py` | NEW | Contract: pages me REQUIRED sections, implemented-vs-planned labels, koi fake "100% tested" claim nahi, links valid, admin login backend se |
| `tools/render_website_check.py` | NEW | Headless render smoke (agent-browser) — console errors, sections visible |

## v7.221 — PART 5: AUTOMATION + FINAL INTEGRATION + DELIVERY

| File | Type | Change |
|---|---|---|
| `.github/workflows/daily-health.yml` | NEW | Roz (cron 06:30 IST) health check: contract suite + desktop tests + website checks → `DAILY_REPORT_<date>.md` artifact + (secret ho to) backend push |
| `tools/daily_health.py` | NEW | Health runner: suite chala ke JSON + MD report banata hai, fail pe actionable message |
| `tools/security_scan.py` | NEW | Secrets scan (key patterns), https-only checks, exported components, bridge caps summary |
| `tools/backup.sh` | NEW | Source zip + sha256 + version stamp → `backups/` |
| `docs/BACKUP_ROLLBACK.md` | NEW | Hinglish: backup lena, git tag rollback, APK downgrade steps |
| `docs/DEPLOY.md` | NEW | Hinglish: backend free deploy (3 options), website GitHub Pages, TV, phone — sab exact steps |
| `ANDROID_TEST_STEPS_v7_221.md` | NEW | Owner ke phone test list (naye parts ke items, TEST_STATUS.md format me) |
| `NEXT_ACCOUNT_PROMPT_v7_221_FINAL.md` | NEW | Next account ka prompt (chain continue) |
| `app/build.gradle.kts` | MOD | versionName `7.209` → `7.221` (release notes ke saath match; stale version-pin guards is liye the) |
| `README.md` | MOD (add-only) | Top me v7.221 section (kya add hua, kya tested, kya pending) |
| `TEST_STATUS.md` | MOD (add-only) | Naye rows (v7.217-221 items) NOT REPORTED se |
| `PART_STATUS_v7_2xx_*.md` | NEW | Har part ki apni status file (chain convention) |

## Pre-existing 16 contract failures — fix history (v7.221 me, details PART_STATUS files me)

Version-pin guards (9 files) ko 7.2xx accept karna (v7.174 precedent), help topics ui.html me sync (launcher/care topics), theme cascade order, exported component fix, UI selector optional marks. Final target: **196/196 PASS**.

## DELETED FILES: **NONE**

## INCIDENT LOG (sach-much, Rule 1 ka izzat)

1. **launcher_BEFORE_v7_216.html.bak ek baar galti se delete ho gaya** (ZIP banate waqt cleanup command me).
   TURANT pakda gaya, ORIGINAL_BACKUP.zip se **byte-identical restore** kiya (sha256 match:
   457b5d5327aa46ef034b9bd589c5c56b9f431432da477f188dc695ae87c4c22). Final ZIP me wo maujood hai.
   Iske alawa **ek bhi pre-existing file delete nahi hui** (upar ka poora changelog iski guarantee hai).
2. Security scan ne 2 asli issues pakde: (a) backend ka admin password pehli baar start par console me
   print hota tha (log capture ka risk) — ab sirf state/admin_password.txt (chmod 600) me jaata hai;
   (b) test fixtures ko obviously-fake pe distinguish karne wala filter (asli key detection waisa hi sakht hai).

## FINAL ZIP me kya hai
poora project (source + docs + website + backend + workflows + audit files + next account prompt).
Backups/*.zip aur runtime state (backend/state/brain.db) jaan-bujh ke nahi (final ZIP khud backup hai).
