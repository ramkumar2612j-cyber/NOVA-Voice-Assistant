# NOVA v7.59 : PART 13D - ADHURA (code ~75%, acceptance baki) - status (simple Hindi, honest)
Owner ne yahin pack karne ko kaha: agla account 13D ka bacha hua kaam poora karega. Part 13 = ~90% (13A, 13B, 13C poore; 13D ka code likha hai, ACCEPTANCE (compile + test + phone) baki).

## 13D me KYA BAN CHUKA (code me, kabhi compile NAHI hua)
1. **Voice / typed**: "nova ye video analyse karo", "is photo me kya hai", "isko check karo ki sach hai ya nahi" => asli analysis. Naya `AnalysisVoiceRules.kt` (pure): parse (ANALYSE / CHECK / NONE), question(), pickTarget() = sabse naya photo / video (link kabhi nahi), honest texts. `NovaService.analyseByVoice` + `deliverAnalysis` (poori report chat me text ke roop me, voice sirf chhota summary, silent mode me kuch nahi bolta, persona nahi: lastKind "media"). Purana ANALYSE_NOT_YET branch add-only rakha (ab unreachable fallback).
2. **KNOWN BUG theek (chat me photo + sawaal)**: `MainActivity.chatPhotoAsk(question)` bridge + `ui.html` block `pt13d_chat`. Sawaal pehle gate hota hai (face / harm / secret = mana, kuch nahi jata), phir native **HAAN, BHEJO / NAHI** box; HAAN ke baad hi `AnalysisPipeline.startChat` (photo RAM me, FrameExtractor nahi). Jawab chat me text me (`emit`), history me path / media nahi. Edit / image / website wale sentence purane raaste par (kind != NONE).
3. `AnalysisPipeline`: `Notify` (voice / chat run ke end par ek baar: success, fail, watchdog, cancel), `startChat`, `CHAT_ID`, `launch()` refactor, run() me chat bytes support.
4. MADAD (HelpGuide.kt) + ui.html CAPS Hindi text me voice examples; `media` topic ka "bolkar analysis abhi nahi" sach kiya. FeatureStatus me nayi row `analyse_web`. versionName 7.59 (build.gradle.kts + test_android_runtime_contract.py).
5. Tests likhe: `AnalysisVoiceRulesTest.kt` (10 JUnit), `tools/test_analysis_voice_contract.py`.

## Chala hua (static): test_analysis_voice / search_compose / analysis_pipeline / analysis_rules / media_voice / part6_skills / help contracts PASS.
## NOT RUN: Kotlin compile (13C + 13D ka sab naya Kotlin kabhi compile nahi hua: AnalysisSearchRules.kt, AnalysisVoiceRules.kt, AnalysisPipeline.kt, NovaService / MainActivity / FeatureStatus ke naye hisse, 2 naye JUnit files), JUnit, tools/run_all_checks.sh, headless render, GitHub Actions, phone.

## BACHA HUA (agla account yahi kare)
1. **Compile + JUnit** (sabse pehle): `tools/local_compile_check.sh` (kotlinc download github se) ya GitHub Actions. Naye Kotlin ki galtiyan theek karo. Jo JUnit expected galat nikle (haath se nikale the) pehle dekho kya code galat hai ya expected; honest raho.
2. `tools/run_all_checks.sh` poora, 0 new fail. Purane stale guards KNOWN_STALE_* me hain.
3. `tools/test_p13_ui_runtime.py` (headless FAKE bridge): FAKE me `chatPhotoAsk` jodo + chat photo flow ka ek check (320 / 360 / 412).
4. FeatureVerify (FeatureCheck.kt) + FeatureVoice me analysis voice ki ek line (agar sasta ho), `docs/PART13_TRACE_v7_52.md` rows 15-17, 20-24, 27-28 DONE (phone NOT TESTED), CHANGELOG v7.59, NOVA_20_PARTS_PLAN STATUS UPDATE v7.59.
5. Owner ke liye simple Hindi test list (PART13D_STATUS me likho): (a) kisi app se video / photo share -> MEDIA card -> ANALYSE -> report + sources (b) wahi bolkar: "nova ye video analyse karo" (c) internet band karke ANALYSE -> honest "file ki size" report (d) "is photo me kaun hai" / "ye kaun hai" -> saaf mana (e) chat me photo jodo + "ye kya hai?" -> HAAN box -> jawab chat me; NAHI dabao -> kuch nahi jata (f) STOP bolo -> turant ruke (g) bina key: honest line.
6. Phir Part 13 POORA = owner phone test; baad me Part 14.

## Honest limits (13C + 13D)
- Gemini grounding ke link Google redirect hote hain. Gemini na ho to compose nahi chalta (dawe unverified). Key-less sources general topics ke liye.
- Chat photo ka jawab chat me aata hai, bolta nahi (poora read-out Part 15).
- Voice me "ye kaun hai" bina media-word ke chat AI ko jata hai (photo ke bina); photo ke sath chat route gate se mana karta hai.
- Chat photo ke liye ROKO button nahi: STOP bolo / HUD STOP.
- Real network / phone par kuch test nahi hua.
