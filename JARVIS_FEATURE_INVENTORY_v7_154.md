# NOVA v7.154 — JARVIS UPGRADE FEATURE INVENTORY (complete project autopsy)

Owner order: JARVIS-level upgrade, ZERO feature removal, add-only. This file is the full inventory
of what NOVA v7.153 (before this upgrade) already had, what the JARVIS wish-list asked for, and what
v7.154 adds. Source: full code autopsy of all 276 Kotlin files + 200 tool scripts, done before any change.

## 1. Project identity (verified from source)

- App: NOVA, Android, Kotlin, package `com.nova.assistant`, versionName 7.153 → 7.154.
- Build: GitHub Actions (`assembleDebug`), owner uploads the ZIP from a phone; sandbox has no Android SDK
  (Kotlin here is NOT COMPILED locally — honest status is always reported).
- Size: 276 Kotlin files in the main package, ~200 contract/audit scripts in `tools/`, 168 contract checks green at v7.153.
- Backup of the untouched v7.153 source: `backups/NOVA_v7153_SOURCE_BACKUP_20261030.zip` (made before this work).

## 2. What ALREADY EXISTS (kept 100%, nothing removed or weakened)

### AI brain (v7.151/152 GLM 5.3 brain + older)
- Provider chain with automatic retry + fallback: Groq (gpt-oss) → Qwen → Z.ai GLM ×3 keys → OpenRouter free
  → **GLM 5.3 Flash (z-ai/glm-5.3-flash:free, brainOnly)** → Pollinations (keyless). Gemini runs its own pool first.
- Provider cooldowns (fail a provider for 10 min / 20 s / 2 h by error class), hedged parallel asking (6 s stagger).
- ProviderRanker: learns per phone from success rate + latency (score = prior + weight*(40*(rate-0.5) - latency penalty)).
- Health: 6-hourly `healRound` probes every keyed provider with a tiny real request; on-demand SELF TEST pings.
- Local offline brain: llama.cpp (llamacpp-kotlin 0.1.0), built-in catalog Qwen2.5-0.5B Q4_K_M (491 MB,
  SHA-256 verified, resumable download), bigger brains owner-tap-only, warm-up only while charging + ≥4 GB RAM.
- Task engine: agent loop (60 steps / 600 s), hard-deny list (pay/otp/bank...), persistent 13-state task ledger
  with checkpoints, INTERRUPTED marking, safe idempotent-only resume, AutoTrace progress lines.
- Memory: ContextMemory (120 s follow-up), PersonalMemory (100 owner-approved facts, ≤3 in prompts),
  chat sessions, learned routes, capability learning.

### Voice (speak)
- Android TextToSpeech (offline) with rate/pitch settings, Hindi/English auto language per reply (ReplyLang).
- v7.129 JARVIS server voice (owner's own Colab + FuturePresentLabs tts-jarvis, clipboard pairing, WAV check).
- v7.138 JarvisLocalVoice (offline en-GB male system voice, pitch 0.85).
- v7.120 Ultron voice (TTS → WAV → pure-Kotlin metallic DSP: pitch shift, octave-down, 70 Hz metal ring, bass
  harmonics, comb, echo, grit, soft-clip → AudioTrack), sentence-chunked streaming.
- SafeStop: typed STOP + STOP button always kill speech instantly; persistent SILENT mode.

### Ears (STT)
- Vosk offline (en-us small, grammar wake spotter with 2-hit + loudness + confidence gates, self-tuning WakeAdapt).
- Optional Porcupine wake word (owner AccessKey). Hindi Vosk pack (runtime download, last-resort listener).
- Cloud STT fallbacks (Groq Whisper, Gemini). Mic: single AudioRecord, AEC + noise suppressor, VAD,
  recovery from read errors, video recording handoff, foregroundServiceType="microphone".
- Lock guard (locked phone = no new work), busy-listen rules, conversation mode.

### Phone automation
- 21-tool strict allow-list (open_app, call, sms, whatsapp, alarm, timer, torch, volume, navigate, media,
  global actions, calendar, brightness, ringer, rotation, camera, settings...), range-validated arguments.
- NovaAccessibilityService: label taps, typed text with read-back, scrolls, reels, per-mille taps, screenshots,
  screen reading (passwords never), protected-screen refusal (bank/payment/installer).
- Calls/WhatsApp only after spoken confirmation; contact disambiguation (MATCH/AMBIGUOUS/LOW/NEED_NAME,
  Hindi-roman folding, duplicate merging). Message Mode auto-reply (10-app allow-list).

### UI
- App: WebGL2 3D orb + globe + 28-bar waveform, listen/think/speak states, chat with typing box, dock
  (CHAT/CHUP/SILENT/STOP/POPUP/ULTRON), graphics quality AUTO/SHARP/SMOOTH, light theme.
- Floating HUD card: orb + waveform + SILENT/STOP + HAAN/NAHI + typing chat + menu; light/dark popup.
- Wake card, movie popup, update center, settings with 3 Gemini keys + Groq/OpenRouter/Z.ai/Together/HF keys.

### Self-diagnostics
- CrashNote (crash file + masked stack), CrashGuard (2 crashes/10 min → 30-min SAFE MODE), MainStallWatchdog
  (ANR recorder), SelfTest (real device tests + readable report + honest NOT TESTED list), VoiceDiagnosis,
  ConfigReport, HealthCheck, WatchDiag, ErrLog (30 masked entries), FeatureVerify (bridge two-way check).

### Everything else (unchanged)
- WhatsApp + WhatsApp Business send/reply, lead generation + website building (SiteGen/LeadStore/Vercel),
  movie guess pipeline, reels, photo AI, trade hub, calendar, Gmail rules, connectors, routines, power guide,
  Wi-Fi Kit v7.153 (scan/best-switch/open-connect/QR/doctor/guard — legal tools only), battery SOS, schedule,
  morning wake alarm, memory, teach, skill packs, offline packs, update center.

## 3. JARVIS wish-list vs reality — the honest gap table

| # | Wish (owner's order) | v7.153 status | v7.154 action |
|---|---|---|---|
| 1 | Deep metallic offline JARVIS voice, no API key | PARTIAL (Ultron DSP on system TTS; neural voice only via owner's Colab server) | **ADD sherpa-onnx + Piper "alan" en-GB male neural voice, fully offline, 20 MB, interruptible streaming playback (JarvisDeepVoice)** |
| 2 | Hindi + English TTS | EXISTS (auto language) | kept; deep voice speaks Latin-script replies (English/Hinglish), Devanagari auto-falls back to the Hindi chain |
| 3 | Interruptible speech (spoken STOP while speaking) | MISSING (mic muted during TTS to avoid echo) | **ADD opt-in barge-in: while NOVA speaks, a tiny grammar recognizer listens only for stop words with self-echo guard (BargeIn)** |
| 4 | Low latency + natural pauses | PARTIAL | deep voice = streaming playback (first audio before the whole sentence is generated), sentence chunking kept |
| 5 | Audio focus | MISSING (no requestAudioFocus anywhere) | **ADD SpeakFocus: transient-may-duck focus around every utterance, ON by default, can be turned off** |
| 6 | Provider health checks | EXISTS (6-hourly + SELF TEST) | **ADD on-demand "provider check karo" command** (probes every keyed provider, speaks the result) |
| 7 | GLM-5.3 as primary reasoning provider | EXISTS for brain jobs (brainOnly-first) | **ADD opt-in "GLM 5.3 sab jagah" (glmAll): GLM 5.3 Flash first for EVERY AI job, default OFF** |
| 8 | Offline-first priority order | EXISTS (local actions → offline brain → cloud) | kept, deep voice adds an offline-first voice layer |
| 9 | Task planning/persistent state/recovery | EXISTS (13-state ledger, checkpoints, safe resume) | kept untouched |
| 10 | Futuristic HUD/waveform/stop/silent/typing | EXISTS | kept untouched (optional light theme already there) |
| 11 | Real automation with verification | EXISTS (allow-list + ToolVerify + confirmations) | kept untouched |
| 12 | Personality, missing-info questions, memory | EXISTS (HelpGuide 67 topics, NEED_NAME, PersonalMemory) | +5 new help topics for the new features |
| 13 | Diagnostics + readable reports | EXISTS (SelfTest report) | kept; new features add their own status row + honest failure text |

NOT done on purpose (honest): no copyrighted JARVIS movie voice is bundled (illegal); no voice cloning of the
movie actor; no auto-resume of interrupted non-idempotent tasks (safety, existing design decision); no
unauthorized network access of any kind (WiFi bypass stays refused — legal line).

## 4. New in v7.154 (this ZIP) — all ADD-ONLY

1. **JARVIS DEEP VOICE (offline neural)** — sherpa-onnx v1.12.14 (jitpack) + Piper en_GB-alan-medium-int8
   (20 MB, SHA-256 verified, resumable download, safe tar.bz2 extraction). Opt-in, default OFF. Speaks
   English/Hinglish (Latin script); Devanagari replies use the existing Hindi voice. Any failure falls back to
   the existing voice chain (server Jarvis → JarvisLocal → Ultron → normal TTS) — nothing removed.
2. **BARGE-IN STOP (bol ke rokna)** — opt-in, default OFF. While NOVA speaks, the mic keeps listening for a
   small stop-word grammar (stop/chup/khamosh/ruko/bas/band karo). A stop word that NOVA itself is saying in
   its own reply is ignored (self-echo guard), so NOVA cannot stop itself.
3. **AUDIO FOCUS (default ON)** — music ducks while NOVA speaks and comes back after; can be turned off by
   voice or Settings.
4. **PROVIDER CHECK command** — "nova provider check karo" probes every keyed AI provider right now and
   speaks a one-line health result per provider.
5. **GLM 5.3 SAB JAGAH (opt-in, default OFF)** — puts GLM 5.3 Flash first for every AI job, not only big ones.
6. Version 7.154, 4 new settings, 5 help topics, Skills card, status rows, tests (see PART_STATUS_v7_154).

## 5. Old feature preservation checklist (all PRESERVED)

- [x] All 276 Kotlin files from v7.153 still present, none deleted; changes are additive edits.
- [x] All voice paths kept: server Jarvis, JarvisLocal, Ultron, normal TTS, Hindi chain, SILENT, SafeStop.
- [x] Wake word, Vosk/Porcupine, mic lifecycle, lock guard, busy-listen: unchanged (barge-in only READS
      chunks that were being dropped anyway, and only when the owner turns it ON).
- [x] Call handling, contact matching, WhatsApp/WaBiz, Message Mode: untouched.
- [x] Lead generation, website workflow, all AI providers + keys + fallback: untouched (glmAll only reorders
      when the owner opts in; default order identical to v7.153).
- [x] Memory, chat history, sessions: untouched.
- [x] UI: no screen removed; 4 new checkboxes + 1 new skills card + 5 help topics added.
- [x] Permissions: ZERO new permissions in v7.154 (deep voice needs only INTERNET, already declared).
- [x] Setup chain version stays 3 (no new permission round).
- [x] All 168 existing contract checks still run; new ones added, none weakened.
