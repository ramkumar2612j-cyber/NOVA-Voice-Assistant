# NOVA v7.60 : PART 13D ACCEPTANCE ~60% (owner ke kehne par yahin pack) - status (simple Hindi, honest)
Owner ne 60% par pack karaya. Agla account yahin se aage kare. Part 13 ~93%: 13A, 13B, 13C poore; 13D code poora; ACCEPTANCE aadha.

## Is account me KYA HUA (60%)
1. Zip khola, 13D status + prompt + trace + plan padhe. Sandbox me INTERNET NAHI, kotlinc NAHI => asli Kotlin compile yaha NAHI ho saka (GitHub Actions / tools/local_compile_check.sh chahiye).
2. Hath se (compile ke bina) padhe aur har symbol / signature milaya: AnalysisVoiceRules.kt, AnalysisSearchRules.kt, AnalysisPipeline.kt, AnalysisPipelineRules.kt, FreeVision.kt, NovaService (analyseByVoice, deliverAnalysis, analysisChatLine, analysisBrain, voice branch ~2201), MainActivity.chatPhotoAsk. Koi compile galti nahi mili. YE COMPILE KA SABOOT NAHI HAI.
3. AnalysisVoiceRulesTest.kt ke 10 JUnit ke expected values hath se simulate kiye: sab sahi.
4. SAFETY GAP theek (add-only): AnalysisRules.FACE_PHRASES me naye phrase ("image / pic / picture / reel / clip kaun hai", "isme kaun hai", "who is in this image / picture / reel / clip", "who s this", "who is that person"). Pehle "ye image kaun hai" face-gate se nikal sakta tha.
5. STALE TEST theek: tools/test_free_vision_contract.py purana finishMetadata signature dhundh raha tha (13D ne chat parameter jodha, behaviour same). Ab dono signature maanta hai; akele chalane par PASS.
6. HEADLESS: tools/test_p13_ui_runtime.py me add-only OVER3 + section 7 (chat photo + sawaal): chatPhotoAsk sawaal ke saath bulaya jata hai; HAAN box khula ho to purana sendText route nahi chalta aur input bacha rehta hai; onChatPhotoGo par input saaf; "notquestion" par purana route chalta hai; refusal line TEXT me dikhti hai (HTML nahi); edit sentence (kind != NONE) aur photo ke bina chatPhotoAsk nahi bulaya jata. Result PASS (320 / 360 / 412). Mutation check (test sach me fail kar sakta hai ya nahi) NAHI kiya.
7. tools/run_all_checks.sh beech me roka gaya: 72 script PASS, 1 FAIL (wahi stale test, ab theek). Aakhri chala: test_part3_stop_silent_contract.py. POORA RUN NAHI hua.

## BACHA HUA (agla account yahi kare, is order me)
1. tools/run_all_checks.sh poora (PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers; setsid nohup se background me chalao, ek command 300 s se zyada nahi chalti; pkill -f mat karo, apni hi shell mar deta hai). 0 new fail chahiye.
2. Mutation check: pt13d_chat ka `if(k!=='NONE')return;` hata kar test_p13_ui_runtime FAIL deta hai ya nahi dekho, phir wapas karo.
3. AnalysisVoiceRulesTest me add-only assert: gateQuestion("ye image kaun hai") / ("isme kaun hai") / ("who is in this reel") == REFUSE_FACE_ID; gateQuestion("is photo me kya hai") == OK.
4. COMPILE + JUnit (sabse zaroori, internet chahiye): tools/local_compile_check.sh /ABSOLUTE/PATH ya GitHub Actions. 13B-2a..13D ka Kotlin kabhi compile nahi hua: FreeVision.kt, AnalysisSearchRules.kt, AnalysisVoiceRules.kt, AnalysisPipeline.kt, NovaService / MainActivity / FeatureStatus ke naye hisse, AnalysisSearchRulesTest, AnalysisVoiceRulesTest, FreeVisionTest. Purane 29 org.json-stub JUnit fail alag hain (ConnectorParse, WebBrowsers, WebSources, VercelPublish, ConnectorRules, FeatureVerify, MusicCatalog, NovaV73). JUnit expected galat nikle to pehle dekho code galat ya expected.
5. FeatureVerify (FeatureCheck.kt) + FeatureVoice me analysis voice ki ek line (agar sasta). docs/PART13_TRACE_v7_52.md rows 15-17, 20-24, 27-28 DONE (phone NOT TESTED).
6. versionName abhi 7.59 (app/build.gradle.kts + tools/test_android_runtime_contract.py). Is zip me bump NAHI kiya; badlo to dono jagah saath.
7. CHANGELOG v7.60 entry (chhoti hai, poori karo); NOVA_20_PARTS_PLAN STATUS v7.60 (jod diya).
8. Owner test list final karo, phir Part 13 POORA = owner phone test, baad me PART 14.

## Owner phone test list (Part 13 poora hone par)
(a) kisi app se video / photo share -> MEDIA card -> ANALYSE -> report + sources
(b) bolkar: "nova ye video analyse karo"
(c) internet band karke ANALYSE -> honest "file ki size" report
(d) "is photo me kaun hai" / "ye kaun hai" / "ye image kaun hai" -> saaf mana
(e) chat me photo jodo + "ye kya hai?" -> HAAN box -> jawab chat me; NAHI dabao -> kuch nahi jata
(f) STOP bolo -> turant ruke
(g) bina key: honest line

## Honest limits
- Kuch bhi asli phone / network par test nahi hua. Kotlin compile NOT RUN. JUnit NOT RUN. run_all_checks PARTIAL (72 PASS).
- Gemini grounding ke link Google redirect hote hain; Gemini na ho to compose nahi chalta. Chat photo ka jawab chat me aata hai, bolta nahi. Chat photo ke liye ROKO nahi: STOP bolo.
- Add-only, persona analysis par nahi, chat size / + button nahi chheda.
