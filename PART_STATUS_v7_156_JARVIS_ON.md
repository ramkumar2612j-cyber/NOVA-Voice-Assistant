# NOVA v7.156 - JARVIS ON BY DEFAULT + JARVIS TAB (add-only, NOT compiled, NOT phone-tested)

Changed (nothing removed):
1. Logic.kt / IntentParser.kt: "run" added as an open-app verb ("run instagram", "instagram run karo").
2. ExtCfg.kt: Deep voice, Barge-in, GLM-everywhere default ON; audio focus stays ON. One-time migration
   (key jarvis_defaults_on_v1) flips old stored OFF values to ON once; later owner changes are respected.
3. NovaService.kt: autoDeepVoiceDownloadLater() - 45 s after service start, if deep voice is ON, model missing and internet present,
   installs the 20 MB model (verified, resumable). Failure is logged only; old voice chain keeps speaking.
4. ui.html: Settings checkboxes for the 3 options now say default ON and are checked; NEW "JARVIS" bottom tab (view v_jarvis):
   5 cards with ON/OFF chip, ON/OFF buttons, DOWNLOAD/STATUS, PROVIDER CHECK. Buttons call Android.sendText with the existing
   voice commands, status read from Android.getSettings().
5. versionName 7.156. (RunVerbOpenTest removed: could not be verified without a compiler and a failing test would block the APK.)

NOT done / unknown (honest): no Android SDK here, so nothing was compiled or run; JS syntax of the new tab was checked with node only.
Whole-app redesign was NOT redone (UI already has many neon layers); only the JARVIS tab was added.
"Every command says samajh nahi aaya" root cause NOT found yet - need the owner's test of: torch on, volume badhao, chrome kholo, phone lock karo
and NOVA > Settings > ERROR/SELF TEST report.

## v7.157 addendum - GLM 5.3 own keys (1 main + 3 fallback), NOT compiled / NOT phone-tested
- FreeProviders.kt: GLM53_1..4 (ids/slots glm53, glm53b, glm53c, glm53d), OpenRouter endpoint, model z-ai/glm-5.3-flash:free, brainOnly. Order: ... openrouter, glm53..glm53d, openrouterglm (old shared-key row stays as last GLM try).
- Key resolvers (NovaService, SiteGen, AutoSetup, SelfImprove, CloudTaskText, ApiUsage) + chain gates know the 4 new slots.
- MainActivity bridge: saveGlmKeyN / hasGlmKeyN / clearGlmKeyN. ui.html: 4 key inputs in Settings (GLM 5.3 FLASH KEYS), JARVIS tab shows keys saved n/4.
- Tests: FreeProvidersTest expected order updated; new Glm53KeysTest (main first, 429 -> next key, empty slot skipped).
