# NOVA Part 9 (naye account ke liye)
Base: NOVA-WIP-v75-PART8.zip. Pehle padho: PART8_STATUS.md, PART7_STATUS.md. Owner Hinglish bolta hai, sirf Android phone se kaam karta hai (Chrome + GitHub website, koi PC/terminal nahi): ek step ek baar, tap-by-tap exact github.com links, lambi edit ho to POORI file code block mein. Jo ho chuka use rewrite mat karo. Strict rules: kuch delete/disable nahi, HAAN/NAHI confirmation nahi hatana, keys log nahi, NOT TESTED honestly likho. Owner chahta hai: asli kaam, kuch fake nahi.

## Part 8 mein ho chuka (details PART8_STATUS.md)
- Owner naam setting (ActionLog.ownerName, ar_owner), Smart reply Business Watch mein (NovaNotificationService.bizSmart, business prompt), bolkar smart/auto reply ON/OFF (SmartReplyRules.command, NovaService.smartReplyVoice/autoReplyVoice, hamesha askFirst), website 2-pass + SiteQuality premium gate (SiteGen.generate, Out.quality).
- run_all_checks: 65 passed.

## SABSE PEHLE (zaroori)
1. Kotlin Part 7 + Part 8 mein COMPILE NAHI hua. Internet wale environment mein `bash tools/local_compile_check.sh /tmp/nova_check` chalao, ya owner se GitHub Actions ("Build and Test APK" / "Build NOVA from zip") ka log lo. Errors ho to pehle in files ko dekho: SmartReplyRules.kt, SiteQuality.kt, SiteGen.kt (polish block), NovaService.kt (smartDraft, smartReplyVoice, autoReplyVoice, bizAuto, runCommand), NovaNotificationService.kt (smartAutoReply, bizSmart, handleBiz), ActionLog.kt, MainActivity.kt (getOwnerName/setOwnerName), SmartReplyRulesTest.kt, SiteQualityTest.kt. Owner jab log paste kare to exact root cause batao aur ye bhi ki project ka bug hai ya owner ki entry ki galti.
2. Asli phone par owner se check karao: 3D CORE orb, ACTIVATE start/stop, AI SMART se WhatsApp jawab + notification, naam wala assistant line, Business Watch + AUTO ON mein smart reply, "smart reply on" bolne par haan/nahi, website polish.

## Baaki kaam
3. Voice se mobile ke aur kaam (PhoneActionRules/AgentRules/NovaService.runTool): sujhav global_action "screenshot" (API 28+), ringer(vibrate/normal; silent ke liye Do Not Disturb access), rotation (WRITE_SETTINGS jaise brightness). Har tool: PhoneActionRules schema+required+prompt, runTool, tests, aur maujooda confirmation/protected-screen rules ke saath. WiFi/Bluetooth ON-OFF Android allow nahi karta, jhooth mat bolo.
4. Website: section-by-section generation ka option (agar polish se quality kam lage), SiteQuality ki shartein asli AI output dekh kar tune karo. Premium gate sakht rakho.
5. Aakhir mein: PART9_STATUS.md, Hindi status, zip, NEXT_ACCOUNT_PROMPT_PART10.md.
