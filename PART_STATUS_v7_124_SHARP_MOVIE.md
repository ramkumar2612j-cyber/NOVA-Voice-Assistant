# v7.124 SHARP hang fix + movie name after scan (lite series; versionName stays 7.114)
- SHARP hang: on HIGH phones SHARP was capped at perf level 3, so a real freeze could never stop the heavy 3D orb. Cap is now 4 (2D orb only after REAL stalls); SHARP pixel cap 3 -> 2.5; new `sharpguard-v7124` script: 3 s after start, 4 s of frame timing, if the phone is really slow (avg > 70 ms or 1 frame in 3 > 120 ms) the 3D orb goes to 2D (not remembered, SHARP stays chosen, text stays sharp).
- Popup / voice "analyse karo" (bare sentence, <= 5 words) now reads the live screen (it used to fall to a plain chat answer or a UI-text read). In a non-video app the frames are read too; if the result is not a video the normal screen reading continues as before (`screenVideoRead(generic)`).
- Movie name after the scan: the last real scan result (name + confidence, no frames) is kept 10 min (`LastMovieRules`). "movie ka naam kya hai / uska naam / movie name" is answered from it with the same honest confidence. "ye kon si movie hai", "dobara", "abhi", "scan" still start a fresh scan; actor / hero / "kaun hai" questions are never matched.
- Tests: LastMovieRulesTest (5). Rules + ScreenVideoRulesTest run with kotlinc + JUnit here: 16 PASS. NOT COMPILED as a whole app (no Android SDK), NOT TESTED on a phone.

PASS: LastMovieRules + ScreenVideoRules logic (JUnit, 16 tests). ui.html scripts parse (same as before).
NOT TESTED: whole-app compile (NovaService edits), SHARP on a real phone, popup "analyse karo", movie name after laser scan.
Owner test: 1) Settings > GRAPHICS QUALITY = SHARP, restart, use 2 min: should not freeze. 2) Play a movie clip, popup > "analyse karo": laser scan, result. 3) Then say "movie ka naam kya hai": name comes without a new scan.
