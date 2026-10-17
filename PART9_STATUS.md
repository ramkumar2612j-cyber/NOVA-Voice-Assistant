# NOVA Part 9 Status

Base: NOVA-WIP-v75-PART8.zip. Kuch delete/disable/replace nahi kiya. HAAN/NAHI confirmation koi nahi hata (askFirst jaisa tha waisa hai).
run_all_checks: 66 passed, 0 failed (Python contract checks, ek naya: tools/test_part9_phone_tools_contract.py). Kotlin COMPILE NAHI hua, JUnit CHALA NAHI (sandbox mein internet aur kotlinc nahi). Part 7 + Part 8 + Part 9 ka Kotlin teeno abhi tak bina compile ke hain.

## Is part mein kya hua
1. Part 7/8 ka naya Kotlin haath se review kiya (SmartReplyRules, SiteQuality, SiteGen polish block, NovaNotificationService smartAutoReply/bizSmart/handleBiz, NovaService smartReplyVoice/autoReplyVoice/smartDraft). Har use hone wala symbol/signature dhoondh kar match kiya. Koi compile error nahi mila. Ye PADHNE ka result hai, compile ka nahi.
2. Voice se mobile ke 3 naye kaam (AI action path: PhoneActionRules -> NovaService.runTool):
   - global_action "screenshot": Android ka apna screenshot action (Accessibility, Android 9+), gallery mein jata hai. Local voice command pehle se tha, ab AI path mein bhi.
   - ringer(mode normal/vibrate/silent): AudioManager se, baad mein wapas padh kar verify. "silent" ke liye Do Not Disturb access chahiye; na ho to NOVA saaf bolta hai aur kuch badalta nahi (vibrate suggest karta hai).
   - rotation(mode auto_on/auto_off/portrait/landscape): Settings.System se, WRITE_SETTINGS chahiye (wahi permission jo brightness ke liye, "ALLOW BRIGHTNESS"), baad mein wapas padh kar verify.
   Har tool: PhoneActionRules schema + required + validation + prompt allow-list, NovaService.runTool + ringerTool/rotationTool, cloud declarations, ToolVerify level (VERIFIED), 3 naye JUnit test (PhoneActionRulesTest), Python contract.
3. WiFi / Bluetooth / mobile data ON-OFF: Android apps ko allow nahi karta, isliye NOVA sirf settings page kholta hai (pehle jaisa). Jhooth nahi.

## NOT DONE
- Kotlin compile + JUnit (Part 7, 8, 9): pehle yahi karo. GitHub Actions "Build and Test APK" ka log chahiye.
- Website section-by-section generation: JAANBOOJH KAR nahi kiya. Total budget 170 sec hai aur polish 100 sec ke baad shuru nahi hota, section-by-section ke liye jagah nahi bachti. SiteQuality ki shartein asli AI output par abhi tune nahi hui; asli site banwa kar dekho, agar har site "quality baaki" bole to tab faisla karo.
- Naye ringer/rotation ke liye bolne ke local (bina AI) shortcuts nahi bane: ye abhi AI chain ON hone par hi chalte hain (PhoneActionRules ke zariye). Help/CAPS text mein bhi nahi joda.
- Asli phone par kuch test nahi: 3D CORE orb, ACTIVATE, AI SMART jawab, Business Watch smart reply, naam, voice ON/OFF, website polish, aur ab screenshot/ringer/rotation.

## Jaanne layak
- ringer silent par Android 7+ mein Do Not Disturb access na ho to SecurityException aata hai; code use pakadta hai aur bata deta hai.
- rotation portrait/landscape ke liye auto-rotate band ho jata hai (lock). Wapas "auto rotate on" bolo.

## Part 9B (owner ka naya order): AI SMART ab paise/rate/link par rukta nahi
- Hata diya: paise / rate / link / number wali blocking (`SmartReplyRules.unsafeReason` ab sirf secrets pakadta hai: OTP / password / PIN / CVV shabd aur card jaisa lamba number; wahan wait-wali line jati hai aur owner ko bataya jata hai). Auto-reply ke purane skip rules (group, code/OTP wale message, bina Reply button) jaise the waise hain.
- AI ab poori chat padh kar wahi jawab deta hai jo saamne wale ne poochha, repeat nahi karta, paise/order/payment ki baat bhi karta hai.
- Jo fact AI ko pata nahi (rate, stock, date, address, UPI, link, number) wo apni taraf se NAHI ghadta. Pata na ho to "main pooch kar batata hoon" + [OWNER]. Owner ko HAR chat ka notice jata hai (jawab bhi jata hai, notice bhi); [OWNER] ya paise/order ke shabd par notice mein "zaruri" likha aata hai aur batata hai kaise jawab dena hai.
- Owner jawab bolta hai: "Rahul ko WhatsApp karo rate 500 hai" (HAAN/NAHI confirmation ke saath, pehle jaisa). Bheja gaya message us chat ka FACT ban jata hai (`SmartReplyRules.convo.addFact`, max 6, sirf memory mein) aur AI aage uhi fact se baat karta hai.
- Limit: fact tabhi milta hai jab contact ka naam notification ke naam se milta ho (jaise "Rahul"); agar WhatsApp par naam alag dikhta hai (jaise "Rahul Sharma") to AI ko fact nahi milega. Duplicate contact chunne wale raste (executeChosenWhatsApp) se bheje message fact nahi banate. Ye NOT TESTED hai.
- Tests: SmartReplyRulesTest mein onlySecretsAreHeldBack + ownerFactsReachThePromptAndAreLimited (CHALE NAHI). Python contract badhaya (PASS). Help/UI/voice-confirm text se "paise nahi" wali line hata di.
- Risk jo owner ko pata hona chahiye: AI ab bina rokte paise ki baat karta hai. Wo sirf owner ke diye facts bol sakta hai, par kisi galat/dhokhebaaz chat mein bhi khud jawab de dega. Isliye har chat ka notice zaroor dekhte raho.

## Part 9C: UI / help / skills mein bhi joda
- Bina AI ke bolkar chalte hain (Logic.kt, Python mirror tools/classify_mirror.py mein bhi): "vibrate par karo", "ringer normal", "ringer silent", "auto rotate on/off", "screen portrait / landscape lock". Bare "silent" shabd jaan-boojh kar nahi liya (wo NOVA ka Silent mode aur volume mute hai). "vibrate off" jaise sandigdh vaakya kuch nahi badalte.
- runLocalTool mein ringer/rotation, Brain.SAFE_KINDS mein ringer/rotation (skills aur seekhe shortcuts use kar sakte hain), nayi starter skill "pocket mode" (vibrate + brightness minimum). Purani skills nahi chhedi.
- MADAD/Help "phone" topic (HelpGuide.kt + ui.html CAPS) ka text naye kaam aur Android ki hadein batata hai (id/title/example wahi, help contract PASS).
- Mirror par jaanch: 6 naye vaakya sahi nikle (Python). Kotlin LogicTest.part9bRingerAndRotationByVoice likha, CHALA NAHI.
- Skills ke liye "pichle" skill-pack tests chalte rahe: 66 checks PASS.

## Kya NOVA SAB kuch kar sakta hai? (honest jawab)
Nahi. Permission dene ke baad bhi ye Android ki hadein hain: WiFi/Bluetooth/mobile data/hotspot/airplane seedha on-off nahi (sirf settings page), phone restart/switch off/factory reset nahi, bank/payment/password screen par kaam nahi, call/WhatsApp bhejne se pehle haan/nahi. Camera shutter/record button NOVA nahi dabata. Silent ringer ke liye Do Not Disturb access, rotation/brightness ke liye ALLOW BRIGHTNESS, screenshot/tap/type/lock ke liye Accessibility zaroori hai.
