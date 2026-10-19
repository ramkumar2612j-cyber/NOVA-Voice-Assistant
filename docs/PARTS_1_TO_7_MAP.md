# NOVA : PART 1 se 7 ka poora naksha (simple Hindi, honest)
Chinh: JUDA = naya add hua | PEHLE SE = purana jo waise hi hai | BAAKI = abhi pura nahi / test nahi

## PEHLE SE (base, kuch hataya nahi)
189 Kotlin files, ui.html (chat, 3D orb, MADAD help, connectors, offline AI pack, music, Lock Guard, screen pehchano, ROZ KA SUDHAAR), 138 JUnit files, 143 tools. Owner-required UI: HOME, ACTIVATE -> STOP, Settings SAVE, 3D orb.
Part 1 se pehle ke chhote blocks (v7.30-7.35, ui.html): p1_auto (auto intent chip), p2_auto (photo edit khud shuru), p3_fx (website steps), p4_fx (orb core + stars), p5_log (app log), p6_verify (auto verify) + FeatureVerify.kt.

## PART 1  AUTOPSY + TOOLS  -> JUDA: tools/autopsy_full.py, tools/test_autopsy_full_contract.py, 20-part plan (NOVA_20_PARTS_PLAN.md)
## PART 2  PERFORMANCE GOVERNOR (v7.37) -> JUDA: ui.html me phone khud naapta hai (FPS + atakna) aur quality 1-4 chunta hai (html[data-perf]); level 3-4 purani lowfx class use karte hain. Test: tools/test_perf_governor_contract.py
## PART 3  NATIVE SMOOTHNESS (v7.38) -> JUDA: NativeSmooth.kt + NativeSmoothTest.kt; MainActivity onPause / onResume / onTrimMemory -> page ko window.novaNative bhejta hai; background me CSS animation ruk jaate hain; RAM kam ho to effects kam.
## PART 4  SAFE STOP (v7.39, v7.40) -> JUDA: SafeStop.kt (STOP sirf bolna / kaam rokta hai, NOVA ON rehta hai, aadha suna command phenk deta hai), ExtCfg.stopPowersOff (default OFF; Settings switch Part 17), CmdQueue.kt (10 command tak line me), queue strip. Tests: SafeStopTest, CmdQueueTest, test_safe_stop_contract.py
## PART 5  FUTURISTIC DESIGN SYSTEM (v7.41, v7.42) -> JUDA: p5_ds / p5_ds2 neon-glass tokens poore app par, boot sequence, p5x_fix (ek "Photo / Video" button, NOVA khud photo / website decide karta hai), photoToSite bridge. Check: tools/check_p5_design.py
## PART 6  HOME REDESIGN (v7.43) -> JUDA: orb ki 6th state "NO INTERNET", 5 chip ka status board (AI, INTERNET, PERMISSION, KAAM, AWAAZ), 4 button ka dock (CHAT, CHUP, SILENT, STOP). Purani ACTIVATE row chhipi hui, ids wahi. Check: tools/check_p6_home.py
## PART 7  MOTION (v7.44) -> JUDA: tab slide, chat rise, haptic + HAPTIC ON/OFF button, number ticker, HUD chat card glass gradient (NovaHud.kt 1 line), intro glass caption + vignette. Check: tools/check_p7_motion.py

## Is baar ki jaanch me mile aur theek kiye
- Part 6 ke status chips 40-41px the, ab 44px (touch ke liye). tools/test_part5_ui_screens.py ab PASS.
- tools/test_screen_session_contract.py FAIL tha: asli bug nahi, test sirf 500 akshar dekhta tha jabki v7.40 queue block ne line aage dhakel di. Window 2500 kiya, ab PASS.

## BAAKI (abhi pura nahi)
1. (v7.45 DONE) 12 purane guard: 3 adapt (payment, social, photo_publish), 5 merge (tools/test_adapted_autopsy_contract.py), 4 retire (docs/retired_guards/). Auto-reply ab sirf AI (v7.46): fixed message kahin nahi, har baat AI karta hai aur owner ko batata hai. Open: wake word locked phone me (owner decision).
2. COMPILE / JUnit / PHONE TEST: NOT TESTED. Asli build GitHub Actions par. OWNER CHECKPOINT 1 phone par baaki.
3. photoToSite() (Part 5) compile / phone test baaki. Voice se bola gaya command jab NOVA busy ho wo queue me nahi jaata (sirf typed).
4. Aage: Part 8 se 20 (Chat UI se Final tak).

## PART 8  CHAT UI (v7.47) -> JUDA: pt8_chat_css / pt8_chat (bubbles + label + time, typing dots, voice waveform patti). Typing reveal pehle se p3_fx me tha. Check: tools/check_p8_chat.py
