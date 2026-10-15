# NOVA Part 8 Status

Base: NOVA-WIP-v75-PART7.zip. Kuch delete/disable/replace nahi kiya. HAAN/NAHI confirmation koi nahi hata.
run_all_checks: 65 passed, 0 failed (Python contract checks). Kotlin COMPILE NAHI hua, JUnit CHALA NAHI (sandbox mein internet band, kotlinc nahi).

## Ho chuka (sab Kotlin sirf padh-kar/pattern-match se likha, compile nahi)
1. Owner ka naam: Settings > Auto-reply mein naya box. `ActionLog.ownerName/setOwnerName`, `SmartReplyRules.cleanName` (sirf letters/digits/space . - ', max 30, prompt injection nahi), bridge `getOwnerName/setOwnerName`, ui.html `ar_owner`. AI ab "<naam> ka assistant" kehta hai (khali ho to "the owner").
2. Smart reply Business Watch (WaBiz) window mein: `NovaNotificationService.bizSmart`. Tab chalta hai jab Auto-reply ON + AI SMART ON + Business watch "auto on". Owner ka catalogue jawab hamesha pehle (replace nahi hota); URGENT par owner ko batana purana hi hai. Business prompt (`systemPrompt(..., business=true)`) AI ko stock/size/rate/delivery batane se rokta hai ("[OWNER]" se owner confirm karega). Same notification do baar aaye to ek hi jawab (`SmartReplyRules.seen`, 2 min).
3. `bizAuto(true)` ab khali catalogue par bhi chalta hai agar AI SMART ON hai (pehle "Catalogue khaali hai" kehkar rok deta tha).
4. Bolkar ON/OFF: "smart reply on/off", "ai reply band karo", "auto reply on/off" (`SmartReplyRules.command` -> Logic -> NovaService.smartReplyVoice/autoReplyVoice). HAMESHA pehle haan/nahi poochta (askFirst; ON par must=true). Sawal ("kya hai", "status batao"), "business watch ..." wale aur unclear (on+off dono) vaakya kuch nahi badalte. Jawab mein bata deta hai agar Notification access / AI chain / Auto reply band ho.
5. Website 2-pass: pass 1 purana (structure + spec repair). Phir naya `SiteQuality.check` (premium gate: viewport, title, lang, h1, landmarks, responsive, CSS variables, font, hover/focus+transition, reduced-motion, lorem, img alt, remote image, dead "#" links, band na hui style/script, stub). Gaps mile aur 100 sec se kam gaye hon to ONE polish call (pass 2) sirf unhi gaps ke saath; polished page tabhi rakhta hai jab gaps kam hon aur requirement problems zyada na hon (`SiteQuality.better`). Baaki gaps `Out.quality` mein, owner ko bol kar bata diye jate hain. Edits (follow-up) par gate nahi lagta.
6. HelpGuide "Auto-reply" text + ui.html CAPS mein AI SMART aur bolkar ON/OFF ki baat (example line wahi rahi, help contract PASS).
7. Naye tests: SiteQualityTest.kt, SmartReplyRulesTest.kt mein 4 naye test (CHALE NAHI), tools/test_site_quality_contract.py (PASS), test_smart_reply_contract.py badhaya (PASS).

## NOT DONE
- Kotlin compile + JUnit: Part 7 aur Part 8 dono ka Kotlin ek bhi baar compile nahi hua. Pehle yahi karo.
- PART8 kaam 5 ka pehla hissa: voice se mobile ke AUR kaam (PhoneActionRules/AgentRules mein naye tools). Shuru nahi hua. Maujooda 19 tools (open_app, call, sms, whatsapp, alarm, timer, torch, volume, navigate, play_music, camera, settings, brightness, brightness_step, media, global_action, calendar_event, search_web) hain. Sujhav: global_action mein "screenshot", ringer(vibrate/normal), rotation. Har naya tool runTool + PhoneActionRules schema + prompt + tests, aur maujooda confirmation ke saath.
- Website "section-by-section" generation: nahi kiya (sirf 2-pass).
- Asli phone par kuch test nahi: 3D CORE orb, ACTIVATE, AI SMART jawab, Business Watch smart reply, naam, voice ON/OFF, website polish quality.

## Jaanne layak
- Polish call se website banne mein ek aur AI call lagti hai (total budget 170 sec ka hi hai; polish 100 sec ke baad shuru nahi hota). Gate ki shartein asli AI output par abhi tune nahi hui: agar har site "quality baaki" bole to shartein dheeli karo.
- Business smart reply "auto on" ke bina nahi chalta (owner ne explicitly allow nahi kiya to nahi).
