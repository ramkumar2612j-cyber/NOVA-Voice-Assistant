# NEXT ACCOUNT PROMPT — v7.154 JARVIS UPGRADE (naye chat/account ke liye poora context)

## Tu kaun hai
Tum NOVA project ka agla AI assistant ho. NOVA ek Android (Kotlin, package `com.nova.assistant`) local-first
voice assistant hai jo Hinglish/English bolti hai. Repo `nova_work/` me hai, version **7.154**, owner GitHub
Actions se APK banwata hai (`OWNER_SETUP_AND_APK_STEPS.md` section G). Pehle ye padho:
`docs/HANDOFF.md`, `JARVIS_FEATURE_INVENTORY_v7_154.md`, `PART_STATUS_v7_154_JARVIS.md`,
`NEXT_ACCOUNT_PROMPT_v7_153.md` (WiFi kit), `OWNER_PHONE_TEST_LIST_v7_154.md`.

## Owner ka order tha (v7.154)
"JARVIS-level upgrade, STRICT ZERO-REGRESSION + ADDITIVE" — complete autopsy pehle, phir koi bhi feature
remove/break/replace NAHI karna. Poora autopsy + gap-table + preservation checklist:
`JARVIS_FEATURE_INVENTORY_v7_154.md`. Source backup: `backups/NOVA_v7153_SOURCE_BACKUP_20261030.zip`.

## v7.154 me kya hua (5 naye features, SAB ADD-ONLY)
1. **JARVIS DEEP VOICE** (offline neural): Piper "alan" en-GB male (int8, 20.1 MB, SHA-256 f0b2526c…50de4)
   sherpa-onnx v1.12.14 (JitPack) se. Default OFF. Download sirf owner ke bole par (`nova deep voice download karo`),
   ModelDownload (resumable+checksum) + TarBz (safe extract) se. Streaming playback (generateWithCallback→AudioTrack),
   STOP turant rok deta hai. English/Hinglish-Roman only; Devanagari→purani Hindi awaaz (latinOk guard).
   FAILED→purani poori chain (server Jarvis→JarvisLocal→Ultron→normal TTS) khud chalti hai.
2. **BARGE-IN** (`nova bol ke rokna on karo`, default OFF): bolte waqt stop-words-only grammar recognizer,
   conf>=0.85 + RMS>=1000 + self-echo guard (curSpeakText). stopSpeaking() = SafeStop semantics.
3. **AUDIO FOCUS** (default ON): GAIN_TRANSIENT_MAY_DUCK (koi app pause NAHI), utterance ke saath acquire/abandon.
4. **PROVIDER CHECK** (`nova provider check karo`): AutoSetup.checkProviders reuse, per-provider OK/401/429/598 reply.
5. **GLM 5.3 SAB JAGAH** (default OFF): ProviderChain.run/runHedged me `glmFirst` param; NovaService runFreeChain
   + smartDraft pass karte hain; phone-action 4.5 s chain jaan-boojhkar untouched.

Files: NAYE — `JarvisDeepRules.kt` (pure, 12 kinds), `JarvisDeepVoice.kt`, `SpeakFocus.kt`, `TarBz.kt`,
`JarvisDeepRulesTest.kt` (15 tests), `TarBzTest.kt` (6), `tools/test_jarvis_upgrade_contract.py`.
CHANGED — NovaService.kt (speak chain + startDeepVoice + barge-in loop + jarvisUpgradeReply + glmFirst),
FreeProviders.kt (glmFirst param, default false), Logic.kt (hook stop-check ke baad, volume/awaaz block se PEHLE),
ExtCfg.kt (4 settings), MainActivity.kt (settings + 4 bridge), HelpGuide.kt (+5 topics = 73), FeatureStatus.kt
(+4 rows), ui.html (4 checkbox + deep_note + collect/fill + Skills 5 cards + CAPS 5 topics),
app/build.gradle.kts (deps + 7.154), settings.gradle.kts (jitpack), 12 version-pin scripts (+7.154).

## LEGAL LINE (kabhi mat todna)
Deep voice = open-source Piper model, ASLI movie JARVIS voice ya kisi insaan ki clone NAHI (copyright — mana).
WiFi bypass/crack pehle hi mana tha (v7.153 note padho) — wo line aaj bhi hai, `test_wifi_kit_contract.py`
aur `test_jarvis_upgrade_contract.py` dono me forbidden-words check hai.

## Abhi ki imaandaari (v7.154)
- RUN (sandbox): `tools/run_all_checks.sh` = **169 passed, 0 failed, exit 0**. ui.html 71 scripts me 70 node --check OK
  (script 20 #version error v7.153 me bhi tha — pre-existing).
- NOT COMPILED: sandbox me kotlinc/Android SDK nahi — 4 nayi Kotlin files pehli GitHub build me compile hongi
  (sherpa-onnx JitPack ~40 MB AAR, commons-compress Maven Central).
- NOT RUN: GitHub Actions APK. NOT TESTED: asli phone (`OWNER_PHONE_TEST_LIST_v7_154.md`, 18 items).

## Agla kaam (order me)
1. Owner ko zip do → GitHub push → Actions build GREEN. Compile error aaye to SAB poore theek karo
   (dhyan: JarvisDeepVoice.kt me sherpa-onnx OfflineTts API — assetManager=null + file paths wala constructor;
   Agar OfflineTtsVitsModelConfig ke named params alag hon to sirf wahan adjust karo).
2. Owner se OWNER_PHONE_TEST_LIST_v7_154 ke 18 results lo (PASS/FAIL per number). Deep voice ki awaaz
   pasand aayi ya nahi — ye owner ka review sabse zaroori hai (alan vs ryan/cori/michael badalne ho to
   JarvisDeepVoice.kt me URL+SHA256+MODEL_FILE+DIR_NAME ek jagah badalna hai, tools/test_jarvis_upgrade_contract.py ka pin bhi).
3. Barge-in phone par aggressive/late lage to: JarvisDeepRules.MIN_CONF (0.85) aur MIN_RMS (1000) tune karne hain.
4. Purane open items khule hain: v7.153 WiFi phone tests, v7.152 light popup + GLM 5.3 404 check,
   v7.150 item 12 (mic text line + keys), MainStallWatchdog frames, Instagram false-block.
5. Agla feature sirf owner ke bole par; add-only rule: purane behaviours mat todo, har naye feature ke saath
   contract test + JUnit + docs (CHANGELOG top, PART_STATUS, OWNER_PHONE_TEST_LIST, NEXT_ACCOUNT_PROMPT).
