# NOVA v6.34 — SPEED + OFFLINE QWEN AUTO (handover)

Owner: Android phone only, Hinglish, short steps, one `Aapko karna hai:` line per reply. Never claim compile/CI/phone test passed unless it ran.

## What changed (from v6.32)
1. SecureStore: Keystore key handle + decrypted values cached in memory. Before, every Android.getFeatureStatus() (every 8 s), AutoTest, AutoSetup etc. did many Keystore AES-GCM decrypts -> page froze/hung.
2. NovaService.onCreate: startForeground() first; Keystore migrations + AgentTaskLedger repair moved to a background thread.
3. ui.html: orb WebGL loop capped at 60 fps (30 fps in lowfx) because boostRefresh() runs the screen at 90/120 Hz; weak phones (<=4 cores or <=3 GB) start in lowfx and stay there; sync poll slowed to 1 s and paused when hidden.
4. Offline Qwen: AutoSetup.startQwen() = own thread, starts ~2.5 s after app open, retries with back-off until installed (resumes the .part file), own busy flag (no longer blocked by the 14 MB voice model), 90 min wait, 256 KB read buffer. Offline brain is used automatically once installed (AiRouter). LocalBrains.warmUp() preloads the model in background on >=4 GB phones so the first offline answer is not a 45 s wait.

## Evidence level
Static review only. Not compiled, not phone tested (no Android SDK here). Run CI (Actions) and test on the phone.

## Added in the same v6.34 zip
- Voice save once: NovaService.startVoiceEnroll() returns "saved" when a profile exists; UI button shows "AWAAZ SAVE HAI ✓" (disabled). AWAAZ HATAO re-enables recording.
- Voice-check model (14 MB, vosk-model-spk-0.4): own runner (AutoSetup.startVoiceModel) at app open, retries with back-off.
- Not changed: speaker model stays vosk-spk-0.4 (only one the Vosk engine supports); Hindi listening pack stays vosk-small-hi-0.22. A better speaker engine (ONNX ECAPA) needs a new library + feature code and a phone test.
