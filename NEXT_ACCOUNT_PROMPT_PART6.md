# NOVA Part 6 — poora brief (naye account ke liye)

Base: `NOVA-WIP-v75-PART5.zip` (SIRF yahi source of truth). Sabse pehle padho: PART5_STATUS.md, PART4_STATUS.md, PART3_STATUS.md, PART2_STATUS.md, PART5_STATUS_HINDI.md.
Owner: Hinglish bolta hai, non-technical, SIRF phone se kaam karta hai (terminal se ZIP GitHub par push karta hai). Reply Hinglish mein, chhote aur saaf.
Har part poora hone par ZIP + agla NEXT_ACCOUNT_PROMPT + chhota Hindi status do (kyunki account ki limit kabhi bhi khatam ho sakti hai). Bich-bich mein baar baar test mat chalao: `bash tools/run_all_checks.sh` sirf sabse aakhir mein EK baar (poora ~3-4 min leta hai, background mein nohup se chalao, polling karo).

## STRICT RULES (kabhi mat todna)
1. Koi feature / screen / setting / API / fallback / permission / command / safety confirmation delete, disable ya replace nahi.
2. Package aur structure wahi rahe (com.nova.assistant, Kotlin + WebView ui.html).
3. No mock / fake success. Jo chala nahi use NOT TESTED likho; PASS tabhi jab sach mein chalaya.
4. API keys logs / reports / status mein kabhi nahi.
5. Bina maujooda HAAN/NAHI confirmation ke call / message / payment nahi.
6. STOP / SILENT (Part 3) aur modern v76/v77 UI layer bachao.
7. Kotlin edit karo to chhote, pattern ke andar, aur unit test ke saath; compile yahan nahi ho sakta (Android SDK, dl.google.com aur Maven Central is environment mein band hain — check kiya, 000). Isliye GitHub Actions ka log hi asli saboot hai.

## Project kya hai
NOVA = Android voice assistant (Hindi/Hinglish). Kotlin service (NovaService.kt ~4000 lines), WebView UI (app/src/main/assets/ui.html ~3300 lines), native floating HUD (NovaHud.kt). Python contract tests tools/test_*.py (plain scripts), JUnit tests app/src/test, `tools/run_all_checks.sh` sab Python scripts chalata hai. `.github/workflows` mein CI hai.

## Ab tak kya ho chuka (Part 2 -> 5)
- Part 2: 15 missing features MADAD list (HelpGuide.TOPICS + ui.html CAPS) + aiFacts mein judi. Part 5 mein ek aur topic "featurestatus" (ab 32 topics, Kotlin = ui.html).
- Part 3: HUD par alag STOP, persistent SILENT ON/OFF (Cfg.silentMode), HAAN/NAHI STOP se alag; SilentRules (ek order ke liye "chup rehna").
- Part 4: modern v76 UI layer (44px targets, focus ring, ARIA, responsive 320-820, SILENT pill, HAAN/NAHI bade).
- Part 5 (ye wala):
  * `tools/ui_screens.py`: Chromium + fake Android bridge se 13 screens (home, featurestatus, chat, chat_haan_nahi, history, help_madad, settings, settings_open, connectors, update_center, skills, limits, sheet) x widths 320/360/412/820 x SILENT ON/OFF, aur checks (sideways scroll, kata text, <44px target, JS error). Options: `--sizes 320x568,360x640 --screens a,b --silent-on|--silent-off --quick`. Har shot ~10s (3D orb software render), isliye 2 process parallel chalao (silent-on aur silent-off).
  * ui.html: duplicate `id="al_on"` suljha: battery switch ab `al_bat` (getAlerts/setAlerts), `al_on` sirf Auto-seekho (getAutoLearn/setAutoLearn, pehle wire nahi tha aur galti se battery chalata tha). Saare `.sm` buttons 44px, Settings ke input/slider 44px, 320px par CONNECT tab ka label (`#tabs .tb em`) theek.
  * NovaHud.kt (sirf visual): STOP/SILENT text 9->11, rects bade (STOP margin+2..62, SILENT margin+66..146, y top+10..38) aur touch hit-areas barabar kiye; HAAN/NAHI 36->44dp, text 16, askExtra 62dp; hint rang saaf.
  * FeatureStatus.kt: 6 nayi read-only rows (lockguard, autoreply, batteryalert, offlinepack, apilimits, routines).
  * FeatureVoice.kt (naya) + NovaService.kt mein ek chhota block (HelpGuide ke baad, SourceMap se pehle): bolkar SIRF STATUS poochna ("auto reply ka status batao", "lock guard on hai ya off", "api limit kitni bachi"...). Order words ("on karo/band karo/delete karo/call...") nahi pakadta.
  * Tests: tools/test_part5_ui_screens.py (static + Chromium, Playwright na ho to SKIP), tools/test_part5_feature_voice_contract.py, app/src/test/.../FeatureVoiceTest.kt (4 tests).
- Aakhri check: `run_all_checks.sh` -> 63 passed, 0 failed.

## NOT TESTED (jhooth nahi): ye sab Part 6 ka pehla kaam hai
- Gradle build, JUnit, Kotlin compile. Naya/badla Kotlin: NovaHud.kt, FeatureStatus.kt, FeatureVoice.kt, NovaService.kt (FeatureVoice block, ~line 1996), HelpGuide.kt (naya Topic "featurestatus"), FeatureVoiceTest.kt. Shak ki jagah: Kotlin `if/else` multiline `when` branches (FeatureVoice.reply), `State` class naam, `HelpGuide.Topic(...)` ke saath comma.
- Asli phone: mic, wake word, TTS, HUD ka dikhna (STOP/SILENT rect orb/title se takrate to nahi), HAAN/NAHI tap, TalkBack, call/WhatsApp.

## KAAM 1 (pehle): BUILD theek karo
1. Owner ZIP GitHub par push karega. Agar Actions ka log mile to padho, saare compile errors fix karo (sirf zaroori badlav). SDK mile to khud `./gradlew test assembleDebug`.
2. JUnit fail ho to test ya code sahi karo (test kamzor karke pass mat banao).
3. Done = build log PASS ya (log na mile to) saaf likho "NOT TESTED".

## KAAM 2: "5-SECOND RULE" (owner ki sabse badi maang)
Maang: NOVA har kaam online aur offline KHUD samjhe; na samjhe to AI fallback; sab ~5 second mein; command shuru ho aur SAHI ho.
Abhi code mein kya hai (Part 5 mein padha):
- Layers: LocalSkills / HelpGuide / SourceMap / FeatureVoice (turant) -> CommandRouter + IntentParser + Logic.classify -> brain.resolve (shortcuts + fuzzy) -> offline model (LocalBrainRules.TIMEOUT_MS=8000, LocalChat.TIMEOUT_MS=8000) -> cloud AI (ProviderChain.run in FreeProviders.kt; runFreePhoneAction budgetMs=9000, default 14000).
- Kami: `budgetMs` sirf do providers ke BEECH check hota hai; har HTTP call FreeHttp mein CONNECT 6s + READ 10s tak ruk sakti hai; providers sequential hain. Worst case 15s+.
Kya banana hai:
 a) Naya pure-Kotlin `UnderstandBudget.kt` (+ unit tests): total understanding budget ~4.5s; local rules <0.3s; offline model sirf command samajhne ke liye <=2.5s; cloud AI ke liye bacha hua time.
 b) Cloud AI: top 2 providers ek saath (hedged/parallel), pehla VALID jawab jeete; har call ka hard timeout = bacha hua budget. FreeHttp mein optional per-call timeout parameter (default purana hi rahe).
 c) Cloud jawab sirf tab chale jab PhoneActionRules.parseAll validate kare aur wahi runTool path use ho. Kam confidence = ek chhota clarification sawaal; andaza lagakar kabhi nahi chalana.
 d) Call / message / payment par maujooda spoken HAAN/NAHI confirmation NAHI hatana; confirmation ka wait 5s mein nahi ginna.
 e) 4.5s mein kuch na mile to saaf bolo "samajh nahi aaya, dobara bolo" — jhootha success nahi.
 f) Har layer ka latency (ms), sirf naam + ginti (bina text/keys), TEST REPORT / FeatureStatus mein dikhao taaki asli phone par 5s check ho.
 g) Fallback ka koi step hatana nahi; sirf time-bound karna.
Acceptance: unit tests (budget policy, hedged winner, timeout), run_all_checks PASS, aur status mein likho ki asli phone par latency NOT TESTED jab tak owner na maape.

## KAAM 3: Graphics "sharp / 3D / bina atke"
Code mein pehle se hai: tier-based DPR cap (high 3x, mid 2x, low 1.5x) `window.__dprCap`, 3D orb WebGL2 (resolution ladder SC=[1,.75,.5], 60/30fps cap, lowfx), quality governor (glow auto-off). Zabardasti sab phones par 4K MAT karo (hang hoga). Owner ke phone ka model/RAM poochho. Phir ek "Quality: Auto / Sharp / Smooth" option jodo (default Auto, kuch purana na hate), aur asli phone par check ke baad hi "smooth" ka dawa karo.

## KAAM 4 (optional)
- Bolkar ON/OFF karna (sirf status nahi) abhi JAANBUJHKAR nahi hai; jodna ho to sirf maujooda confirmation ke saath.
- ui.html badle to: `python3 tools/ui_screens.py <out> --sizes 360x640 --screens settings_open,connectors --silent-off` (har shot ~10s).

## Aakhir mein (har part ke baad)
- PART6_STATUS.md (Hinglish, honest PASS / FAIL / NOT TESTED), chhota Hindi status, NOVA-WIP-v75-PART6.zip, NEXT_ACCOUNT_PROMPT_PART7.md.
- Owner ko saaf batao: kya ban gaya, kya NOT TESTED, aur agla kaam.
