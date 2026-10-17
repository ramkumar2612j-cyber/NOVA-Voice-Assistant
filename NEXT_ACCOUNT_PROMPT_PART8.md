# NOVA Part 8 (naye account ke liye)
Base: NOVA-WIP-v75-PART7.zip. Pehle padho: PART7_STATUS.md, PART6_STATUS.md. Owner Hinglish bolta hai, sirf phone se kaam karta hai. Jo ho chuka use rewrite mat karo. Strict rules: kuch delete/disable nahi, HAAN/NAHI confirmation nahi hatana, keys log nahi, NOT TESTED honestly likho.

## Part 7 mein ho chuka
- UI (ui.html, block `p7_core3d` + `p7_tabfix`): home par ACTIVATE beech mein; tab bar ke beech 3D CORE orb jo ACTIVATE panel kholta hai; Settings ka SAVE icon.
- AI SMART REPLY (Settings > Auto-reply > "AI SMART", default OFF): SmartReplyRules.kt, NovaService.smartDraft, NovaNotificationService.smartAutoReply. Owner ko History + notification + voice se batata hai. Paise/rate/link/number ka jawab nahi, wo owner ko jata hai.
- run_all_checks: 64 passed. Feature audit (static): 33 features ke peeche asli code.

## SABSE PEHLE (zaroori)
1. Kotlin Part 7 mein COMPILE NAHI hua (sandbox mein internet band tha). Pehle GitHub Actions log dekho ya internet wale environment mein `bash tools/local_compile_check.sh /tmp/nova_check` chalao. Errors ho to pehle SmartReplyRules.kt, NovaService.smartDraft, NovaNotificationService.smartAutoReply, SmartReplyRulesTest.kt dekho.
2. Asli phone par owner se check karao: 3D CORE orb smooth? ACTIVATE sach mein start/stop? AI SMART se WhatsApp par jawab + notification? Popup band? 5s speed? Quality=SMOOTH?

## Baaki kaam
3. Smart reply ko Business Watch (WaBiz) window mein bhi jodo (abhi wahan sirf catalogue jawab). Owner ke naam ka setting (abhi AI "the owner" kehta hai).
4. Website quality: SiteGen 2-pass (structure, phir polish) ya section-by-section; premium gate sakht.
5. Voice se mobile ke aur kaam (AgentRules/PhoneActionRules) sirf maujooda confirmation ke saath; bolkar ON/OFF sirf confirmation ke saath.
6. Aakhir mein: PART8_STATUS.md, Hindi status, zip, NEXT_ACCOUNT_PROMPT_PART9.md.
