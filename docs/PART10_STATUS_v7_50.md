# NOVA v7.50 : PART 10 ROUNDABOUT COMMANDS - status (simple Hindi, honest)

## Kya juda (add-only, kuch hataya nahi)
1. **"ha kr do" bug theek.** Pehle NOVA "ha kr do" ko samajh nahi paata tha aur confirmation CANCEL kar deta tha. Ab ye sab "haan" maane jaate hain: ha kr do, haan kar do, haan karo, kar do, theek hai, bilkul, ok chalo, zaroor. "nahi / mat / ruko" hamesha jeette hain ("mat kar do" = na). Bina saaf haan ke kuch approve nahi hota: "yes please", "sabko bhej do abhi", sirf "do", sirf "karo" = unclear (pehle jaisa).
2. **Chhote follow-up (sirf aise kaam jo nuksaan nahi karte, aur ulte ho sakte hain):** "wahi kar do", "usi ko dobara", "dobara karo", "ek baar aur" = pichla kaam phir. "ulta kar do" / "undo" = torch on<->off, volume up<->down, media play<->pause, quiet / silent on<->off. Call, SMS, WhatsApp, delete, payment, publish, record kabhi context se dobara nahi chalte (ContextMemory ka purana guard waisa hi).
3. **Ishare se samajhna (bina AI, offline bhi):** "andhera hai" = torch on. "awaaz nahi aa rahi" = volume up. "awaaz bahut tez hai" = volume down. "aankhen chub rahi hain" = brightness kam. "screen bahut dim hai" = brightness zyada. Sirf chhote (8 shabd tak) poore vaakya par; lambi baat-cheet se kuch nahi badalta.
4. **Risky kaam sirf ishare se = kabhi andaaza nahi.** "wo jo kal wale ko bhej do", "usi ko call karo", "call him", "usko payment kar do" -> NOVA poochta hai "Kisko? Naam ya number bolo." Naam / number bol diya to purana normal raasta chalta hai. Driving / message-mode ke "reply" commands ko ye nahi chhoota.
5. **UI se juda (aapka standing rule):** MADAD / HelpGuide / aiFacts me 2 naye topic (Persona - Part 9 ka chhoota hua - aur Roundabout), SKILLS list me 2 entry, page self-check list me naya block `pt10_wire`.
6. **Ek asli dead switch mila aur jodha:** Settings ka "Background noise cancellation" (`nc_on`) checkbox kisi se juda nahi tha. Ab wahi bridge use karta hai jo kaam karne wala `wb_nc` karta hai; dono saath badalte hain.
Persona (Part 9) ko chhua nahi: ye part sirf "kya samajhna hai" badalta hai, "kaise bolna hai" nahi. "hint" aur "clarify" jawab par persona kabhi nahi lagta.

## Files
Naya: `RoundaboutRules.kt`, `RoundaboutRulesTest.kt` (JUnit), `tools/test_roundabout_contract.py`, `tools/test_p10_ui_runtime.py`. Badla (chhote hisse): `Logic.parseAnswer` (1 line), `ContextMemory.resolve` (1 branch), `NovaService.handle` (2 chhote block), `HelpGuide.kt` (2 topic), `ui.html` (2 topic + 2 skill entry + `pt10_wire`), versionName 7.50.

## Test status (honest)
- RUN aur PASS: `tools/run_all_checks.sh` (saare contract scripts + check_p5..p8), `test_roundabout_contract.py` (asli files ki wiring + Python mirror), `test_p10_ui_runtime.py` (headless Chromium: #nc_on setNoiseCancel bulata hai, page me JS error nahi), ui.html ke saare JS block ka node syntax check.
- NOT RUN: Kotlin compile, JUnit (`RoundaboutRulesTest.kt` ke expected values haath se nikaale), asli phone, GitHub Actions. Ye ab tak kul 4 versions ka Kotlin hai jo compile nahi hua: v7.45-7.46, v7.48, v7.50.
- Python "mirror" asli Kotlin nahi hai; wo sirf shabd-suchi aur niyam dobara chalata hai. Asli saboot sirf JUnit + phone hai.

## CHECKPOINT 2 : aapko phone par kya dekhna hai (build ke baad, ek ek karke)
1. NOVA se koi aisa kaam karwao jisme wo haan/na poochhe (jaise "mummy ko call karo"). Jab pooche to bolo **"ha kr do"**. Pehle ye cancel ho jaata tha; ab chalna chahiye.
2. Bolo "torch jala do". Phir "ulta kar do". Torch band honi chahiye. Phir "wahi kar do" - torch phir jale.
3. Bolo "andhera hai" - torch jalni chahiye.
4. Bolo "usi ko call karo" - NOVA kisi ko call NAHI karega, poochhega "Kisko?".
5. Settings kholo -> "Background noise cancellation" switch dabao - chalna chahiye (neeche chhota status likha aayega).
6. MADAD kholo - "Jarvis andaaz" aur "Ghuma ke bole kaam samajhna" dikhne chahiye.
Jo galat lage wo seedha batao, agle account ko wahi pehle theek karna hoga.

## Baaki
Part 11 se 20. Plan: `NOVA_20_PARTS_PLAN.md`. Prompt: `NEXT_ACCOUNT_PROMPT_v7_50_PART11.md`.
Purane known bugs jo abhi baaki hain: photo + sawaal vision (Part 13), website verify warning (Part 16), photoToSite compile / phone test, voice command jab NOVA busy ho queue me nahi jaata (sirf typed), wake word locked phone (owner ka faisla baaki).
