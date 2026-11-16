# PART STATUS v7.190 - LAUNCHER PART L2 (image jaisa neon design + live status)

Plan: L1 base (done v7.189) -> **L2 design + live status (ye part)** -> L3 KAAM-MANAGER -> L4 business (leads + orders home par).

## L2 mein kya bana (LauncherActivity.kt dobara likha, LauncherRules.kt mein add)
- **Home page**: badi ghadi + tareekh + greeting; **mausam card** (Open-Meteo, free, bina key). Jagah: phone ki last-known location (agar location permission pehle se mili ho) warna shehar jo owner card tap karke ek baar likhta hai. 20 min cache.
- **System Status**: Battery / RAM / Storage live bars (har 5 sec), rang: hara / peela / laal.
- **Search / command box**: app ka naam = app khule; poora vakya = seedha Jarvis (`typed`). Naam likhte hi apps wala page khulta hai.
- **Dock**: YouTube, Google, Play Store, Instagram, WhatsApp (jo installed ho) + More.
- **Quick Actions**: Camera, Call, Message, WiFi, Dark mode*, Torch, Jarvis chat, Sab apps.
- **Laptop Control card**: jura hai ya nahi (LaptopLink code se) + "Laptop par kaam bolo" (box mein "laptop par " bhar deta hai).
- **JARVIS orb (neeche)**: band ho to service chalu; chalu ho to **kisi bhi app ke upar Jarvis ka card (JarvisHud popup)** dikhata hai. "Display over other apps" band ho to Android ki ek baar wali screen kholta hai.
- **Sab apps page**: A-Z grid, back = home.
- Tests: `LauncherRulesTest` mein mausam labels, weather/geo parse, percent, bar color jode.

## Honest
- *Dark mode / WiFi*: Android app ko chupke se on/off nahi karne deta; sirf system ki screen kholte hain.
- Laptop card mein "Mirror / Share files / Gaming" nahi bana kyunki laptop app mein ye hain hi nahi (jhooth nahi likha). Sirf link status + kaam bolna.
- Kotlin COMPILE NAHI hua, phone par test NOT RUN. Pehle GitHub Actions build chalao.
- Weather: shehar set na ho aur location permission na ho to "Shehar set karo (tap)" dikhega.
- Jarvis ka card hamesha sirf tab aata hai jab "Display over other apps" ek baar ON ho (Android ka niyam).
- Payment / OTP / password / PIN / CVV wale kaam hamesha band.

## Agla: L3 KAAM-MANAGER
Sab agents/automations ki ek list (naam, kya karta hai, kab use karna hai, keywords), aur Jarvis boss ka vakya sun kar sahi tool khud chune (JarvisBrain ke saath). Owner ko kuch jaanna na pade.
