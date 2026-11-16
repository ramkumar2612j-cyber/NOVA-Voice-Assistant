# PART STATUS v7.191 - LAUNCHER PART L3 (KAAM-MANAGER)

Plan: L1 base (v7.189) -> L2 design (v7.190) -> **L3 Kaam-Manager (ye part)** -> L4 business home (leads + orders).

## Kya bana (add-only)
- `TaskManagerRules.kt` (+ `TaskManagerRulesTest.kt`): 17 entries - website, publish, leads, orders/biz status, offer, autopilot, call agent, instagram, story, screen, video/movie, camera, photo, app banana, seekhna, kai apps kholna, laptop, aur lamba-agent. Har entry: kya karta hai, KAB use karna hai, device (phone/laptop/business/web), example (wahi style jo uska local command samajhta hai).
- `JarvisService.kt` (chhota hook, brain wale jagah): jis vakya ko koi local command nahi mila, usko JarvisBrain ab **tool-list ke saath** samajhta hai aur `agent` (sahi tool) + `say` (boss ka vakya us tool ki style mein) deta hai. Service `say` ko normal local command path (`Logic.classify -> runCommand`) se chalata hai - **koi extra "kya main karu?" nahi**. Samajh na aaye / classify na kare to purana rasta bilkul pehle jaisa.
- "Jarvis tum kya kya kar sakte ho?" - registry se turant jawab (AI call nahi), Phone / Laptop / Business / Web ke hisaab se.
- Hard deny: paisa/bank/password/OTP/PIN/CVV - boss ke vakya ya AI ke `say` mein ho to saaf mana (Plan.denied). Pehle wali AgentRules deny list bhi waisi hi chalu.
- `TaskManagerStore.kt`: aakhri kaam yaad rakhta hai; launcher home par "Aakhri kaam: ..." dikhta hai (24 ghante).
- versionName 7.191.

## Honest
- Kotlin COMPILE NAHI hua, phone par test NOT RUN. Pehle GitHub Actions build chalao.
- Ye AI par tikka hai: AI key / free chain chalu ho tabhi (agentChainReady). Bina AI ke sirf purane local commands + directory jawab chalenge.
- Calls / WhatsApp wale commands ka apna spoken confirm (pichle owner decision se) abhi bhi hai, kyunki wo unke apne command ke andar hai; Manager khud koi naya sawal nahi puchta.
- "Aakhri kaam" sirf Manager se shuru hue kaam dikhata hai; kaam ka live progress (chal raha agent) abhi launcher par nahi.
- Ye jaanch nahi hui ki har example sahi Cmd deta hai (Logic.classify yahan chal nahi sakta). Phone par 5-6 vakya bol kar dekhna: leads, website, orders, laptop, call.

## L4 (agla): business home
Leads aur website orders launcher home par ("Aaj ke leads / orders"), voice report, ek-tap se kaam shuru.
