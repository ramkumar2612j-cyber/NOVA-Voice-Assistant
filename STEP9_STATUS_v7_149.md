# STEP 9 OF 10 (v7.149): MODERN LIGHT POPUP
Owner order 30 Oct. Add-only. CHANGELOG.md not touched.

## What it does
- Light popup theme, OPT-IN (default stays the dark Ultron card, because the phone is not proven stable yet): frosted white-blue card (rose tint while NOVA speaks), dark readable text, darkened state colour for the "NOVA" label, light menu card and minimized pill.
- Effect: slow soft blue shimmer sweep across the light card (2.8 s loop, alpha max ~16%, 0 at both ends, one shader moved by a matrix: no per-frame allocation).
- Voice: "nova light popup karo / halka popup" and "nova dark popup karo / ultron popup"; needs the word "popup" (so "torch light" never matches). Applies from the NEXT popup.
- Files: HudTheme.kt (pure), ExtCfg.lightPopup/setLightPopup, NovaHud.kt (HudView: `light` flag + ~12 colour sites + shimmer), NovaService voice route (before FeatureVoice), HudThemeTest (4 tests).

## Evidence
- HudTheme + HudMath + test compiled with kotlinc 2.0.21 and run with a JUnit shim: 4/4 PASS.
- 160 of 162 contract scripts PASS; test_perf_governor_contract failed only under parallel load and PASSES alone; test_part4_fx_render needs playwright (not run here).
- NOT DONE: NovaHud / NovaService / ExtCfg not compiled, Android build NOT CONFIRMED, phone NOT TESTED (look, contrast, shimmer smoothness, battery). Dark buttons and the Ultron head are unchanged in the light card (looks fine on paper, unverified). The chat typing box (opened from the three dots) is still dark. No Settings toggle yet, voice only.
