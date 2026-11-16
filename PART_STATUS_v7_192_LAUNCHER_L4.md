# PART STATUS v7.192 - LAUNCHER PART L4 (BUSINESS HOME)

Plan: L1 base (v7.189) -> L2 design (v7.190) -> L3 Kaam-Manager (v7.191) -> **L4 business home (ye part)**. Launcher L1-L4 poora.

## Kya bana (add-only, purana kuch nahi hata)
- `BusinessHomeRules.kt` (+ `BusinessHomeRulesTest.kt`, 13 tests): card ke 4 lines (orders, leads, autopilot, aakhri report), voice report, tap -> purana command, "naya order/lead" card ka text, website order parser (WooCommerce + Shopify), honest "website order kaise aate hain" line. Data na ho to hamesha "abhi koi nahi". Customer ka phone / address kahin store nahi hota.
- `BusinessHomeStore.kt`: asli stores se padhta hai - LeadStore (aaj ke leads), AutopilotStore (confirm hue orders + adhoore chat orders), AutopilotStore.cfg (autopilot on/off), LeadFinder.lastReport (restart ke baad bhi 7 din yaad). Website store ke orders har 15 minute mein ek baar (Autopilot.pass ke andar, ek line ka hook) padhta hai: WooCommerce (key+secret+URL) ya Shopify (token+naam), jo AUTOPILOT tab mein pehle se saved hai. READ only. Pehli baar sirf purane orders yaad karta hai (purane orders ka shor nahi).
- `AutopilotNet.kt`: `wooOrdersRaw`, `shopifyOrdersRaw` (sirf GET).
- `LauncherActivity.kt`: "Aaj ka business" card - 4 lines + website-order status line + 2 button (Leads dhoondo, Report sunao). Tap: orders line = "jarvis orders dikhao", leads line = "jarvis leads dikhao", autopilot line = "jarvis autopilot status", report / title = Jarvis bolkar report sunata hai. "Leads dhoondo" = boss ka saved shehar + dhandha (jarvis biz city / category) se "jarvis <shehar> me <dhandha> ke genuine leads dhoondo"; wo set nahi to bata deta hai ki pehle set karo.
- `JarvisService.kt`: `showBusinessCard` (naya lead / naya chat order / naya website order par card kisi bhi khuli app ke upar, "Display over other apps" ek baar; busy / listening ho to card nahi aata), `onStoreOrder`, `speakBusinessReport`. Purani awaaz + notification wali sooch jaisi thi waisi hi hai.
- versionName 7.192. TEST_STATUS mein 4 naye rows.

## HARD DENY (hamesha)
Payment, boss ke paise se order dena, password, OTP, PIN, CVV. Card ke taps sirf padhne / dhoondne wale commands bhejte hain; `TaskManagerRules.isDenied` har bheje gaye vakya par chalta hai.

## Website orders - asli sach
- Jo pehle se tha: customer WhatsApp / Instagram chat mein order deta hai, Jarvis (Autopilot concierge) order leta hai, confirm hone par owner ko batata hai. Ye chal raha hai (phone par test NOT REPORTED).
- Naya (L4): agar website Shopify ya WooCommerce hai aur AUTOPILOT tab mein key saved hai, to uske orders bhi card par aate hain. Shopify token ko `read_orders` permission chahiye. Wix ke orders abhi nahi padhte (nahi hai).
- Jo NAHI hai: skfashions142.lovable.app (Lovable site) se seedha order laane ka koi backend / form code mein nahi hai. Banayi hui sites ka order button WhatsApp / mailto kholta hai, koi server nahi. Aasan step: website ke order button ko boss ke WhatsApp Business number par bhejo, ya Shopify / WooCommerce lagao.

## Honest
- Kotlin COMPILE NAHI hua (is environment mein compiler nahi), unit tests NOT RUN, phone test NOT RUN. Pehle GitHub Actions build chalao; fail ho to sirf laal text bhejo.
- Store-order pull ek baar bhi asli store se nahi chala. API ka rasta public docs se likha hai.
- Card ke numbers sirf is phone ke stores ke hain (leads: LeadStore, orders: Autopilot + store). Dusre phone / laptop ka data nahi.
- Launcher L1-L4 cross-check: manifest mein HOME activity, SYSTEM_ALERT_WINDOW, INTERNET maujood; naye hook add-only; braces / brackets ka static check theek; baaki compile ke baad pata chalega.
