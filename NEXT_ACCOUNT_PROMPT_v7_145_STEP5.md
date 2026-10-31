# NOVA HANDOVER PROMPT (v7.145 STEP 5 of 10, 2026-10-30 ~11:30 IST) - FULL, not a delta

## 0. Pehle ye padho
Owner Hinglish me baat karta hai. Reply Hinglish me. Ek time par ek hi step, chhote messages (phone screen ~7 lines), full clickable URLs. DONE / WORKING / VERIFIED tab tak mat likho jab tak saboot (log, screenshot, output) na ho. Jo test nahi hua use saaf "NOT TESTED" likho. Owner khud code ya Gradle edit nahi karta. Har step ke baad owner ko ZIP (poora source) + yeh next-account prompt (poori detail ke saath) dena COMPULSORY hai (owner ka order, 30 Oct 03:24 IST).

## 1. Project
- NOVA = Android voice assistant (Kotlin, package com.nova.assistant). Offline Vosk wake word, optional Gemini/Groq, Porcupine (optional), llama.cpp offline engine, Jarvis voice (JarvisVoiceApi/Client/Speaker/LocalVoice), WebView UI (assets/ui.html), native floating card NovaHud (canvas), Accessibility service for control of other apps.
- Owner sirf Android phone se kaam karta hai (Chrome + GitHub web + Termux). PC nahi. Compile sirf GitHub Actions ("Build and Test APK", artifact nova-apk, + smoke job artifact auto-test-report).
- Repo: https://github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant , sirf branch main. Purane repos me push mat karo.
- versionName abhi bhi 7.138 (commit naam v7.14x hain, gradle bump nahi hua). versionCode = 2000 + run number (build.yml khud). build.yml badlo to tools/test_part5_contract.py me uska sha256 update karo (pinned 3f33c02ed799cf09f274f04b8b589c35f9a9f2f49313fc850288a61c53c4a009; build.yml abhi NAHI badli).
- GOTCHA: CHANGELOG.md ke upar ka entry `## v7.13x` hi rakho. tools/test_part20_contract.py `## v7.14x` accept nahi karta. Isliye Step 1-5 ne CHANGELOG nahi chhua; notes alag status file me jate hain (STEP5_STATUS_v7_145.md).
- Owner ka Termux: kaam wala clone ~/n139. /tmp likhne layak nahi. Chat se bheji files ~/storage/downloads me aksar NAHI milti. Code PATCH ke roop me do. LAMBA patch (>~8 KB) seedha paste karne par beech me kharab ho jata hai (30 Oct ka garbled paste). Isliye lamba patch gzip + base64 karke do, jaise: `gzip -9 -c x.patch | base64 -w 76`, owner me: `cat > ~/x.b64 <<'EOF' ... EOF`, phir `base64 -d ~/x.b64 | gunzip > ~/x.patch && sha256sum ~/x.patch` (hash match dikhao), phir `git apply --check --ignore-whitespace ~/x.patch && git apply --ignore-whitespace ~/x.patch && git status --short`, phir `git add -A && git commit -m ... && git push origin main`. Patch hamesha GitHub ke latest clone par `git apply --check` se test karke do.

## 2. Owner ke pakke rules
- Risky action ke liye spoken local "yes". Silent install nahi. API keys kabhi log/repo me nahi. NOVA apna code khud nahi badlega.
- Add-only: purana kaam karne wala code zaroori na ho to mat hatao.
- Wake sirf "Nova" word par (strict) ya real alert par. Popup card me type karne ka option. Har phone feature/setting voice se control ho. "App ON hona" (ACTIVATE tap) sabse bada bug.
- Jarvis jaisa free own brain: kaam samjhe, kare, internet se seekhe, coding/website bana de. Natije chahiye, bahane nahi.
- NOVA phone ka koi bhi kaam kare, kisi bhi khule app ke andar ka bhi, bolne par. Thinking 2-3 s, kaam turant.
- English jawab Jarvis awaaz me, Hindi jawab Hindi awaaz me, AUTO (ReplyLang.kt).
- Safety jo kabhi nahi tootti: payment/bank/password/OTP/Settings/installer par tap nahi; secure (FLAG_SECURE) screen ka screenshot nahi; chehre se kisi insaan ka naam nahi (celebrity sirf screen par likhe text/title/caption se); game khelna nahi, sirf dekhna/batana.

## 3. Owner ka bada order (30 Oct 03:21-03:24 IST)
"App ke saare features dekho, upgrade karo, jitna zyada mobile ko access de sako, perfect voice assistant: har app use kare, app ke andar bhi bolne se kaam kare, screen analyse kare (movie, web series, game, celebrity). Ye sab 10 steps me add karo. Har step ke baad zip dena compulsory, full 10 steps aur NOVA ki full details ke sath next account prompt."

### 10-step plan (status)
1. DONE-CODE (v7.141, GitHub par, phone NOT TESTED): Kaam ka hisaab. AgentScore.kt + FeatureVoice "agentscore" ("nova kaam ka hisaab batao").
2. DONE-CODE (v7.142): Seedha raasta. DirectRoute: YouTube search / Maps route ek hi step me link se (NovaService ~2855, directRouteRun ~4040); anjaan phrase ya fail par agent chalta hai.
3. DONE-CODE (v7.143): Seekhe hue raaste. LearnedRouteRules/LearnedRouteStore: sirf VERIFIED, saaf, bina typing wale agent kaam ke harmless steps (open_app/tap/scroll/back) hash-key se yaad; wahi kaam dobara bolne par bina AI plan replay; 2 fail par bhool jata hai.
4. DONE-CODE (v7.144): Vision tap. VisionTapRules: kamzor Accessibility screen (<=2 clickable) par agent {"act":"tap_xy",x,y} (per-mille), game me kabhi nahi, edge se door, max 5 tap/job, AgentRules.check se guzarta hai; vision-tap wala kaam kabhi learn nahi hota.
5. DONE-CODE (v7.145 + v7.146 UI wiring, IS ZIP ME, push owner karega): Screen pehchan GAME / VIDEO.
   - ScreenIdRules.kt (pure): parse("ye kaun sa game hai" / "ye video ka naam batao", Devanagari bhi) -> Kind.GAME / VIDEO; movie/series ab bhi MovieScanRules (pehle) ke paas. Recommendation / khelo / download / hack / people ("kaun hai", player, streamer) / shared file / bina "ye"-word wale sentence match nahi hote.
   - GAME shortcut: NovaService.startScreenId: protected screen -> privateText; agar khule app ko Android khud game maane (ApplicationInfo.category == CATEGORY_GAME ya FLAG_IS_GAME) to app ka naam turant jawab (HIGH, bina AI / internet / wait). NOVA khelta nahi.
   - Baaki (video, ya game jo Android game na maane): purana startMovieScan(kind) = capture + vision + web search; MovieGuessPipeline.run(..., kind = null default => movie scan bilkul purana). App ka naam + window title (NovaAccessibilityService.otherWindowTitle) "APP: / WINDOW:" lines ke roop me screen text se pehle prompt me jaate hain. Confidence rule wahi: HIGH = naam screen par likha + 2 web source; web result na ho to LOW = "verify nahi hua". Chehre se naam kabhi nahi (parseVision drop karta hai).
   - Saboot: ScreenIdRules + ScreenIdRulesTest (12) asli kotlinc 2.0.21 se compile + chale, 12/12 PASS. NovaService/NovaAccessibilityService/MovieGuessPipeline: sirf syntax-level kotlinc check (95 error kinds, baseline se bilkul same, koi naya nahi). 105 related contract scripts PASS. Android compile = GitHub build (NOT CONFIRMED). Phone NOT TESTED.
6. DONE-CODE (v7.146, NOT COMPILED, phone NOT TESTED): Atakna watchdog. MainStallWatchdog.kt + StallRules.kt + StallRulesTest; voice "nova atakne ka hisaab batao"; MADAD/Skills entry. Details + honest audit: STEP6_STATUS_v7_146.md. Agla: GitHub build green hone ke baad owner ke phone ka asli atakna-hisaab dekho aur jo frame naam aaye sirf wahi theek karo.
7. DONE-CODE (v7.147, NOT COMPILED, phone NOT TESTED): KYA BAND HAI. BlockedRules.kt + BlockedSnapshot.kt + bridge getBlocked/fixBlocked + Home card + voice "nova kya band hai batao" + MADAD/Skills. Details: STEP7_STATUS_v7_147.md.
8. TODO: Watch se bolke kaam (WatchCommandActivity / watchmic): owner ne kaha kaam nahi kar raha; root cause NAHI mila. Pehle owner se watch model aur kya hota hai pucho.
9. TODO: Modern popup (NovaHud, canvas card) effects ke saath, par halke (GPU glitch wala phone). Pehle phone stable hone ka saboot.
10. TODO: Final audit, tools/emulator_smoke.sh patch (ANR reason + native crash "Fatal signal"; owner ne push nahi kiya), owner phone test list, aakhri full next-account prompt.

## 4. Is session me kya hua (saboot ke saath)
1. ANR fix (initTts worker thread "nova-tts-init"), Instagram "phone ne roka" fix (waitFront + 30x150ms), ReplyLang auto voice: sab push hue; emulator smoke 7/7 PASS (29 Oct 20:32 UTC); phone par NOT TESTED.
2. Owner ka phone bug (naye install ke baad atakna / rang-birange tukde): v7.139 (safe graphics, voice cache) aur v7.140 (website photos) ke baad owner ne kuch nahi bataya. Pehla shak: uninstall+install ke baad Vosk model APK se dobara copy ("Pehli baar model tayyar ho raha hai", 1-2 min). 2-3 min ruk kar dobara kholne ko bola tha, jawab NAHI aaya. Phone model abhi tak NAHI pata.
3. Step 5 (upar). Mene is session me sirf Step 5 banaya.
4. test_part20_contract ne CHANGELOG me `## v7.145` entry par fail kiya; entry hata kar STEP5_STATUS_v7_145.md me rakhi. Pass.

## 5. Owner ke pending (phone par, abhi tak NOT REPORTED)
(a) Instagram test ("Nova Instagram kholo": app khule, galat "phone ne roka" na aaye), English Jarvis awaaz, Hindi awaaz. (b) "Samajh nahi aaya, dobara bolo" root cause: owner se chat ki "🎤 <text>" line ka screenshot + Settings me Groq/Gemini key hai ya nahi. (c) "thinking der tak": "nova feature status" ki "Samajhne ki speed" row ka screenshot (UnderstandBudget 4.5 s rule pehle se hai; END_SILENCE_MS 1300). (d) 2-3 asli failing example. (e) Step 5 phone test: koi game kholo -> "nova ye kaun sa game hai"; koi video / reel -> "nova ye video ka naam batao". (f) Settings me "Awaaz ki bhasha: Auto" line (sirf owner bole to).

## 6. Code ke nishan
- NovaService.kt: initTts (~605), speak (~641), startJarvisLocal (~787), waitFront (~3968), launch (~3996), MovieScan route (~2631) + ScreenIdRules route (turant uske baad), startScreenId / isGameApp / appLabelOf / startMovieScan(kind) (~7140-7230), handle (~2504), process (~1667), audioLoop (~1195), agent (agentRun, DirectRoute ~2855, learned routes ~4950/5412, vision tap ~5315).
- NovaAccessibilityService.kt: agentSnapshot, agentTap/agentType, agentScreenshot, gestureTap, reelPackage (NOVA ki apni window skip), movieProtected, movieShot, readScreen, otherWindowTitle (NAYA).
- MovieGuessRules.kt / MovieGuessPipeline.kt / MovieScanRules.kt / MovieFrameRules.kt / QuickScan.kt / FreeVision.kt / LastMovieRules.kt = movie scan; ScreenIdRules.kt (NAYA) = game / video.
- AgentRules.kt (MAX_STEPS 60, check() DENY), AgentTaskLedger.kt, AgentScore.kt, DirectRoute.kt, LearnedRouteRules/Store.kt, VisionTapRules.kt.
- Cfg.kt (applyVoice, voiceKey), ReplyLang.kt, JarvisVoiceClient.kt, UnderstandBudget.kt, FeatureStatus.kt.

## 7. Sandbox tools (mere nateeje)
- Repo public: `git clone https://github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant.git` sandbox me chalta hai (read-only). add_repo / gh run download fail (GitHub link nahi); artifact owner se zip upload karwao.
- kotlinc 2.0.21: `curl -L -o kc.zip https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip` (~86 MB). Sirf pure-Kotlin files compile hoti hain. Trick: MiniJson object FreeProviders.kt me hai (poori file Android deps kheenchti hai); `awk` se sirf `object MiniJson` nikaal kar alag file me compile karo, saath me AnalysisRules.kt, AnalysisPipelineRules.kt, AnalysisStage.kt, MovieGuessRules.kt.
- JUnit nahi hai: chhota shim (org.junit.Test annotation + Assert.assertEquals/True/False/Null) aur reflection runner likho, wahi test class chalao.
- Android files (NovaService etc.) ka syntax check: `kotlinc -nowarn <files> -d out 2>&1 | grep error: | grep -v "unresolved reference"` ko baseline (latest clone) aur naye version par chalao; distinct error kinds same hone chahiye. Type-correctness ka saboot nahi, bas naye syntax error nahi.
- Contract scripts tools/test_*.py (160): related wale `xargs -P 8` se background me (timeout 170). Sab ek saath 10 min+. test_part4_fx_render sandbox me fail (playwright), GitHub par pass.
- Logcat me ANR: grep "ANR in com.nova.assistant", "Long monitor contention", "Skipped .* frames", "Fatal signal".

## 8. Baaki purani backlog
TEST_STATUS.md (owner ke phone ke 19 tests, sab NOT REPORTED). 3-part audit plan Part 3 baaki. Purani: confirm UI/chat/animations, "not speaking" bug, pattern unlock test, scheduled actions, speaker allow-list, bottom-tab layout, HealthCheck error log, AI image generate/photo edit (Gemini key), screen-reading fix, Settings self test. JarvisVoiceApiTest ka client-vs-local-server test hata hua hai (ServerSocket se dobara likh sakte ho). Signing secrets na ho to throwaway debug key: update "app not installed" de to uninstall karke install. Actions "Node.js 20 deprecated" notice: abhi mat chhedo (build.yml badli to hash update).

## 9. v7.146 add-on (Step 1-5 UI wiring)
Steps 1-5 ab MADAD help list (64 topics, HelpGuide.kt = ui.html) aur Skills screen me bhi hain: agentscore, screenid, smartroute. Contract scripts 159 PASS. Android compile (GitHub build) aur phone test abhi bhi NOT TESTED. Owner ko zip diya: NOVA-v7_146-STEP1-5.zip (poora source). Step 5 ab tak GitHub par push nahi hua; owner ne lamba paste fail hone ki wajah se zip maanga hai, patch nahi. Agla kaam: owner se push/upload ka tareeka ek-ek step me, phir Step 6.

## 10. v7.146 Step 6
Zip: NOVA-v7_146-STEP1-6.zip. Step 1-6 code zip me, GitHub par sirf Step 1-4 (v7.144) hain. Step 5 + 6 push baaki. kotlinc is baar sandbox me nahi tha (network band) isliye naya Kotlin compile NAHI hua; pehle GitHub build dekho.

## 11. v7.147 Step 7
Zip: NOVA-v7_147-STEP1-7.zip. Code Step 1-7 in zip; GitHub par sirf Step 1-4 (v7.144). Step 5, 6, 7 push baaki. Naya Kotlin compile NAHI hua: pehle GitHub build dekho, error aaye to wahi theek karo. Agla: Step 8 (watch mic: pehle owner se watch model aur kya hota hai pucho).
