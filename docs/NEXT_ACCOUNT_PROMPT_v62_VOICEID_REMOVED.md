# NOVA next-account prompt (v6.50 -> 6.51 in progress)
Owner: Hinglish, short replies. Repo https://github.com/skfashion142/NOVA-Voice-Assistant (upload /upload/main, build /actions/workflows/build.yml). Always send zip named NOVA-Voice-Assistant.zip via SendUserFile, say honestly RUN / NOT RUN. Sandbox has no Gradle/Android build.

## Task in progress: remove speaker-ID / owner-only / voice unlock / pattern / face gate
Owner rule: when phone is LOCKED NOVA may hear and speak, but takes NO new task; old running task continues. Anyone's voice is accepted when unlocked.

### DONE (not compiled, not tested)
- Deleted: VoiceLock, OnnxSpeaker, SpeakerRules, VoiceFaceRules, VoiceEngineRules, Fbank, FaceGate, PatternUnlock, PatternSetupActivity (+ tests FbankTest, SpeakerRulesTest, VoiceFaceRulesTest, VoiceEngineRulesTest, VoiceSetupSafetyTest; python test_voice_contract, test_voicelock_selftest_contract).
- New PhoneLock.kt (KeyguardManager); NovaService.lockedPhoneAllows() in dispatch (allows answers to running job, "stop", wake-alarm OFF; else speaks refusal).
- Edited: NovaService (voice-ID code, wakePcm/vpre removed), MainActivity (bridge methods removed), FeatureStatus (row "phonelock"), AutoSetup/AutoSetupPlan (VOICE task removed; test updated), ModelAuto (only Qwen), ExtCfg, SelfTest, VoiceFlowRules(+Test: EnrollGuard gone, AlertGate 4 args), AndroidManifest, themes.xml, build.gradle.kts (onnxruntime dep removed), ui.html (voice/face boxes, JS, Pattern row removed), tools/test_autosetup_contract.py, ui_shots.py.

### TODO
1. Edit tools/test_ai_rank_autolearn_contract.py lines ~22 and ~25 (still require lockOptIn / voiceAllows / faceAllows) -> replace with "files deleted" + "lockedPhoneAllows in NovaService" checks.
2. Run all tools/test_*.py; fix failures (test_part1_voice_flow.py, test_part2_security.py, SafetyInvariantsTest may reference removed things).
3. grep leftovers: VoiceLock|OnnxSpeaker|FaceGate|PatternUnlock|voiceLock|voiceSilent|faceGate|enroll in app/ tools/ (docs/archive may stay).
4. node --check ui.html scripts; brace balance of edited Kotlin; rerun pure-Kotlin kotlinc harness (/home/claude/kt/kotlinc, rebuild file list, -J-Xmx6g, JUnitCore from app/ dir). Last good: 521 tests at v6.42.
5. Bump version 6.51 (app/build.gradle.kts), update CHANGELOG, README, docs/HANDOFF.md (it still describes owner-only voice/unlock), rezip, send.
6. Tell owner: old saved voiceprint/ONNX files on phone are just unused; the attached NOVA_v15_assistant.zip is old (6.31) and was not modified.
Keep: llamacpp-kotlin 0.1.0 API, androidx.core forced 1.13.1.
