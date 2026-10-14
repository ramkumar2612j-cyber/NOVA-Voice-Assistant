# NOVA NEXT ACCOUNT PROMPT v57 — finish the ONNX speaker upgrade + verify v6.34 (read fully)

Owner: Android phone only, not technical, writes Hinglish. Reply in short Hinglish, one step at a time, end every reply with exactly one `Aapko karna hai: ...` line. Exact GitHub links: repo https://github.com/skfashion142/NOVA-Voice-Assistant (upload: /upload/main, builds: /actions). Never claim compile/CI/phone-test passed unless it really ran. Keep all existing files and earlier prompts. No build dirs / __pycache__ in the zip.

## State of this zip (v6.34, versionName 6.34)
DONE and in the normal source tree (static review only, NOT compiled by Gradle, NOT phone tested; Fbank.kt IS compiled + numerically tested):
1. SecureStore caches the Keystore key + decrypted values (fixes the freeze caused by Android.getFeatureStatus() every 8 s).
2. NovaService.onCreate: startForeground first; Keystore migrations + AgentTaskLedger repair on a background thread.
3. ui.html: WebGL orb capped at 60 fps (30 in lowfx); weak phones (<=4 cores or <=3 GB) start in lowfx and stay; sync poll 1 s and paused when hidden.
4. Offline Qwen auto: AutoSetup.startQwen (own thread, retry/back-off, resume, own busy flag in ModelAuto, 90 min wait, 256 KB buffer) + LocalBrains.warmUp (>=4 GB RAM).
5. Voice saved once: NovaService.startVoiceEnroll returns "saved" if a profile exists; UI button shows "AWAAZ SAVE HAI ✓" (disabled); AWAAZ HATAO re-enables.
6. Voice-check model (Vosk spk 14 MB) own auto-download runner AutoSetup.startVoiceModel.
7. Fbank.kt (pure Kotlin Kaldi 80-bin fbank + CMN, same as WeSpeaker): matches an independent numpy reference to 1e-6 (see wip_onnx_speaker/fbank_reference.py). It is not used yet.

## The owner's screenshot showed an OLD build
The owner's phone screen said "Qwen apne aap download nahi hota. Is build me offline engine band hai" — that text does not exist anywhere in this source. So the APK they ran was NOT built from this code. First job: make sure the new zip is uploaded and the green APK is installed, then ask for a Setup > Model Download + MERI AWAAZ screenshot. If it still says "engine band", read the build log (llamacpp-kotlin / abiFilters arm64 only).

## NOT DONE: better voice recognition pack (owner request, answered "add now with safe fallback")
Goal: a better speaker-ID engine used automatically when the phone is strong enough, otherwise the old Vosk speaker model; all automatic. Files parked (so CI stays green) in `wip_onnx_speaker/`:
- `OnnxSpeaker.kt.txt` — move to app/src/main/java/com/nova/assistant/OnnxSpeaker.kt (drop the .txt). Written, never compiled. WeSpeaker ResNet34 ONNX: URL https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM/resolve/main/voxceleb_resnet34_LM.onnx , size 26530309, sha256 7bb2f06e9df17cdf1ef14ee8a15ab08ed28e8d0ef5054ee135741560df2ec068 (read from the HF tree API; re-check). Uses ONNX Runtime; reads the input name from the session; output = 256 floats.
- `FbankMain.kt.txt`, `kc.sh.txt`, `fbank_reference.py` — how the fbank was verified (Kotlin compiler jars live in Gradle's lib folder; Maven/PyPI torch are blocked in that sandbox).

Steps to finish:
1. app/build.gradle.kts: add `implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.1")` (confirm it exists; keep abiFilters arm64-v8a). Move OnnxSpeaker.kt into the source tree.
2. VoiceLock: profile JSON keeps "v" (Vosk 128-d) and optional "v2" (ONNX 256-d). `activeEngine(ctx)` = "onnx" if v2 exists AND OnnxSpeaker.usable(ctx) else "vosk". `loadProfile` returns the active engine's print. `vector(ctx, md, pcm, rate)` returns the active engine's vector; if ONNX returns null, fall back to the Vosk vector for that call. Add `vectorVosk` / `vectorOnnx` (enrollment needs both), `saveProfile(ctx, v, v2)`, `lastEngine`, `threshold(ctx)` and `markTuned(ctx)`.
3. NovaService enrollment (~line 917-930, voiceAllows): compute both vectors per sample, keep two lists (enrollVecs, enrollVecs2), save both averages. In startVoiceEnroll's worker thread also call OnnxSpeaker.ensure(this) (failure is not fatal) so new users get both prints.
4. Thresholds: replace the 3 uses of SpeakerRules.effectiveThreshold(...) (voice test ~line 594, tuneSample, voiceAllows) with VoiceLock.threshold(this): Vosk = old behaviour; ONNX = cosine-distance levels STRICT 0.45 / NORMAL 0.55 / LOOSE 0.65 (GUESS, not tuned on a phone) and the owner's tuned value only if it was tuned with the same engine (markTuned). Add the engine name to the chat "Awaaz match" line.
5. AutoSetup.startVoiceModel: after the Vosk speaker model is ready also call OnnxSpeaker.ensure (only if OnnxSpeaker.wanted). Add a bridge `voiceEngine()` + show it in the UI status line ("naya ResNet" / "purana Vosk"). Existing owners with only a Vosk profile keep the Vosk engine until they press AWAAZ HATAO and save again (say so).
6. Unit tests: OnnxSpeaker.deviceOk, Fbank (frameCount, constant-signal behaviour), profile v/v2 JSON handling (put the JSON part in a pure object so it is testable).
7. Evidence: run the pure Kotlin tests if possible, then upload for CI. Say honestly that the ONNX call and the thresholds are untested on a phone; the safety net is: two ONNX errors in a row mark the engine bad (per app version) and the Vosk engine takes over.

Also open: owner asked about a bigger offline LLM (Qwen2.5-1.5B Q4_K_M, 1117320736 bytes, sha256 6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e) for >=8 GB phones — NOT added (LocalBrainRules.TIMEOUT_MS is 8 s, probably too slow). And a big Vosk Hindi/en-in server model was refused (1-1.5 GB, huge RAM, native crash risk) unless the owner insists, then only for >=12 GB phones with a crash guard flag.

## Rules to keep (from earlier prompts)
No silent permission grants/installs/payments; never expose keys; do not automate secure screens; do not bypass voice/face checks; risky Send/delete keeps its spoken yes.
