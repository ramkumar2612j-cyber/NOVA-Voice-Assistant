# NOVA v15 — Qwen Offline + Voice Auto-Setup Upgrade

## Changes
- Added a real Android GGUF inference adapter using Kotlin-LlamaCpp.
- Enabled the offline Qwen2.5-0.5B GGUF slot in `LocalBrains`.
- Offline Qwen is automatically offered/installed in the background when RAM is sufficient.
- Existing resumable download + size + SHA-256 verification remains the gate before activation.
- Voice speaker model remains automatic and silent.
- Hindi voice pack remains automatic and silent.
- First-run voice enrollment now starts automatically after the voice service is running and the speaker model is ready.
- Until a voice profile exists, voice-locked NOVA refuses command execution rather than accepting an arbitrary speaker.
- After 5 valid enrollment samples, the voiceprint is saved atomically and voice lock is enabled.
- Voiceprint validation rejects empty, non-finite, or zero-norm profiles.
- Basic permission prompts are no longer repeatedly requested after the first decision; granted permissions are reused automatically.
- Existing security gates, spoken confirmations, protected-screen rules, and no-secret rules were preserved.

## Testing
- Contract scripts executed: 27
- Contract script failures: 0
- Voice contract: PASS
- Hindi contract: PASS
- Review/security contract: PASS
- Auto-setup contract: PASS
- Full contract-suite result: 27 scripts, 0 failures

## Android build
UNVERIFIED. This sandbox does not contain the Gradle wrapper/Android build environment and cannot resolve/build the Android dependency here. No fake BUILD SUCCESS is reported.

The new dependency is `io.github.ljcamargo:llamacpp-kotlin:0.1.0`, selected because its published dependency history matches the project's Kotlin 1.9.24 era. The current latest 0.4.0 line requires Kotlin 2.3.20, so it was not used.

## Important runtime behavior
- Qwen download can be large (~491 MB) and runs silently in the background.
- The model is only used after download verification and successful local-engine load.
- Android itself controls runtime permissions. NOVA cannot silently grant microphone/notification permissions. Once the user grants them, the app checks the current state and does not intentionally re-prompt on every launch.
- Accessibility, overlay, default-assistant, notification-access, battery-optimization and similar special settings remain user-controlled by Android.
