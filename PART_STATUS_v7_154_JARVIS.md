# v7.154 JARVIS UPGRADE — status (imaandaari se: kya RUN hua, kya NAHI)

Owner order: JARVIS-level upgrade with STRICT ZERO-REGRESSION. Poora autopsy pehle hua
(JARVIS_FEATURE_INVENTORY_v7_154.md — 276 files, gap table, preservation checklist), source backup liya
(backups/NOVA_v7153_SOURCE_BACKUP_20261030.zip), phir SAB KUCH ADD-ONLY add kiya. Kuch bhi delete/disable/replace NAHI hua.

## Kya bana (5 naye features, sab opt-in default OFF except audio focus)

1. **JARVIS DEEP VOICE (offline neural)** — `JarvisDeepRules.kt` (pure) + `JarvisDeepVoice.kt` + `TarBz.kt`.
   Piper "alan" en-GB male voice (int8, 20.1 MB, SHA-256 pinned: f0b2526c…50de4) sherpa-onnx v1.12.14 se,
   100% offline, bina key. Download sirf "nova deep voice download karo" par (ModelDownload reuse: resumable,
   Range, checksum fail = delete). Extract path-traversal/symlink/size-safe. Playback streaming
   (generateWithCallback → AudioTrack ENCODING_PCM_FLOAT), STOP generation + audio dono turant rok deta hai.
   speak() chain me SABSE PEHLE (system TTS ki zaroorat hi nahi); FAILED hone par purani poori chain
   (server Jarvis → JarvisLocal → Ultron → normal TTS) wapas chalti hai. English/Hinglish (Roman) only;
   Devanagari = purani Hindi awaaz (latinOk guard, wahi rule jo v7.129 server Jarvis ka hai). Emojis clean() se hataaye jaate hain.
2. **BARGE-IN (bol ke rokna)** — NovaService mic loop me, sirf jab ttsActive && ExtCfg.bargeIn (default OFF).
   Grammar recognizer sirf stop words + [unk]; final result par conf >= 0.85 + RMS >= 1000 + self-echo guard
   (jo word curSpeakText me hai wo ignore — NOVA khud ko nahi rok sakti). Hit → stopSpeaking() (SafeStop semantics:
   service/mic/wake ON hi rehte hain) + wasBlocked reset. Locked phone par stop allowed (existing policy).
3. **AUDIO FOCUS (default ON)** — `SpeakFocus.kt`: GAIN_TRANSIENT_MAY_DUCK, setWillPauseWhenDucked(false)
   (koi app pause NAHI hota, sirf duck). Har utterance ke saath acquire, utteranceEnded/stopSpeaking/destroy par abandon.
   Doosri app permanent focus le → NOVA khud chup ho jaati hai.
4. **PROVIDER CHECK** — "nova provider check karo" → AutoSetup.checkProviders (chhota REAL request per keyed provider,
   network thread) → JarvisDeepRules.replyProviders (OK / key galat 401-403 / quota 429 / offline 598, max 6 lines).
5. **GLM 5.3 SAB JAGAH (default OFF)** — ProviderChain.run + runHedged me naya `glmFirst: Boolean = false` param
   (forJob(providers, rich || brain || glmFirst)). NovaService: runFreeChain + smartDraft pass karte hain
   glmFirst = ExtCfg.glmAll(this). Phone-action 4.5 s chain jaan-boojhkar ALAG (speed-critical, GLM wahan kabhi nahi).

## Wiring (sab jagah, koi dead code nahi)

- Logic.classify: `JarvisDeepRules.parse(text)` stop-check ke turant BAAD (volume/awaaz block se pehle —
  "awaaz se rokna" warna us block me `return null` ho jaata). Negated orders kabhi match nahi.
- NovaService.runCommand: `in JarvisDeepRules.KINDS -> jarvisUpgradeReply(c)` (12 kinds).
- ExtCfg: jarvisDeep/bargeIn/glmAll default false, afocus default true. OFF karne par engine turant release.
- MainActivity: getSettings me 5 naye keys (4 switch + deepVoiceReady), saveSettings me 4 save; 4 nayi read-only
  bridge methods (getJarvisDeep/getBargeIn/getAfocus/getGlmAll) Skills dots ke liye.
- ui.html: 4 checkboxes + deep_note status line + collect/fill; Skills me 5 nayi cards (VOICE: deep/barge/focus,
  AI: glmall/provcheck); MADAD CAPS me 5 naye topics (HelpGuide.Topic ke saath exact sync — test_help_contract).
- FeatureStatus: 4 nayi read-only rows (jdeep/barge/afocus/glmall).
- Gradle: sherpa-onnx v1.12.14 (JitPack repo settings.gradle.kts me), commons-compress 1.26.2, versionName 7.154.
  NAYI PERMISSION: ZERO (contract me pinned: count 30/31/35/36 hi).

## Evidence — IMAANDAARI SE

- **RUN (sandbox)**: `tools/run_all_checks.sh` = **169 passed, 0 failed, 0 known stale, exit 0**
  (naya `tools/test_jarvis_upgrade_contract.py` bhi PASS). ui.html ke 71 `<script>` me se 70 `node --check` OK —
  script 20 (#version private-field) ka error v7.153 backup me bhi waisa hi tha (pre-existing, is version se nahi).
- **NOT COMPILED**: is sandbox me kotlinc / Android SDK nahi. 4 nayi Kotlin files + 6 changed files pehli
  GitHub Actions build me compile hongi (sherpa-onnx JitPack se aayega, commons-compress Maven Central se).
- **NOT RUN**: GitHub Actions APK build; JUnit (WifiKitTest ki tarah ye bhi CI me chalega).
- **NOT TESTED**: asli phone — OWNER_PHONE_TEST_LIST_v7_154.md par owner khud bolega.

## Known honest limits

- Deep voice sirf English/Hinglish (Roman) — Devanagari Hindi jawab purani Hindi awaaz me (model English-only hai).
- Alan ka voice "JARVIS jaisa" hai (deep British male), ASLI movie voice NAHI hai — wo copyright hota, mana kiya.
- Neural synthesis system TTS se slow hai (pehla shabd ~1-3 s me aata hai, streaming ke kaam se) — isliye default OFF.
- APK ~25-30 MB bada (onnxruntime .so, arm64+x86_64 abiFilters ke baad) — known cost.
- Barge-in final-result par chalta hai (partial nahi) — matlab "stop… (½ s silence)… " tab fire hota hai; galat-stop
  se bachne ke liye high threshold rakhna zaroori tha.
- Barge-in speaker-phone / loud NOVA par: echo guard word-level hai; agar NOVA ka speaker khud ka "stop" miss ho
  (bohot loud), conf gate (0.85) + RMS gate jhooth bolenge hi nahi.
- JitPack pehle resolve par build slow ho sakta hai (AAR ~40 MB).
- Agar OpenRouter GLM 5.3 404 de (model id change): fix sirf FreeProviders.OPENROUTER_GLM (v7.152 rule, waisa hi).

## Agla kaam (order me)

1. Owner: ZIP upload → GitHub Actions build GREEN (compile errors, ho to sab batao — 6 changed files + 4 naye).
2. Phone test list v7_154 (15 items, neeche file me) — owner bolega, PASS/FAIL likhega.
3. Phir purane open items (v7.153 WiFi phone tests, v7.152 light popup, v7.150 list).
