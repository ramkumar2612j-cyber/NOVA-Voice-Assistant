# STEP 8 OF 10 (v7.148): WATCH SE BOLKE KAAM (diagnosis)
Owner order 30 Oct. Add-only. CHANGELOG.md not touched.

## Honest finding
Root cause of "watch se kaam nahi karta" is NOT proven. Code read: manifest VOICE_COMMAND -> WatchCommandActivity -> NovaService.wakeFromWatch -> WatchMic.start (Bluetooth SCO) -> wakeNow. Permissions present (RECORD_AUDIO, BLUETOOTH_CONNECT, MODIFY_AUDIO_SETTINGS). Weak spots seen in code: wakeFromWatch returned false SILENTLY when NOVA was off / busy / phone locked; nothing proved whether Android even delivered the watch button.

## What this step adds (changes no behaviour)
- WatchDiag.kt (pure): remembers last 6 watch events in memory (button, service_off, busy, locked, mic_watch, mic_phone) and says where the last press stopped.
- WatchCommandActivity + NovaService.wakeFromWatch: one WatchDiag.add line each.
- Voice (FeatureVoice key "watchdiag", read-only): "nova watch ka haal batao", "watch button status".
- WatchDiagTest (4 tests).

## Evidence
- WatchDiag + test compiled with kotlinc 2.0.21 and run with a JUnit shim: 4/4 PASS.
- Contract scripts step6, step7, part5_feature_voice, part5, part20: PASS.
- NOT DONE: Android files (NovaService, FeatureVoice, Activity) not compiled; build NOT CONFIRMED; phone and real watch NOT TESTED. Events are lost if the app process dies.

## Owner must answer (one short message)
1. Watch ka model? 2. Watch button dabane par kya hota hai? 3. Dabane ke baad bolo "nova watch ka haal batao" (phone se) aur jo bole woh bhejo.
Reading: "signal nahi aaya" = watch/phone settings (voice assistant = NOVA, ALWAYS). "mic nahi diya" = watch call-audio (SCO) support.
