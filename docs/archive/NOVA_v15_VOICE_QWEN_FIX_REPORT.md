# NOVA v15 — Voice + Offline Qwen Fix Pack (v6.32 / build 14)

## Fixed from the phone screenshots

1. **Repeated voice-enrollment loop fixed**
   - Removed the second delayed auto-enrollment path from `NovaService`.
   - `AutoSetup` is now the single owner of automatic enrollment.
   - A persistent one-time guard prevents a failed enrollment from silently starting another 5-sample session after every service restart.
   - Manual `AWAAZ SAVE KARO` remains available.

2. **`no-voice-vector` handling strengthened**
   - Speaker model readiness now checks the official core speaker-model files instead of only a generic file count.
   - Speaker extraction now creates a normal Vosk recognizer and explicitly attaches `SpeakerModel` with `setSpeakerModel()`.
   - Larger audio chunks are fed to the speaker recognizer.
   - All completed Vosk segments plus the final result are collected and merged.
   - A missing `spk_frames` field is handled conservatively instead of discarding an otherwise valid x-vector.
   - The command model (`model-en-in`) is preferred for speaker extraction when present; the wake model remains the fallback.
   - Missing/invalid vectors still fail closed and cannot execute commands.

3. **Offline Qwen screen fixed**
   - Removed the old UI text claiming the offline engine was disabled.
   - Setup now reports actual engine/model/RAM state.
   - Qwen button no longer uses the obsolete “engine is disabled” message.
   - Offline Qwen remains a verified 491 MB GGUF download with SHA-256/size validation.
   - Automatic background download remains enabled when the device has enough RAM.
   - Offline model install result no longer says an engine must be plugged in.

4. **Offline Qwen engine integration hardened**
   - `llamacpp-kotlin:0.1.0` remains used because this project is on Kotlin 1.9.24; 0.1.0's published dependency history matches that Kotlin generation.
   - `LlamaBrain` now uses the documented `LlamaHelper` load callback form and waits for its event collector to subscribe before prediction, preventing dropped first tokens.

5. **Version bumped**
   - versionName: `6.32`
   - versionCode: `14`

## Validation

- All available contract scripts: **33/33 PASS**.
- ZIP integrity checked after packaging.
- Kotlin/Android Gradle build is **not claimed as verified in this sandbox** because a Gradle wrapper/build environment is not available here.
- Physical-device end-to-end validation is still required for the native Vosk speaker model and llama.cpp runtime.

## Important behavior

Android still controls runtime permission dialogs; NOVA cannot silently grant microphone/notification permissions. After permission is granted, the app avoids the old repeated enrollment/permission behavior.

The voice lock is speaker verification, not a cryptographic identity guarantee. It is designed to reject ordinary different voices and fails closed when the vector/model is unavailable; high-risk actions retain their own confirmation checks.

## Repository naming alignment
- GitHub repository: `skfashion142/NOVA-Voice-Assistant`
- Project root directory in this archive: `NOVA-Voice-Assistant/`
- Gradle `rootProject.name`: `NOVA-Voice-Assistant`
- Android `namespace` and `applicationId` remain `com.nova.assistant` intentionally; changing these would change the app identity and can break installed-app upgrades/signing/data continuity.
