# NOVA v7.72 - OWNER PHONE TEST LIST (CHECKPOINT 4)

Ye list sirf phone par chalane ke liye hai. Ab tak ka sab test sandbox me hua hai, **phone par kuch bhi TEST NAHI hua** (GitHub Actions bhi NOT RUN). Isliye har item ka jawab sirf **PASS** ya **FAIL** likho. FAIL ho to sirf wo line + screenshot bhejo, baaki mat bhejo.

Tarika: pehle APK banao (`OWNER_SETUP_AND_APK_STEPS.md`, section G), install karo, phir neeche se ek-ek karo. Ek item ek baar.

---
## 0. Pehle ye 3 (agar ye FAIL to aage mat jao)
- [ ] 0.1 App khule, crash na ho. Home par orb dikhe.
- [ ] 0.2 Settings ya HOME ka "SETUP BAAKI HAI" card: Microphone, Notifications, Accessibility, Write settings, alarm permission ALLOW karo.
- [ ] 0.3 ACTIVATE dabao, bolo "nova torch jala do" -> torch jale. "nova torch band karo" -> band.

## 1. CHECKPOINT 1 (Parts 5-7: naya look, home, motion)
- [ ] 1.1 Boot / intro screen neon-glass dikhe, app atke nahi.
- [ ] 1.2 Home par orb ke neeche 5 chips + 4 dock buttons dikhein aur touch par kaam karein.
- [ ] 1.3 Internet band karo -> orb amber "NO INTERNET" dikhe. Wapas on karo -> normal.
- [ ] 1.4 Screens badalte waqt slide / ripple chale; HAPTIC button dabane par halka vibration; numbers ticker ki tarah badlein.
- [ ] 1.5 Floating chat card (HUD) dikhe aur aankh ko theek lage.
- [ ] 1.6 STOP dabao / bolo -> NOVA sirf bolna roke, NOVA ON rahe (Part 4 SAFE STOP).

## 2. CHECKPOINT 2 (Parts 8-10: chat, persona, ghuma ke kaam)
- [ ] 2.1 Chat me message bhejo: bubbles aur typing reveal theek dikhe, text screen se bahar na jaye.
- [ ] 2.2 "nova kaise ho" -> chhota, mazaakiya jawab (persona ON).
- [ ] 2.3 "mummy ko call karo" -> NOVA poochhe -> bolo **"ha kr do"** -> call lagni chahiye (pehle cancel ho jaata tha).
- [ ] 2.4 "torch jala do", phir "ulta kar do" -> torch band. Phir "wahi kar do" -> torch phir jale.
- [ ] 2.5 "andhera hai" -> torch jale.
- [ ] 2.6 "usi ko call karo" -> NOVA call NA kare, poochhe "Kisko?".
- [ ] 2.7 Settings -> "Background noise cancellation" switch dabao -> chale.
- [ ] 2.8 MADAD kholo -> "Jarvis andaaz" aur "Ghuma ke bole kaam samajhna" dikhe.

## 3. Parts 11-13 (media, frames, analysis)
- [ ] 3.1 Kisi app se video / photo NOVA ko SHARE karo -> "MEDIA MILA" card dikhe.
- [ ] 3.2 ANALYSE dabao -> report + sources aaye (key ho to). Bina key: saaf "key nahi" jaisa honest line.
- [ ] 3.3 Bolo "nova ye video analyse karo" -> wahi kaam.
- [ ] 3.4 Internet band karke ANALYSE -> honest "file ki size" jaisi report, jhooth nahi.
- [ ] 3.5 "is photo me kaun hai" / "ye kaun hai" -> NOVA **mana** kare (chehre se naam nahi batata).
- [ ] 3.6 Chat me photo jodo + "ye kya hai?" -> HAAN/NAHI box aaye. HAAN -> jawab chat me. NAHI -> kuch na jaye.
- [ ] 3.7 Analysis ke beech STOP bolo -> turant ruke.

## 4. CHECKPOINT 3 (Parts 14-15: laser scan + cards)
- [ ] 4.1 Analysis ke time green laser scan overlay upar se neeche chale, touch-through na ho, STOP par turant band.
- [ ] 4.2 Instagram reel chalao, bolo "nova ye kon si movie hai" -> scan -> naam + bharosa + sources. Na mile to honest "pakka nahi / screen padh nahi paya".
- [ ] 4.3 Report card: summary, sources (tap par browser), confidence, "kya verify nahi hua", COPY, SHARE, CLOSE.
- [ ] 4.4 "poora padho" -> poora report bole. Silent mode me chup rahe.
- [ ] 4.5 History me report dikhe; ek delete karo; sab delete karo.

## 5. NAYA (Parts 16-18) - ye sabse zaroori, abhi tak kisi ne phone par nahi dekha
### Part 16 (call uthao)
- [ ] 5.1 App update ke baad permission round me "Phone calls answer" wali permission ek baar aaye -> ALLOW.
- [ ] 5.2 Kisi doosre phone se call karwao -> bolo "nova call uthao" -> call uthe. Phir dobara call karwao -> "call kaato" -> call kate.
- [ ] 5.3 SAARE FEATURES / status me "Call uthana / kaatna" row OK dikhe.
- [ ] 5.4 "kya tum call utha sakte ho" -> naya MADAD jawab aaye.
### Part 17 (SWITCHES card)
- [ ] 5.5 Settings -> SWITCHES card dikhe (SAVE row ke upar).
- [ ] 5.6 GRAPHICS QUALITY badlo (AUTO / SHARP / SMOOTH), app band-khol -> asar dikhe.
- [ ] 5.7 PERSONA OFF -> "nova kaise ho" -> seedha, bina mazaak ke jawab. PERSONA ON -> wapas mazaakiya.
- [ ] 5.8 STOP KAISE KAAM KARE = "SIRF ROKE" -> floating STOP dabao -> NOVA chalu rahe. "ROKE + NOVA BAND" -> STOP par NOVA band ho.
### Part 18 (safe mode)
- [ ] 5.9 Normal use me upar "SAFE MODE" banner **NAHI** dikhna chahiye (ye zaroori).
- [ ] 5.10 (Optional, mushkil) Safe mode dekhne ke liye 10 min me 2 crash chahiye - normal use me nahi hoga, isliye ise **skip** kar sakte ho. Agar kabhi banner aaye: "SAFE MODE BAND KARO" dabao -> banner hate, call / message / data par asar na pade.

## 6. Final (Part 20)
- [ ] 6.1 Ek lamba use (10-15 min): wake word, 3-4 commands, chat, ek analysis. App hang na ho.
- [ ] 6.2 Phone lock karke wake word / commands (locked par kaam ya mana - jo bhi ho, bas bata do kya hua; ye owner faisle wala hissa hai).

---
## Owner faisle jo abhi PENDING hain (bina aapse poochhe code nahi badla)
1. **Voice command jab NOVA busy ho**: abhi queue me nahi jaata (typed jaata hai). Wajah: mic loop bolte waqt apni awaaz ka echo rokne ke liye audio fenkta hai. Queue banana ho to compile + phone test chahiye. **Aapko batana hai: queue chahiye ya nahi?**
2. **wb_ll (locked phone par wake word)**: Lock Guard se takrata hai. **Aapko batana hai: locked phone par wake word chahiye ya nahi?**

## Result kaise bhejna hai
Sirf aisa: `0.1 PASS, 0.2 PASS, ... 5.2 FAIL (call nahi uthi)` + FAIL ka screenshot. Bas.
