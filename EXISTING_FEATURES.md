# EXISTING_FEATURES.md — JARVIS (nova) v7.216 upload ka poora audit

> Ye file upload `JARVIS_v7_216_HOME_HUD_L3 (1).zip` ke andar jo tha, uska **sach-much existing** inventory hai
> (is session me kuch bhi naya add hone se PEHLE ka status). Rule 2 kehta hai: "feature missing mat samjho
> sirf isliye ki uska code doosre module me hai" — isliye har cheez ka asli location likha hai.
> Status legend: **CODE PRESENT** = code repo me hai (phone par owner ne test nahi kiya), **TESTED SANDBOX** =
> is sandbox / pichle sandbox me test chala, **PHONE PASS/FAIL** = TEST_STATUS.md ke hisaab se owner ne phone par test kiya.

## 1. Project structure (jaisa upload me mila)

| Jagah | Kya hai | Status |
|---|---|---|
| `app/` (com.nova.assistant) | Android app: 569 Kotlin files (main+test), WebView UI (`assets/ui.html` ~800KB, ~8.4k lines), launcher (`LauncherActivity` + `assets/launcher.html`) | CODE PRESENT |
| `app/src/test/` | 237 JUnit test files (pure `*Rules` logic tests) | TESTED SANDBOX (is run me) |
| `desktop/` | Electron desktop app (Windows/laptop): boot, HUD, voice (Groq Whisper STT), brain (notes/reminders/workspaces), commands/actions, phone-link | TESTED SANDBOX (node --test 48/48) |
| `tools/` | 192 Python contract tests + build/audit/self-improve scripts + `jarvis_server/` (TTS server) + `kt/` standalone checkers + `orb/` shader renders | TESTED SANDBOX (is run me) |
| `.github/workflows/` | `build.yml` (Android CI: contract checks + Gradle APK + jarvis-latest release), `desktop.yml` (Windows exe), `chat-perf-fix.yml` (one-time patch) | CODE PRESENT (is session me nahi chala; owner ke GitHub par chalta hai) |
| `docs/` | ARCHITECTURE, BUILD, FEATURES_AND_LIMITS, HANDOFF, TEST_REPORT_* etc. | CODE PRESENT |
| Jaise-jaise `PART_STATUS_v7_*.md`, `NEXT_ACCOUNT_PROMPT_v7_*.md` | 60+ history files (add-only chain) | CODE PRESENT |

## 2. Android app — features (kuch bhi hataya NAHI ja sakta, sab preserve hoga)

### Voice / AI
- Wake word: Vosk offline (default) + optional Porcupine (owner AccessKey) — PHONE: NOT REPORTED
- AI provider chain (client-side, owner keys Android Keystore me encrypted): Groq (gpt-oss-120b/20b, qwen 27b), Z.ai GLM (4.7/4.5-flash, 3 key slots), OpenRouter (free models incl. glm-5.3-flash), Pollinations (keyless), Gemini (3.5-flash family) — free-first fallback chain CODE PRESENT
- Offline brain: llama.cpp (llamacpp-kotlin, 491MB model, DeviceTier gating) CODE PRESENT — download NOT RUN
- JARVIS voice server (custom TTS): `tools/jarvis_server/server.py` (VoxCPM2 LoRA) + `JarvisPairing`/`JarvisVoiceClient`/`JarvisSpeaker` phone side, Colab notebook `NOVA_Jarvis_Colab.ipynb` — CODE PRESENT
- Cloud STT fallback: Groq Whisper; call-agent two-way talk `groqHear`

### Phone control
- 21 automation tools + `NovaAccessibilityService` (taps/scroll) with safety blocks (bank/pay/install screens) 
- Calls (dialer), WhatsApp text send + auto-reply (v6.53), Instagram, torch, volume, brightness, media, apps open, camera, settings, alarms/timers, calendar
- Contact resolution (ContactsContract) with ambiguity dialog
- Screen reading (accessibility), video/reel watching, camera look, photo analysis, image generation

### Agents / business
- Website builder (SiteGen + GitHubPublish + VercelPublish), Leads finder, Orders (WooCommerce/Shopify read-only), Offers, Autopilot (WhatsApp Business), Call agent (call karke table book), Instagram DM/story, App builder, Learn, Movie guess, Match live, TradeHub (info-only), GitHub self-build/self-patch
- Business home (Aaj ka business card, launcher L4), BizStore/BizRules, BusinessHomeStore

### Home / launcher
- LauncherActivity (home screen replacement): apps grid, search, dock, quick actions, weather, business card, System Status gauges, arc reactor, hologram, live wallpaper (v7.210-211), HOLO theme (v7.210), CARE (v7.213)
- v7.214-216 HOME THEME L1-L3: launcher.html reference-image JARVIS HOME (notifications panel, metrics, arc reactor, recent activity), ui.html `#jh` HUD + `HomeHud.kt` real metrics + `Android.homeJson()` bridge
- **NOTE (is audit ne pakda):** v7.216 ka `HomeHud.kt` COMPILE NAHI HOTA THA (missing import + expression-body return) — GitHub Actions build fail ho jaata. Is session me FIX kiya (PROJECT_CHANGELOG.md dekho).

### Memory / schedule / tasks
- `nova_memory` (memory in chain), `nova_chats` history, ScheduledTaskStore (30 tasks, weekday/prio/note, lead alarm), TaskManagerStore (aakhri kaam), AgentTaskLedger (interrupted task resume-safety), morning routine, wake alarm, battery guard + SOS WhatsApp
- Desktop brain mirror: notes/reminders/workspaces in `userData/brain.json` (local only)

### Link / cloud (is task ka base)
- **LaptopLink (v7.177)**: phone ↔ laptop E2E-encrypted link. AES-256-GCM, topic = `jarvis-<sha256[:40]>`, relay ntfy.sh (free, no account), types `cmd`/`ask`/`reply`. Key SecureStore slot `laptoplink`. Phone: `JarvisService.restartLaptopLink()`, "laptop par X" route. Desktop: `link.js` mirror + "phone jodo" pairing. **API keys kabhi phone se bahar nahi jaate** (test-enforced)
- **OfflineJobQueue + JarvisCloudQueueWorker**: background website/learn/image/lead-draft jobs (WorkManager), MAX 20, scrubbed payloads
- **JarvisPairing**: TTS server one-tap code `JARVIS-JARVIS:<b64 json>`
- Connectors: github, vercel, netlify, cloudflare, supabase, notion, lovable, gmail, mcp_*, hook_*, n8n_* (read/read+write perms)

### Security / safety (invariants)
- HARD DENY: pay/bank/password/OTP/PIN/CVV/delete/format/shutdown (desktop `commands.js` bhi)
- Secrets: Android Keystore AES-GCM (`SecureStore`), desktop Electron `safeStorage`
- No lock-screen bypass, no auto-start (ACTIVATE only), err/crash logging, SAFE MODE after crashes
- Contract tests enforce: bridge param caps, no api-key strings in desktop code, link crypto interop (node↔java), etc.

## 3. Desktop (Electron) — existing
- boot.html real-status boot, HUD inject (CPU/RAM/phone-link/notes), `commands.js` parser + `actions.js` executor (per-OS), `brain.js` local memory, voice (Groq Whisper + SpeechSynthesis), link mirror, `sync-ui.js` (phone ui.html shared), 48 node tests

## 4. Backend — existing (sirf itna)
- `tools/jarvis_server/server.py` = **TTS ONLY** (POST /tts, GET /health, Bearer token, stdlib). Koi brain/auth/device-sync nahi.
- Koi central brain, device registry, task sync, shared memory cloud, admin panel, official website **exist nahi karta** (ye is task ke 5 parts hain)

## 5. Baseline verification (is session me khud chalaya — upload ke code par)
| Check | Result |
|---|---|
| Python contract suite (`tools/run_all_checks.sh`) | **180 PASS / 16 FAIL** (16 = upload me pre-existing; is session me sab FIX — ab 200/200) |
| Kotlin main compile (kotlinc 1.9.24 + android-34 + stubs + REAL org.json) | **6 errors → HomeHud.kt (v7.216 ka naya file) — FIX kiya → 0 errors** |
| Kotlin test compile + JUnit | is doc likhte waqt chal raha hai (result PROJECT_CHANGELOG.md me) |
| Desktop tests | node --test 48/48 PASS (audio-test ke chhote kharabiyan agar hon to log me) |
| Real phone / GitHub Actions | **NOT RUN** (sandbox me hardware nahi) |

*Baseline ki poori detail aur fix history: `PROJECT_CHANGELOG.md` + `TEST_STATUS.md` (owner ke phone results wahi likhe jaate hain).*
