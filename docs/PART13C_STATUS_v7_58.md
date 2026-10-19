# NOVA v7.58 : PART 13C POORA (code me) - SEARCH + COMPOSE - status (simple Hindi, honest)

## Progress: 13C = 100% (code)  |  Part 13 = ~80% (13A, 13B, 13C poore; sirf 13D baki)
## Kya naya (ab report me kya badla)
Pehle report me saari baatein "verify nahi hua" aati thi. Ab ANALYSE ke baad:
1. **SEARCH**: AI ne jo cheezein check karne layak batayi, unki chhoti text query (<= 4, har ek alag call, <= 80 akshar) web par jaati hai. Photo / frames / poora sawaal / yaad kuch nahi jaata.
   - Pehle Gemini Google Search grounding (sources ke asli title + link + jis line ko wo source support karta hai).
   - Gemini na chale / kuch na de to key-less Wikipedia + Google News (title, link, tareekh). Sirf allowed hosts.
   - Internet nahi = search nahi chali (ye "kuch nahi mila" se alag likha jata hai).
2. **COMPOSE**: AI ko sirf dawe + numbered results milte hain (link KABHI nahi). Wo sirf result ka NUMBER batata hai. Galat / range ke bahar number = dawa "verify nahi hua" me. Jawab galat format me aaye to ek baar retry, phir bhi na bane to saare dawe unverified (sources list me, dawon se bandhe nahi).
3. Report ab `full` mode me aa sakti hai: har dawa ya asli source number ke saath, ya "verify nahi hua". Banaya hua link naamumkin: link sirf search tool se aata hai aur `sanitizeResults` se guzarta hai.
4. Search chali par kuch nahi mila => limits me nayi line: "Search me kuch nahi mila, isliye koi dawa verify nahi hua."
5. Quota: <= 4 search call + 1 compose (+1 retry), sab ApiUsage + Gemini cooldown se. STOP / ROKO turant rokta hai. Samay ka hisaab: 80 s ke baad nayi search nahi, 95 s ke baad compose nahi (120 s watchdog ke andar).
6. Progress bar: SEARCH k/n aur COMPOSE 0/1 -> 1/1 asli events se (fake timer nahi).
7. UI / MADAD text sach kiya: "web par jaanch nahi hoti" hata, consent line me "sirf chhoti text query jaati hai, photo nahi".

## Files (add-only)
- NAYI `AnalysisSearchRules.kt` (pure): groundedRequestJson, parseGrounded, parseWikiResult, parseNewsResults, keyless, interleave, runSearch, composeRequestJson, runCompose, searchAllowed / composeAllowed / composeNeeded, honest lines (searchNotRunLine, composeFailedLine, searchPrivacyLine).
- `AnalysisRules.kt`: noSearchResultsLine + limits me ek line.
- `AnalysisPipeline.kt`: Brain me callRaw / callTextWith / webGet (default khaali), run() me SEARCH + COMPOSE, buildReport(merged, composed, found, sent, isVideo, searchRan, en).
- `NovaService.kt`: analysisBrain me teeno call, wahi postFallback / ApiUsage / geminiCoolUntil; key-less GET WebBrowsers.httpGet se (allowed hosts). Naya HTTP client nahi.
- `HelpGuide.kt` + `ui.html`: analysis topic text + consent line. versionName 7.58 (build.gradle.kts + test_android_runtime_contract.py).
- Tests: `AnalysisSearchRulesTest.kt` (22 JUnit, haath se nikale expected), `tools/test_search_compose_contract.py`; purana 13B contract ab 13C wali buildReport line maangta hai.

## Test status (honest)
- CHALA: static contract scripts PASS: search_compose, analysis_pipeline, analysis_rules, android_runtime, free_vision, p13_ui_runtime (headless FAKE), part6_skills.
- NOT RUN (owner rule: Part 13 poora hone par ek baar): Kotlin compile, JUnit (AnalysisSearchRulesTest sahit), run_all_checks, GitHub Actions, phone. Is hisse ka naya Kotlin kabhi compile nahi hua: 13D ke baad pehli baar compile me chhoti galtiyan nikal sakti hain, tab theek hongi.

## Jo jaan-boojh kar / honest limits
- Gemini grounding ke link Google ke redirect (vertexaisearch...) hote hain; title domain ka hota hai. Link asli hai par seedha site ka nahi.
- Source asli hone ka matlab ye nahi ki wo dawa poora sach karta hai (limits me likha aata hai).
- Gemini na ho (sirf free vision chain) to search key-less se hoti hai par COMPOSE nahi chalta (Gemini text chahiye): sources list me, dawe unverified, wajah limits me. Free text chain se compose baad ka kaam.
- Key-less sources (Wikipedia / News) sirf general topics ke liye achhe; photo ke bahut khaas dawon par kam milega.
- Live network / real phone par kuch test nahi hua.

# PART 13 ME BACHA HUA KAAM (1 hissa)
## 13D : VOICE / CHAT WIRING + ACCEPTANCE
1. Voice: "nova ye video analyse karo", "is photo me kya hai", "isko check karo ki sach hai ya nahi" -> asli pipeline (MediaIntake.Voice.ANALYSE_NOT_YET hatao). Sabse naya MEDIA MILA item; koi nahi to "pehle video / photo share karo". Sawaal gateQuestion se.
2. KNOWN BUG: chat me photo + sawaal par vision jawab (analysisStart("", question)).
3. Persona analysis jawab par nahi; quiet mode me sirf speechSummary; long job background + STOP.
4. MADAD / SKILLS voice examples, FeatureVerify / FeatureStatus / FeatureVoice, CHANGELOG, plan STATUS, trace rows 15-17, 20-24, 27-28.
5. ACCEPTANCE (pehli baar sab chalega): compile + sab JUnit + tools/run_all_checks.sh, jo fail ho theek karo; owner ke liye Hindi test list (share -> ANALYSE -> report -> offline par honest -> "ye kaun hai" par mana -> chat photo + sawaal).
Agla prompt: NEXT_ACCOUNT_PROMPT_v7_58_PART13D.md
