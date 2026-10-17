NOVA Android app (zip attached: NOVA-WIP-v75.zip). Pichla kaam (website builder v7.0) poora ho chuka hai. Ab ye naya kaam chal raha hai, baaki hissa yahan se continue karo. Existing features bilkul mat bigadna. Har naye kaam ke baad `bash tools/local_compile_check.sh` (absolute path se) aur `python3 tools/test_*.py` chalao. Note: sandbox mein internet band ho to kotlinc download nahi hota (compile check chalega hi nahi, wo GitHub Actions pe hota hai); org.json stub ki wajah se 24 JSON-parse tests (ConnectorParseTest, WebSourcesTest, WebBrowsersTest, ConnectorRulesTest.n8nParsers) pehle se fail dikhte hain: ye normal hai. Owner Hinglish mein baat karta hai, sirf Android phone se kaam karta hai: jawab chhota, ek step ek time.

OWNER KI MAANG (Hinglish):
1) Chat mein mic aur send button ChatGPT jaisa.  -> DONE (ui.html).
2) NOVA "haan bolo" puchhe to popup mein HAAN / NAHI buttons.  -> DONE code mein (NovaHud.kt, NovaService.answerPending/askListener/pendingPromptNow, MainActivity pendingAsk/answerAsk, ui.html #c_ask). Compile + test baaki (Actions pe).
3) NOVA ko ChatGPT/Claude jaisa banao: lagbhag har kaam, saare connected AI fallback mein, web search karke bataye.  -> PART A + B DONE (v7.3), C baaki.
4) Kaam ke baad NOVA "thinking" tez ho.  -> TODO
5) Pehle owner ki baat samjho (ek line mein "Samjha: ...").  -> rich prompt mein hai (backup AIs); siteBegin() aur Gemini par TODO
6) Har cheez app mein update dikhe.  -> TODO (har naya feature ui.html + MADAD/HelpGuide mein)

V7.2 MEIN KYA HUA (Plan A, backup AIs ke liye):
- FreeProviders.kt: richSystemPrompt(en), cleanRich() (newlines/lists/code bachte hain, cap 8000), speakable() (TTS ke liye markdown hata kar ~800 char), MAX_USER_CHARS_RICH=6000, RICH_MAX_TOKENS=2000, buildBody(..., userCap), parseReply(body, rich), ProviderChain.run(..., rich = false).
- NovaService.kt: @Volatile richTurn (typed() mein true, finishTurn mein false); runFreeChain(said, en, withFacts, rich) rich prompt + rich=true use karta hai; sirf cloud-answer path (runFreeChain(said, en, rich = richTurn)) mein lagaya, line ~3244 wala "explain in 4 sentences" call plain hi hai.
- Naya test: app/src/test/java/com/nova/assistant/RichChatTest.kt (7 tests, abhi Actions pe chalana baaki). 58 python contract tests pass. NOTE: tools/test_autosetup_contract.py aur test_fallback_context_contract.py exact text dekhte hain ("extra = p.extra", "turns = turns"), buildBody call named-args mein rakho.

V7.3 (Plan B DONE): naya WebContext.kt (needsWeb, queryOf, block, sourceNames, withSources) + NovaService.webItemsFor() (Gemini searchWeb agar key ho, warna Google News/DuckDuckGo/Wikipedia, short timeouts). Sirf typed (rich) turn par; WEB RESULTS block system prompt mein jaata hai, chainPreAnswer ka 'live source nahi' jawab tab skip hota hai jab web text mila; jawab mein Sources: line na ho to jud jaati hai. Naya test WebContextTest.kt (Actions pe chalana baaki).

V7.3 PURA (C-G done): ui.html markdown renderer + COPY (mdRender/copyBtn/cadd), FreeProviders.speakable() TTS mein (speak(), speakClick, MainActivity.speakMsg), Gemini GEMINI_RICH_NOTE (richTurn par), GeminiSpeed.kt (2.5 flash thinkingBudget=0, 400 par retry), SiteSummary.kt ("Samjha: ..." siteBegin ke jawab mein), HelpGuide "questions" topic + ui.html CAPS update, CHANGELOG v7.3. Naye tests: RichChatTest, WebContextTest, NovaV73Test, tools/test_rich_chat_contract.py. 59 python tests pass.

ABHI KARNA HAI (next account):
1) GitHub Actions "Build and Test APK" chalao: Kotlin compile + saare JUnit tests yahan nahi chale; koi compile error aaye to usse theek karo (sabse pehle FreeProviders.kt, NovaService.kt, WebContext.kt, GeminiSpeed.kt, SiteSummary.kt dekho).
2) Phone par check: chat mein lamba sawal (code/plan) -> lamba jawab + COPY; "aaj ka news" -> Sources ke saath; website bolo -> pehle "Samjha:" line; HAAN/NAHI popup.
3) Agar owner naya kaam de to usi zip se continue karo. Spoken path (FreeProviders.systemPrompt, 3 vaakya) mat badlo.

V7.4 (owner: "ghuma ke bolun to bhi NOVA samjhe"): PhoneActionRules.likelyAction v2 (stems + NOUNS + CUES + Devanagari), systemPrompt intent-first + examples + {"actions":[...]} (parseAll, MAX_ACTIONS=3), NovaService.cloudStep loop runs the list and stops at pending confirmation, Gemini voice prompt mentions hints. Test: PhoneActionUnderstandTest. Sab static check hua (python 60 tests + bracket balance); Kotlin compile/JUnit Actions pe baaki.
IDEAS (agar owner kahe): local IntentParser mein bhi indirect cues (offline), "NOVA ne kya samjha" ek line chat mein dikhana action se pehle, rank/log ki kaun sa AI sabse sahi action deta hai.
