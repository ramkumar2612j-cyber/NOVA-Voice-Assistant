# NOVA Part 10 (naye account ke liye)
Base: NOVA-WIP-v76-PART9.zip. Pehle padho: PART9_STATUS.md, PART8_STATUS.md. Owner Hinglish bolta hai, sirf Android phone se kaam karta hai (Chrome + GitHub, koi PC/terminal nahi): ek step ek baar, tap-by-tap exact github.com links, lambi edit ho to POORI file code block mein. Jo ho chuka use rewrite mat karo. Strict rules: kuch delete/disable nahi, HAAN/NAHI confirmation nahi hatana, keys log nahi, NOT TESTED honestly likho. Owner chahta hai: asli kaam, kuch fake nahi.

## SABSE PEHLE
1. Kotlin Part 7 + 8 + 9 kabhi compile nahi hua. Internet wale environment mein `bash tools/local_compile_check.sh /tmp/nova_check` ya owner se GitHub Actions ("Build and Test APK") ka log lo. Errors ho to in files se shuru: SmartReplyRules.kt, SiteQuality.kt, SiteGen.kt (polish block), NovaService.kt (smartDraft, smartReplyVoice, autoReplyVoice, bizSmart, globalAct screenshot, ringerTool, rotationTool), NovaNotificationService.kt, ActionLog.kt, MainActivity.kt, PhoneActionRules.kt, ToolVerify.kt, PhoneActionRulesTest.kt, SmartReplyRulesTest.kt, SiteQualityTest.kt. Exact root cause batao: project ka bug ya owner ki entry ki galti.
2. Asli phone par check: screenshot, "phone vibrate par karo", "silent karo" (DND access ke saath/bina), rotation lock, plus Part 8 ki list (3D CORE orb, ACTIVATE, AI SMART, Business Watch smart reply, naam, voice ON/OFF, website polish).

## Part 9B (ho chuka, NOT TESTED)
AI SMART ab money/link/number par nahi rukta; sirf OTP/password/PIN/CVV rokta hai. Owner facts (`convo.addFact`, voice WhatsApp se) AI ko milte hain; jo pata nahi wo [OWNER] + notice. Details PART9_STATUS.md "Part 9B". SmartReplyRules.kt, NovaService.kt (smartDraft, whatsappTool) compile check mein dekho. Is rule ko phir se sakht mat karo jab tak owner na kahe.

## Baaki kaam
3. ringer/rotation/screenshot ke local (bina AI) voice shortcuts Logic.kt mein + HelpGuide/CAPS text (help contract PASS rakhna).
4. Website: asli AI output dekh kar SiteQuality tune karo; section-by-section sirf tab jab polish ke baad bhi gaps zyada rahen (170 sec budget dhyan mein).
5. Aakhir mein: PART10_STATUS.md, Hindi status, zip, NEXT_ACCOUNT_PROMPT_PART11.md.
