# NOVA voice, Android limits and battery (v6.75)

Everything here is read from the source and the JUnit / contract tests. **Nothing was tested on a phone** (NOT TESTED). Nothing was compiled in the sandbox that produced v6.75 (NOT RUN).

## 1. The voice chain and what can fail
| Step | Where in the code | What the owner sees when it fails |
|---|---|---|
| Microphone permission | `VoiceDiagnosis.wake` | row "wake word": off, "Microphone permission nahi hai", fix = mic |
| Foreground service (mic type) start | `NovaService` stores the start failure, `VoiceDiagnosis.classifyStartFailure` names it | Android 12+ refuses a microphone service started from the background: "NOVA app kholo aur screen par rehte hue ACTIVATE dabao". Android 14+ refuses it without RECORD_AUDIO: "Mic allow karo, phir ACTIVATE dabao". Anything else: "dobara ACTIVATE dabao; baar baar ho to phone restart karo" |
| Wake word listening | Vosk grammar (`Logic.wakeGrammar`); Porcupine file exists as an optional engine | last fatal text of the loop (mic busy, model missing) is shown while the service is NOT running |
| Command speech to text | Vosk offline; Groq / Gemini audio when a key is set | see `GroqStt.kt` and the listen loop in `NovaService` (chain order not re-audited in Part 5); with nothing heard the wake counts as false and NOVA sleeps quietly (`Logic.isFalseWake`) |
| Spoken replies (TTS) | Android TextToSpeech, init code kept (`ttsInitCode`) | init error: "Settings > Text-to-speech me engine chuno aur voice install karo" (opens that page); Hindi voice missing: warning, English replies still work |
| Battery / background | `VoiceDiagnosis.battery` | restriction on: "Android kuch der baad NOVA ka mic band kar sakta hai" + brand note |

`FeatureStatus` and `ConfigReport` use these rows, so the app shows the real reason instead of a generic "not ready".

## 2. Battery notes (per brand)
- Any phone: set NOVA to **Battery: No restrictions** (Android Settings > Apps > NOVA > Battery). Without it Android may stop the microphone service after a while.
- Xiaomi / Redmi / POCO: also turn **Autostart** ON for NOVA.
- Oppo, Realme, OnePlus, Vivo, iQOO, Huawei, Honor, Samsung, Tecno, Infinix, itel: also allow auto-start / background activity for NOVA (names differ per brand).
- Other brands: the battery setting alone is shown.
- The app can show the warning but cannot change these settings itself; it opens the right page. Whether the warning matches a given phone model: NOT TESTED.

## 3. What Android does not let NOVA do (answered clearly, never bypassed)
| Request | NOVA says / does |
|---|---|
| power off, restart, reboot | use the power button (no public API for apps) |
| factory reset, wipe | refuses; points to Settings > System > Reset options |
| cancel alarm / timer | cannot; use the Clock app (NOVA keeps no handle to alarms it set) |
| Wi-Fi, Bluetooth, mobile data, hotspot, airplane mode, location, do not disturb on/off | opens the right Settings page; the switch is the owner's |
| unlock the phone | unlocking was removed (no voice / pattern / face unlock) |
| locked screen, banking / payment / secure screens, secure settings | blocked by the existing safety rules (`SafetyInvariantsTest`); no bypass |

## 4. Wake word: false triggers and misses
- The wake word must open the sentence (at most one short token before it, e.g. "hey nova"): `Logic.heardWake`.
- A partial result must repeat `WAKE_CONFIRM_HITS` (4) times in a row; a final result counts at once.
- After a turn ends the wake word is ignored for `WAKE_COOLDOWN_MS` (3 s) so NOVA does not hear its own voice.
- Silence or noise after a wake: back to sleep quietly, no spoken "did not understand", no card (`Logic.isFalseWake`).
- Timing: nothing said within 3 s of the wake ends the turn; a command ends after 1.8 s of silence; hard cap 60 s.
- Covered by `WakeStrictTest`, `WakeAdaptTest`, `ListenGuardTest`, `WakeRulesTest` (not run here). The real false-trigger / miss rate in a room with TV or music: NOT TESTED.

## 5. How a spoken sentence is handled
1. `Understand.classify`: strict `Logic.classify` first, then the loose reading of long casual sentences (`Understand.simplify`, only for ONE clearly safe intent).
2. A negated sentence ("volume kam mat karo") gives no local command and goes to the AI.
3. Sentences with a song title ("gaana bajao tum hi ho") are longer than 4 words, so no plain play / pause command; the AI picks the song.
4. No local answer: the cloud AI (only if a key is set), otherwise the offline brain.
The 181-phrase corpus is `app/src/test/java/com/nova/assistant/CommandCorpusTest.kt`.
