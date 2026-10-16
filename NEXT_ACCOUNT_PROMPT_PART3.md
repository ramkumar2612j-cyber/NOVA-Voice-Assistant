# NOVA - Part 3 of 5 (agla account yehi prompt use kare)

Hinglish mein, chhota aur seedha jawab do. Pehle audit, phir kaam. Jo feature chal raha hai use mat todo, jo ban chuka hai dobara mat banao. Fake "complete" mat bolo.

Project: NOVA-Voice-Assistant.zip (isi zip ko patch karo, naya zip wapas do, naam exactly NOVA-Voice-Assistant.zip).
Compile sirf GitHub Actions par hota hai. Jo compile/test nahi hua wo saaf likho (PASS / FAIL / NOT TESTED).

## 5 parts ka plan
1. Call uthana / reject voice se  -> DONE (compile NOT RUN)
2. Voice screenshot, ringer, auto-rotation + UI cards  -> DONE (compile NOT RUN)
3. AI SMART reply (Instagram/WhatsApp)  -> YE KARNA HAI
4. Jin 15 settings ke bridge methods ka UI button nahi hai unke buttons
   (listChats, selfTest, selfTestFree, selfTestImage, clearErrors, getWaitSec, setWaitSec, getMemoryText, getAutoLearn, setAutoLearn, getWebBrowsers, getLocalFirst, getHomeCity, testBrowser, requestCalendarRead)
5. NOVA ki help/knowledge (HelpGuide, FeatureCheck/FeatureStatus) mein saare naye features + final tests + final zip

## Part 3 ka kaam (AI smart reply)
- SmartReplyRules.kt pehle se zip mein hai (pure rules: allowedForAi, systemPrompt, userPrompt, sanitize, choose). Wired nahi hai, test nahi hai.
- ExtCfg mein smartReply switch (default OFF). Settings mein toggle + saaf line: "message ka text cloud AI ko jata hai".
- NovaNotificationService.autoReply: insult wala rasta SocialReplyRules se pehle jaisa rahe. Jab smartReply ON ho, SmartReplyRules.allowedForAi(text) true ho, to NovaService ka naya public smartReply(sender, text) (private agentChain use kare, worker thread par, timeout ~8 sec) ek chhota jawab banaye. Fail / None -> purana fixed jawab (SmartReplyRules.choose). Jawab kabhi khaali nahi.
- AutoReplyRules ki limits (2 per ghanta, 10 min cooldown, 80 per din) waisi hi.
- Paise/OTP/link/number wale message cloud AI ko KABHI nahi jaate.
- SmartReplyRulesTest likho. ui.html mein toggle + Skills card (state: switch ON + notifAccess).
- ActionLog mein jawab ka note "AI" ya "fixed" dikhe.

## Mat karna
- PIN / password se phone unlock KABHI nahi (owner ne nahi manga).
- LockGuard ka behaviour mat badlo.
- Koi yes/no sawal nahi, kaam poora karo.

## Har part ke baad do
1. Updated NOVA-Voice-Assistant.zip
2. Build/test report: modified files, har feature PASS / FAIL / NOT TESTED, bache bugs
3. Agle part ka chhota prompt
4. Continuation notes (done, pending, architecture decisions, next steps)
