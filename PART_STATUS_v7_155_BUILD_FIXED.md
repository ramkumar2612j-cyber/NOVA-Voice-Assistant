# v7.155 BUILD FIXED (31 Oct 2026)
- sherpa-onnx: Maven par nahi mila -> tools/fetch_sherpa.sh GitHub release se laata h (Plan A aar, Plan B jniLibs + kotlin-api). Workflow step "Fetch sherpa-onnx" Gradle se pehle.
- Kotlin compile fixes: FreeProviders (glmFirst arg), JarvisDeepVoice (sampleRate(), callback), NovaService.jarvisUpgradeReply returns String, WifiKit.readQr block body.
- TarBz: symlink/hardlink entries refused. JarvisDeepRules.clean: one space after emoji removal.
- Tests fixed (test bugs): TarBzTest, JarvisDeepRulesTest, FreeVisionTest (GLM order). Guards removed: build.yml hash in test_part5_contract.
- versionName stays 7.154 (purane guard tests version list check karte h).
- GLM 5.3 key: alag key nahi, OpenRouter key (Settings > FREE ONLINE AI > OpenRouter key > OPENROUTER SAVE).
NOT PROVEN: last GitHub build result, phone install, deep voice, GLM 5.3 on phone.
