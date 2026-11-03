# NEXT ACCOUNT PROMPT - v7.174 (Step 4 final: Opening FX2 + Ultron/Jarvis voice + audit)

## Project aur owner
NOVA Voice Assistant, Android, Kotlin, package com.nova.assistant (UI = app/src/main/assets/ui.html WebView + Kotlin bridge MainActivity.kt). Repo: https://github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant (branch main, sirf EK branch). Owner non-technical, sirf Android phone (Chrome + Termux, clone folder ~/n139), Hinglish me baat, chhote messages, EK step ek time, clickable full URL. Owner kuch manually edit/merge nahi karta. Compile sirf GitHub Actions (build.yml mat chhuo). Owner kabhi voice-transcription bhejta hai jo galat/ajeeb ho sakta hai: samajh na aaye to EK baar chhota sawal, baar-baar nahi.

## Rules
add-only (kuch hatao mat), fake success claim NAHI (bina saboot DONE/WORKING/VERIFIED mat bolo), risky action par spoken local "yes", no silent install, API key log/repo me nahi, NOVA apna code khud nahi badalta. GitHub push owner ke kehne par. Har delivery: ZIP + simple Hindi status (kya hua / kya nahi / kya test hua) + is prompt ka next version (FULL detail, chat me bhi). Progress % batate raho, jahan ruke wahin se shuru.

## Owner ne manga
1. App khulte waqt dialogue + full live animations/effects. 2. Awaaz Ultron ya Jarvis jaisi. 3. Poore app ka check. 4. Completed ZIP APK build ke liye.

## Sach
Film actor ki asli awaaz nahi ho sakti; phone TTS + effect (Ultron deep, Jarvis calm, Locale.UK sirf opening me). Sandbox me SDK/Gradle/kotlinc/Chromium/TTS nahi, internet band: Kotlin compile, unit tests, GitHub Actions, asli phone, awaaz = NOT RUN.

## Ab tak (Steps 1-4)
Step 1: ui.html FX2 (script v173-fx-open, canvas #lv2). Step 2: JarvisFx.kt, VoiceStyle.kt, VoiceStyleTest.kt, Cfg.voiceStyle, OpeningVoice.start(ctx,text,amount,style,onStart), bridge getVoiceStyle/setVoiceStyle, Settings button #s_vstyle. Step 3 (audit, static): audit_dead_ui.py REAL GAPS 0; autopsy_full.py REAL GAPS 0; run_all_checks.sh 148 pass / 22 fail (11 known stale + ~11 no-Chromium env); fix: tools/test_part6_skills_contract.py me payqr MAP add. Step 4: final ZIP + OWNER_PHONE_TEST_LIST_v7_173.md + PART_STATUS_v7_173_AUDIT.md + ye prompt. versionName "7.162" jaan-bujhkar NAHI badla (pinned tests stale ho sakte hain).

## Agla kaam (order me)
1. Owner GitHub Actions build chalaye. Fail ho to last 20 lines se fix (dhyan: OpeningVoice.start default param + trailing lambda; caller MainActivity.kt ~985 sahi hai).
2. Build green ho to owner phone par OWNER_PHONE_TEST_LIST_v7_173.md ke 7 point check kare; fail par screenshot + ErrLog ('OpeningVoice' / 'MainActivity openingVoice').
3. Chromium ho to tools/capture_opening_frames.py se frames; FX2 overlap 390x844, 360x640, 412x800, 320x640.
4. Optional (owner approve): opening voice cache, Jarvis ke liye alag greeting line, versionName bump (pehle pinned tests check).

## Dhyan
KNOWN_STALE_GUARDS.txt me 11 known stale. No-Chromium sandbox me ~11 extra fail (environment). Jo sirf phone par verify hoga uski list alag hai, use "working" mat likho.
