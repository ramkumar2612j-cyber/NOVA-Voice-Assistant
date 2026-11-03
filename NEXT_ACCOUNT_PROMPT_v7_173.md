# NEXT ACCOUNT PROMPT - v7.173 (Opening FX2 + Ultron/Jarvis voice choice) - POORI DETAIL

## Project aur owner
NOVA Voice Assistant, Android, Kotlin, package com.nova.assistant (UI = app/src/main/assets/ui.html WebView + Kotlin bridge MainActivity.kt). Repo: https://github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant (branch main, sirf EK branch). Owner non-technical, sirf Android phone (Chrome + Termux, clone folder ~/n139), Hinglish me baat, chhote messages (phone par ~7 lines), EK step ek time, clickable full URL. Owner kuch manually edit/merge nahi karta. Compile sirf GitHub Actions (build.yml - mat chhuo).

## Rules (hamesha)
add-only (koi feature/API/fallback/connector/screen/setting/permission mat hatao), fake success claim NAHI (bina saboot DONE/WORKING/VERIFIED mat bolo), risky action par spoken local "yes", no silent install, API key log/repo me nahi, NOVA apna code khud nahi badalta. GitHub push owner ke kehne par. Har delivery: ZIP + simple Hindi status (kya hua / kya nahi / kya test hua) + yeh prompt ka next version (FULL detail, chat me bhi likho). Progress % batate raho; jahan ruke wahin se shuru.

## Owner ne is round me kya manga
1. App khulte waqt dialogue bole, animations + full live effects ke saath.
2. Awaaz "original Ultron ya Jarvis" jaisi.
3. Poore app ka check: har feature chal raha hai ya nahi; jo nahi chal raha use really working banao.
4. Completed ZIP jisse APK build ho.

## Sach (owner ko bata diya)
- Film ke asli actor ki awaaz mere paas nahi hai / clone nahi ho sakti. Jo ho sakta hai: phone ka TTS + robotic effect (Ultron = deep/heavy, Jarvis = calm/clean). Quote wahi hai: "Keep your friends rich and your enemies rich, and wait to find out which is which." (OpeningVoice.QUOTE).
- Sandbox me Android SDK/Gradle/kotlinc/Chromium/TTS nahi, internet band. Kotlin compile, GitHub Actions, asli phone, awaaz sunna: NOT RUN.

## Ab tak (is round)
### Step 1 - v7.173 FX2 (sirf ui.html, add-only)
Naya <style id="v173-fx-css"> + <script id="v173-fx-open"> (v170-pay-css se pehle). Naya canvas #lv2 (#lv ke baad): HUD corner brackets + live T+ timer/LAT/CORE %, shockwave rings (hit 2850 ms aur finale HA+HOLD+120), edge red vignette pulse, title ke aas-paas lightning arcs (lowfx/slow par band), quote typing ke waqt gol voice-wave (ring of 56 bars radar ke paas). window.__nvOnOpen ko chain kiya (purana prev bhi chalta hai). window.__nvFx2 = {draw,sz,cv} (test hook). Purane layers (#lv rain/radar/boot-log/particles) waise hi.
### Step 2 - Voice style (Kotlin + ui.html, add-only)
- NAYI files: JarvisFx.kt (pure Kotlin: presence band + 220 Hz light ring + 4 ms comb + 110 ms echo + level + gentle tanh; PITCH 1.0, MAX_RATE 0.95), VoiceStyle.kt (ULTRON/JARVIS, normalize/next/pitch/maxRate/process), test VoiceStyleTest.kt (5 tests).
- Cfg.kt: voiceStyle (pref "voiceStyle", default "ultron").
- NovaService.startUltron: pitch/rate/process ab VoiceStyle se (normal replies dono style me). Hindi replies par language nahi badli.
- OpeningVoice.start(ctx,text,amount,style=ULTRON,onStart): Jarvis par Locale.UK, warna US. MainActivity.openingVoice style pass karta hai.
- MainActivity bridge: getVoiceStyle(), setVoiceStyle(v) (v.take(16) cap - test_bridge_caps_contract ke liye zaroori).
- ui.html: Settings me button #s_vstyle ("VOICE: ULTRON/JARVIS", tap = badle + testVoice), script id v173-vstyle-js, BRIDGE list me getVoiceStyle/setVoiceStyle. Boot-log ka static word 'ULTRON' -> 'ONLINE' (style badalne par galat na dikhe).
- Purana ULTRON on/off button aur strength slider CSS se hidden hi hain (owner ka v7.124e faisla); strength dono style par lagti hai.

## Test jo hua (saboot)
- ui.html: 82 inline scripts JS syntax OK (x-shader chhod kar), 0 error.
- FX2: mock-canvas smoke test, 657 frames (0-10.5 s), 0 exception. Voice button: mock Android bridge se 2 tap -> ultron->jarvis->ultron, testVoice 2 baar.
- JarvisFx ka Python port (same math, 16 kHz tone): amount 0.2/0.75/1.0 par NaN nahi, peak 31128 (clip nahi), length +33%.
- tools/run_all_checks.sh (timeout 20 s, sandbox me Chromium nahi): original v7.172 = 137 pass / 33 fail (11 known stale, baaki sandbox-limit). Patch ke baad shuru me 2 naye fail: test_bridge_caps_contract (fix kiya, ab uncapped 0 / 121 checked) aur test_part6_render (akele chalane par PASS). Baaki fail original jaise hi.
- NOT RUN: Kotlin compile + unit tests (VoiceStyleTest, OpeningVoiceTest), GitHub Actions, asli phone, awaaz sunna, Chromium me visual video review.

## Agle account ka kaam (order me)
1. Owner se phone par check karwao: opening me awaaz aayi? FX2 effects dikhe? Settings me VOICE button Ultron<->Jarvis kaam karta hai? Na aaye to ErrLog 'OpeningVoice' / 'MainActivity openingVoice'.
2. Internet ho to Kotlin compile: `tools/local_compile_check.sh` (kotlinc GitHub se, android.jar `git clone --filter=blob:none --sparse Reginer/aosp-android-jar`) aur unit tests; ya owner ke Termux/GitHub Actions se. Fail ho to sach batao. Dhyan: OpeningVoice.start ka naya default param trailing-lambda ke saath compile hona chahiye.
3. STEP 3 (owner ka bada kaam): poore app ka audit - har feature ki list (JARVIS_FEATURE_INVENTORY_v7_154.md, tools/autopsy_full.py, tools/audit_dead_ui.py), jo UI me hai par bridge/Kotlin se wired nahi ya fail hota hai use dhoondo, real working banao (add-only, koi fake/mock nahi), har fix ke saath test. Jo sirf asli phone par verify ho sakta hai uski alag honest list (OWNER_PHONE_TEST_LIST_*.md me jodo).
4. Chromium ho to: `python3 tools/capture_opening_frames.py /tmp/fr 10.5 voice 3 0` se frames, FX2 ka overlap check 390x844, 360x640, 412x800, 320x640 par (rings/arcs/HUD title ya quote ko na dhakein).
5. STEP 4: final full ZIP (NOVA_v7_17x_*.zip), simple Hindi status, is prompt ka next version. build.gradle.kts versionName abhi "7.162" stale hai; bump se pinned tests stale ho sakte hain - pehle check.
6. Optional (owner approve kare): opening voice pre-generate/cache (dheeme phone par turant bole); Jarvis ke liye alag quote/greeting line.

## Known / dhyan
- tools/KNOWN_STALE_GUARDS.txt me 11 known stale guards. Sandbox bina Chromium me ~22 aur check fail dikhte hain (ui_shots/playwright wale) - yeh code bug nahi, environment hai; Chromium wale sandbox me pehle 150 pass / 20 fail aaya tha.
- Hook names ui.html me: __nvOpen (read-only state), __nvOnOpen (startOpen ke end me), __nvFx2.
- Files badli: ui.html, Cfg.kt, NovaService.kt, OpeningVoice.kt, MainActivity.kt; nayi: JarvisFx.kt, VoiceStyle.kt, VoiceStyleTest.kt, PART_STATUS_v7_173_FX2.md, PART_STATUS_v7_173_VOICE.md, yeh prompt, CHANGELOG top.
