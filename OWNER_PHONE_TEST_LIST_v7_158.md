# OWNER PHONE TEST LIST — v7.158 AUTOPILOT (FINAL, Parts 1-4)

Pehle: naya APK install karo (GitHub Actions se). Ye list SIRF v7.158 ke naye features (AUTOPILOT)
ki hai — purane features ke liye OWNER_PHONE_TEST_LIST_v7_154.md (deep voice / barge-in / wifi) wahi
chalega. Har item par PASS / FAIL likho. FAIL aaye to: kya hua, kya hona chahiye tha, aur
Settings > error log COPY — teeno bhejo.

## A. SETUP WIZARD (Part 3) — pehli baar

1. App kholo → pehli baar AUTOPILOT SETUP wizard khud khulna chahiye (ek baar, ~2.5 second baad).
   Nahi khula to AUTOPILOT tab > "nova autopilot setup karo" bolo → wizard khule.
2. STEP by STEP: PHOTOS (ALLOW; Android 14 par "Allow all" chuno) → NOTIFICATION (NOVA ko ON karo)
   → ACCESSIBILITY (NOVA ON) → BATTERY (No restrictions; Xiaomi/Oppo/Vivo par AutoStart bhi) →
   GROUP (list me se apna WhatsApp Business group TAP karo ya naam likho) → TOKENS → TEST → READY.
   Har step par WAPAS button chale? "baad me" wale steps skip ho rahe hain?
3. READY screen par "AUTOPILOT ON KARO" dabao → AUTOPILOT tab khule, HUD strip me SYSTEM ON dikhe.

## B. AUTOPILOT TAB + HUD (Part 1 + Part 4)

4. Home par AUTOPILOT card kholo → tab khule. Sabse upar HUD strip: NOVA AUTOPILOT + radar ring +
   SYSTEM ON/OFF chip + live chips (QUEUE / IG / STORY / YT / KEYS / QUOTA). Chips me aaj ke asli
   number dikh rahe hain? (IG 0/6 jaisa)
5. GRAPHICS QUALITY SMOOTH (default) par: tab smooth khulta hai, koi atakna nahi, animation nahi
   (static hologram - corner brackets + grid). SHARP karke app restart karo: tab khulte waqt cards
   ek-ek karke fade-in hote hain + radar ring ghoomti hai. Dono me scroll smooth hai?
6. STATUS card: ON KARO / OFF KARO / ABHI SCAN + POST — teeno kaam karte hain? Armed line me
   wajah dikhti hai (jaise "token nahi hai")?
7. SAB THEEK HAI? card: CHECK KARO dabao → 10 checks ki list aaye; jo laal hai usme FIX button
   dikhe aur dabane par sahi Settings screen khule? (Accessibility OFF rakh ke ek baar try karo)
8. TEST BUTTONS: GITHUB TEST (repo asli padha jaye), INSTAGRAM TEST (token + id verify), YOUTUBE
   TEST (login + quota). Sirf token hai to hi THEEK dikhega — warna wajah batayega.

## C. GROUP MEDIA → POST (Part 1 + 2) — ASLI KAAM

9. Apne WhatsApp Business group me 2-3 photos bhejo (ek saath, 90 second ke andar) → AUTOPILOT tab
   ke QUEUE me ek BATCH (ek caption pack) aana chahiye → website (gallery) par post + Instagram par
   EK CAROUSEL. Website link kholo kar photo dikh rahi hai?
10. Ek video bhejo (1 minute se chhota, seedha phone se) → IG REEL + YT SHORT dono jaane chahiye.
    Lambi video (>3 min) sirf YouTube par jaani chahiye (Shorts nahi banta — honest message aata hai).
11. Galat format wala video (har 30 second me ek bhejo, jaise screen recording codec alag ho) →
    sirf CHARGING + WiFi par re-encode hona chahiye. Battery par nahi (message me wait bole).
12. Horizontal video reel/Short ke liye 9:16 blurred background lagta hai? (video ke piche blur,
    upar sharp video)
13. BEST TIME ON hai to post slot par jaata hai (9:00/12:30/15:30/18:30/20:30/22:00 IST), OFF par
    turant. Queue me runAt time dikhta hai?
14. Aap KHUD bheji photo: PEHLE PUCHO (ASK) mode me REVIEW job banta hai ("aap ne bheja hai - haan
    karo"). AUTO mode me chat ka naam check karke khud jaata hai. OFF me kabhi nahi.
15. DObara wahi photo bhejo (same file) → duplicate post NAHI hona chahiye (file-hash de-dupe).
16. Share → NOVA (MEDIA MILA card) → POST TO AUTOPILOT button (sirf ON hone par dikhta hai) →
    same queue me jaata hai?

## D. CONCIERGE — CUSTOMER DM (Part 1 + 3)

17. Doosre phone se (customer) apne business DM/WhatsApp par poochho: "discount milega?" → jawab
    me LIVE website offer ka zikr aaye (jo AAJ KA OFFER box me likha hai). Website par bhi wahi
    offer dikhe — dono match karo.
18. Customer order de: "2 red saree, naam Rahul, 9876543210, address..." → step by step poochta
    hai → CONFIRM par ORDERS list me aaye + aapko voice/notification mile.
19. Aap bolo: "nova order Rahul dispatched 15 tak" → save ho. Ab customer poochhe "mera order kab
    aayega" → wahi status bolega (jo aapne bola). Kuch store na ho to honest line + aapko alert.
    (Delivery date kabhi khud nahi banani chahiye.)
20. Business lead ("wholesale rate", "bulk", "collab") → LEAD me save + aapko ek baar bataye.
21. Group me koi customer-sawal pooche → NOVA group me JAWAB NAHI degi (rule: groups kabhi nahi).
    Aap khud reply kar do to AI side me ho jaye.

## E. VOICE COMMANDS (Part 3) — JARVIS tone

22. "nova autopilot on karo" / "off karo" / "status batao" → Sir wala honest jawab.
23. "nova aaj kya post hua" → aaj ke asli post bole (na ho to "kuch nahi hua").
24. "nova orders dikhao" → orders ki list bole.
25. "nova offer 20% off code SK20" → website par offer lag jaye (AUTOPILOT tab + website dono check).
26. "nova isko abhi post karo" → turant scan + queue + pump.
27. "nova story lagao" → sabse naya photo story queue me.
28. "nova autopilot setup karo" → wizard khule.
29. Morning brief: subah (8-11) pehli baar → "Sir, subah ki report: kal X post hue..." bole.
    (Yeh test karne ke liye agle din tak ON rakhna padega.)

## F. TOKENS + GUIDES (Part 2 + 3)

30. GUIDE cards kholo (GITHUB TOKEN / INSTAGRAM TOKEN / YOUTUBE LOGIN / SHOP+OFFER) — step-by-step
    Hinglish steps dikhte hain, ek-ek khulta hai?
31. YouTube device login: CLIENT ID + SECRET save → YOUTUBE TEST → "LOGIN CHAHIYE" → CODE MANGO →
    code dikhe + google.com/device khule → CODE COPY KARO → wahan type karke login → CHECK KARO →
    refresh token save. YOUTUBE TEST ab quota bataye?
32. Instagram token save karke 60-din counter chale; 5 din pehle NOVA khud refresh try kare (fail
    ho to aapko bole). (5 din ka test abhi possible nahi — sirf save + TEST chale.)
33. SITE TYPE dropdown: SHOPIFY / WOO / WIX fields + token boxes dikhte hain? (use karne ka test
    sirf unhi ke liye jo wo store use karte hain)
34. Token kabhi wapas NAHI dikhti (sirf SAVED chip) — kisi bhi screen par token text nahi dikhna
    chahiye. AUTOPILOT > TOKENS line me SAVED/nahi likha hai?

## G. SELF-KNOWLEDGE (Part 4)

35. Chat me "autopilot kya hai" / "tum kya kar sakte ho" poochho → jawab me AUTOPILOT ka zikr aaye
    (local jawab, bina AI key ke bhi). MADAD kholo → list me "Autopilot (group se website +
    Instagram + YouTube)" topic dikhe + tap karne par example chale?
36. "nova autopilot status batao" bina wizard ke bhi chale. Home par KYA CHALTA HAI box me
    "Autopilot (group se post + DM concierge)" row dikhe (OFF hai to OFF + wajah)?

## H. REGRESSION (purana kaam — 2 minute)

37. Wake word "nova", torch on/off, "youtube kholo", WhatsApp confirm flow, "nova wifi scan karo",
    deep voice on/off, STOP button bolte waqt, SILENT mode — sab pehle jaise?
38. Phone restart → NOVA notification → ACTIVATE → AUTOPILOT queue resume (jo pending thi) +
    duplicate post NAHI (idempotency). Reboot ke baad 15-min worker phir se chalu ho?
39. 10 minute chhod do (screen off) → AUTOPILOT ON hi rehna chahiye (battery optimization OFF hai
    to). Poster band? — battery settings dobara check.

## Notes

- Honest limits: Instagram/YouTube endpoints public docs se likhe gaye hain — pehli live post par
  hi pata chalega ki sab theek hai. FAIL par error log ka COPY zaroor bhejo.
- YouTube: unaudited Google project ki wajah se upload PRIVATE ho sakta hai — NOVA saaf bolegi
  "Google ne PRIVATE kiya" + audit steps. Ye bug nahi hai.
- Sab kuch OFF karke chhodo to app v7.154 jaisa hi rehna chahiye (zero-regression rule).
