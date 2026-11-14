# NEXT ACCOUNT PROMPT - v7.170 (Payment QR + payment check)
Ab tak: v7.169 BIZ ke upar v7.170 add-only (CHANGELOG top). QR image (zxing), PAYMENT QR screen, UPI-app/SMS credit alert se auto payment check + bolkar batana. Owner Hinglish, non-technical, phone par.
Baaki: (1) Kotlin compile + PayRulesTest + GitHub Actions (internet chahiye), (2) asli phone test: GPay/PhonePe/SMS alert se match, (3) alert text ke naye format mile to PayRules.BAD/GOOD/AMT me add, (4) optional: advance-payment message (Autopilot) ke saath QR bhi bhejna, (5) BIZ setup screen + Places key field.
Rules: add-only, build.yml mat chhuo, fake success claim nahi, GitHub push owner ke kehne par. BizStore ka upi / advance message PayKit se badla nahi jaata.

# NEXT ACCOUNT PROMPT - v7.169 (business automation)
Ab tak: v7.166 checks+bridge cap, v7.167 speed (BRAIN_READ_MS 10 s, stagger 2.5 s), v7.168 cleanup (233 purane docs hata), v7.169 BIZ (BizRules/LeadFinderRules/BizStore/LeadFinder, CHANGELOG top dekho). Compile + 1829 tests OK (sandbox: kotlinc GitHub se, android.jar `git clone --filter=blob:none --sparse Reginer/aosp-android-jar`; raw.githubusercontent blocked).
Baaki: (1) QR image (UI me JS QR ya owner ki apni QR image), (2) ui.html me BIZ setup screen + Places key field (abhi sirf voice/chat commands), (3) payment screenshot owner ko forward + "payment mili" command, (4) HelpGuide topic, (5) asli phone test, GitHub Actions.
Rules: add-only, build.yml mat chhuo, GitHub push owner ke kehne par.

# NEXT ACCOUNT PROMPT - v7.166 (v7.165 + checks)
Is round: run_all_checks chali (150 pass / 20 fail, 11 known-stale). openingVoice bridge ko 400-char cap (MainActivity.kt, 1 line, compile NOT RUN). 8 aur fail pre-existing (v7.165 hata kar bhi same): fx_render, part6_smooth, part20, ui_tier, ui_v37(orb shader), v7152_light_popup, check_p7_motion, native_smooth(flaky timeout).
Baaki kaam: Kotlin compile + GitHub Actions (internet chahiye), phone test + Ultron awaaz (owner), optional voice cache. Neeche v7.165 ka prompt:

# NEXT ACCOUNT PROMPT - v7.165 LIVE OPENING (poori detail)

Project: NOVA Voice Assistant (Kotlin, com.nova.assistant). Owner non-technical, Hinglish. Rules: add-only (koi feature/API/fallback/connector/screen/setting/permission mat hatao), no demo/mock/placeholder code, package + structure same, fake success claim NAHI. Har delivery: full ZIP + simple Hindi status + NEXT_ACCOUNT_PROMPT + latest source ZIP. Owner kuch manually merge/edit nahi karega.

## Owner ne kya manga (is round me)
1. Opening video photo/image jaisi lag rahi thi, "kuch khas nahi" -> LIVE banti hui dikhe, chalti hui.
2. Ultron wala dialogue (quote) wapas dikhe - likhte hue (typing) effects ke saath.
3. Ultron voice me bola bhi jaye (awaaz).
4. Ab agla account: TEST bhi kare + jo baaki hai sab complete kare + full details ke saath pack.

## Ab tak kya hua (v7.165, sirf app/src/main/assets/ui.html + docs)
- Naya <style id="v165-live-css"> + <script id="v165-live-open"> ui.html ke END me (</body> se pehle).
- Live canvas #lv (photo slides #rv ke upar, 3D NOVA #n3 ke neeche): digital rain, scan beam, radar (sweep, blips, NEURAL SCAN %), boot-log typing (BOOT/MIC GUARD/NEURAL LINK/VOICE ENGINE/PERMISSIONS/ALL SYSTEMS), hex stream, ~700 particles jo NOVA title banate hain (slam par burst, finale par explode), glitch slices, voice-bars + caret sparks quote typing ke waqt. Slow frames (>38ms x25) par rain/half particles band (HQ=false); html.lowfx par rain off.
- Photo slides: sirf dim (#rv opacity .30!important) - hati nahi.
- 3D NOVA letters: ab class `hit` par (r>=2700 ms) slam hote hain (CSS `#n3:not(.hit) .n3w span{animation:none;opacity:0}`), pehle 1.1 s par aate the.
- Quote box (#n3q): bada font, glow, sweep line, har naye letter par `.kk` glitch flicker.
- Slow voice: agar VS<0 aur HOLD ke aakhri 250 ms me bhi voice start ki khabar nahi (aur snd on, Android maujood) to HOLD +40 ms/frame, max 2000 ms tak intezaar.
- 2 chhote hook (existing IIFE ke andar, add-only): `window.__nvOpen()` (read-only state: seq, HA, HOLD, VS, VD, QN, QUOTE, snd, addHold) aur startOpen ke end me `window.__nvOnOpen()` call. Wajah: opening ka code ek IIFE hai, global `seq/HOLD/...` bahar nahi dikhte (pehla draft isi wajah se kuch paint nahi karta tha; fix ho gaya, canvas paint verify hua).
- Kotlin me KUCH NAHI BADLA (OpeningVoice.kt, MainActivity.openingVoice bridge, UltronFx waise hi).
- Docs: CHANGELOG.md (top), PART_STATUS_v7_165_LIVE_OPENING.md.

## Test jo hua (sandbox)
- Headless Chromium (swiftshader), FAKE Android bridge (tools/ui_shots.py), 0 JS pageerror.
- Virtual-clock frame-by-frame capture (tools/capture_opening_frames.py): performance.now/rAF/setTimeout/setInterval + document.getAnimations() ko manual step; frames dekhe: quote typing aata hai, CRT line -> reactor -> title; canvas painted pixels > 0 verify (329160 px @ V=2.3 s).
- NOT DONE: (a) poora 10.5 s video ka visual review (capture background me chal raha tha jab pack hua), (b) tools/run_all_checks.sh contract suite, (c) Kotlin compile (tools/local_compile_check.sh needs internet), (d) GitHub Actions, (e) ASLI PHONE, (f) Ultron awaaz suni nahi (sandbox me TTS nahi; preview silent).

## Agle account ka kaam (is order me)
1. `bash tools/run_all_checks.sh` chalao. Naya failure (known-stale 11 ke alawa) aaye to fix (ui.html ke naye ids: lv, v165-live-css, v165-live-open, __nvOpen). Kuch bhi hatao mat.
2. Video dobara banao: `python3 tools/capture_opening_frames.py /tmp/fr 10.5 voice 3 0` (aur `... 3 1`, `... 3 2` parallel, setsid nohup se background; frame ~1-2 s lagta hai) phir `ffmpeg -framerate 30 -i /tmp/fr/f%04d.jpg -c:v libx264 -pix_fmt yuv420p out.mp4`. Frames dekh kar check karo: (a) particles se NOVA ban-na, (b) radar/boot-log dikhe, (c) quote typing + sparks + bars, (d) text title/quote se overlap na kare (390x844, 360x640, 412x800, 320x640 sab size), (e) finale clean.
   Layout dhyan: boot-log y = max(256, 30% H) (H<640: 196); radar centre y = max(52% H, titleCenter+150); quote box bottom 112 px; overlap mile to y-positions adjust.
3. Owner ko voice ka sach batao: Ultron voice sirf phone par chalti hai (OpeningVoice.kt -> TTS -> WAV -> UltronFx -> UltronPlayer). Owner se phone par check karwao: awaaz aayi? quote dikha? Na aaye to ErrLog 'OpeningVoice' / 'MainActivity openingVoice' lines (TTS init 8 s timeout, synth 20 s timeout, `snd` flag nahi to voice band). Agar owner kehta hai quote kabhi dikha hi nahi -> phone par ui.html version verify karo (zip install hua?) aur Settings me reduced-motion/lite check (rm=true par opening skip hoti hai).
4. Kotlin compile + GitHub Actions run karo (internet ho to tools/local_compile_check.sh); fail ho to sach batao.
5. Remaining ideas (owner approve kare to): Ultron voice pre-generate/cache (pehli baar synth, baad me cache wav) taaki dheema phone par bhi turant bole - Kotlin change, add-only; version bump (build.gradle.kts versionName abhi "7.162" stale hai - bump karne se pinned tests stale ho sakte hain, pehle check).
6. Delivery: full ZIP (NOVA_v7_166_*.zip) + link, simple Hindi status (kya hua / kya nahi / kya test hua), is prompt ka next version, latest source ZIP.

## Files
Is zip me: ui.html (changed), CHANGELOG.md, PART_STATUS_v7_165_LIVE_OPENING.md, NEXT_ACCOUNT_PROMPT_v7_165.md, tools/capture_opening_frames.py (naya). Baaki sab v7.164 jaisa.
