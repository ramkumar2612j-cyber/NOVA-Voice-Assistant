# NOVA v7.53 : PART 13B-1 POORA (ANALYSE button + engine) - status (simple Hindi, honest)

## Progress: 13B-1 = 100%   |   13B = 50% (13B-1 / 13B-2)   |   Part 13 = 37% (13A, 13B-1 poore; 13B-2, 13C, 13D baki)
Aapne 13B ko 2 hisso me toda. Pehla hissa (13B-1) poora: ab app me pehli baar **ANALYSE** button aaya hai.

## App me kya naya dikhega
MEDIA MILA card me har photo / video ke saath **ANALYSE** button (FRAMES NIKALO ke paas). Upar ek chhota box: "Sawaal likho (zaroori nahi)".
1. ANALYSE dabao -> stage chalte hain: file taiyar -> frames -> AI tasveer dekh raha hai (1/2, 2/2). Bar sirf asli kaam se badhta hai.
2. ROKO ya STOP dabao -> turant ruk jata hai.
3. Report text me aati hai: summary, kya dikha, likha hua text, "verify nahi hua" wale dawe, bharosa (low / medium / high + wajah), limits.
4. Abhi web par jaanch nahi hoti (wo 13C me), isliye saare dawe "verify nahi hua" likhe aate hain. Kuch banaya nahi jata.
5. Frames AI (Gemini) ko SIRF tab jate hain jab aap ANALYSE dabate ho (card ke neeche likha hai).

## Mana karna (sach)
- "ye kaun hai" jaise sawaal par chehre se naam nahi batata; nuksaan wali cheez; OTP / PIN / password / card number: saaf mana, kuch bheja nahi jata.
- Gemini key nahi / internet nahi / limit poori: sirf file ki size / length batata hai ("Images offline analyse nahi ho sakti"), kuch dekha nahi, kuch banaya nahi.
- NOVA band ho to pehle NOVA chalu karo, tab ANALYSE chalega.
- Chat me jodi hui photo ka sawaal: abhi nahi (13D). Bolkar "video analyse karo" bhi abhi nahi (13D).

## Technical (sankshep)
Naye: AnalysisPipelineRules.kt (pure), AnalysisPipeline.kt, AnalysisPipelineRulesTest.kt (14 test). NovaService.analysisBrain (wahi key / model / quota raasta, naya HTTP client nahi). Bridge: analysisStart / Status / Cancel / Result / ResultText. STOP ab analysis bhi rokta hai. MADAD topic "Photo / video ANALYSE karna" + SKILLS entry. Version 7.53. Nayi permission nahi (30 hi). build.yml ko haath nahi lagaya.

## Test status (honest)
- PASS (is chat me chala): poora project Kotlin COMPILE (kotlinc 1.9.24, 0 error); JUnit sab chale: sirf purane 29 fail (org.json-stub wale: ConnectorParse, WebBrowsers, WebSources, VercelPublish, ConnectorRules, FeatureVerify, MusicCatalog, NovaV73); AnalysisRulesTest (13A, 45) aur AnalysisPipelineRulesTest (14) PASS.
- PASS: tools/run_all_checks.sh = 120 script, 0 fail (naya test_analysis_pipeline_contract.py aur test_p13_ui_runtime.py shamil; ye UI test headless Chromium + nakli bridge hai, phone nahi).
- NOT TESTED: asli phone (Gemini ko asli frames bhejna, bar, ROKO / STOP, report). GitHub Actions build abhi nahi chalaya.
- Limits: ROKO ke baad chal rahi network call (<= 35 sec) band nahi hoti, bas uska jawab phenk diya jata hai. Free provider (Groq etc.) abhi sirf text hain, isliye Gemini ke bina vision nahi: 13B-2 me dekhenge.

## Aapko abhi kya karna hai
Kuch zaroori nahi. Chaho to zip GitHub par daal ke build chalao; red ho to sirf laal lines bhejo. Phone test checkpoint 15 par hai.
## Agla: 13B-2. Prompt: NEXT_ACCOUNT_PROMPT_v7_53_PART13B2.md
