# NEXT ACCOUNT PROMPT (FULL) - JARVIS v7.181: bacha hua SAB kaam

Zip: JARVIS_v7_181_FULLCHECK.zip (root folder jarvis/). Sabse pehle ye files padho (is order me):
PART_STATUS_v7_181_FULLCHECK.md, PART_STATUS_v7_180b_WIRING.md, PART_STATUS_v7_180_CALLTALK.md, NEXT_ACCOUNT_PROMPT_v7_177_LINK2B_FULL.md, docs/APP_BUILDER_v7_176c.md, TEST_STATUS.md.

## Owner aur compulsory rules
- Owner non-programmer hai, sirf Android phone (Termux F-Droid + Chrome + GitHub web). Jawab Hinglish me, CHHOTE messages (~7 lines), ek step ek time, numbered steps, full clickable URLs.
- ADD-ONLY: purani file/feature mat hatao, sirf zaroorat par chhuo. build.yml mat chhuo. Original source backup rakho.
- Kabhi DONE / WORKING / VERIFIED mat likho bina evidence ke. Level batao: written < statically checked < tests run < CI green < phone tested. Sirf PASS / FAIL / NOT TESTED. Koi fake/demo feature nahi.
- Har part ke baad: poora CUMULATIVE zip (naam me version, purane zip overwrite nahi) + CHANGELOG + status file + agla prompt.
- Push owner karta hai Termux se: repo ramkumar2612j-cyber/JARVIS-Voice-Assistant, local folder ~/nova_clean (sirf main branch). Home folder par kabhi `git add` nahi. Claude ke paas network / GitHub access nahi.
- Owner lambe log nahi padh sakta: build fail ho to sirf failed step ki red lines maango (screenshot theek).

## Ab tak kya ho chuka (v7.180b + v7.181)
- v7.180 CALL TALK: wiring poori (CallAgent ke 7 hooks JarvisService.wireCallAgent me, feed() mic loop me).
- v7.180b: Lead Finder card ke dead buttons (Android.call / openWame / openUrl bridge MainActivity me joda), duplicate #lfdCard hataya, 6 purane version-pin tests (part2/3/4/5/6_site_learning/7_brain_learned) theek (7.178-7.180, versionCode 40), AUTO VERIFY page BRIDGE list me 11 naam jode.
- v7.181 FULL CHECK: FullCheck.kt = ek MERGED report (SelfTest + AUTO VERIFY + FeatureStatus + errors), bridge Android.fullReport(), Settings > TEST REPORT me POORA REPORT + POORA REPORT COPY, voice/chat "kya nahi chal raha / full check / poora report" -> local jawab. tools/test_full_check_contract.py PASS.
- Purane buttons (COPY REPORT, POORI JAANCH ABHI, AUTO VERIFY) add-only rule se rakhe hain.

## Jo NOT TESTED / NOT DONE hai (honest)
1. Kotlin COMPILE nahi hua (sandbox me kotlinc nahi tha): FullCheck.kt, FullCheckRulesTest.kt (6 tests, chale nahi), MainActivity ke naye call/openWame/openUrl/fullReport, JarvisService full-check hook, LaptopLink / CallAgent wiring. GitHub Actions hi judge hai.
2. test_native_smooth_contract.py aur test_perf_governor_contract.py (RUNTIME timing) sandbox me FAIL, original upload par bhi fail. CI par dekho; red ho to asli wajah dhoondo (sandbox ka timing hai ya asli bug).
3. Poori suite aakhri baar dobara poori nahi chali. versionName 7.180 hi hai (7.181 karne se kai version-pin tests tootte; pehle sab pins me naya version jodo, phir bump).
4. Phone par kuch test nahi: Lead card CALL/WHATSAPP/SITE, call-talk (Groq key + dusra number), POORA REPORT + COPY, "jarvis kya nahi chal raha".
5. Purane khule kaam: NEXT_ACCOUNT_PROMPT_v7_177.md aur v7_175 (kinetic UI phone test, JARVIS awaaz Ultron default OFF test, Instagram filler, match live, Instagram DM, Video Studio, Payment QR).
6. Laptop app (desktop/): Electron exe build aur asli ntfy.sh relay par phone+laptop end-to-end NOT TESTED.

## Agla kaam (is order me, ek step ek time)
### Step 1 - CI result
Owner se pucho: "GitHub > Actions > JARVIS Android CI green hai ya red?" Red ho to sirf failed step ki red lines. Pehle shak: FullCheck.kt (Triple destructuring, JSONObject), MainActivity naye methods, JarvisService hook, native_smooth/perf_governor tests. Green ho to APK artifact `jarvis-apk` owner install kare.

### Step 2 - Owner phone test (ek ek, natija owner batayega; TEST_STATUS me tabhi likho)
a) Settings > TEST REPORT > POORA REPORT, phir COPY. b) Bolo "jarvis kya nahi chal raha". c) Lead card: CALL / WHATSAPP / SITE. d) Call-talk test (Groq key + apna dusra number).

### Step 3 - PART B: JARVIS SELF-UPGRADE (owner ka bada order, abhi NOT BUILT)
Owner ka order (15 Nov 2026): "JARVIS khud build/upgrade kare, infinite try, hum undo kar sakein, JARVIS hamesha hamse puche; ab koi bhi feature JARVIS hi banaye."
Seedha sach (owner ko batao): phone ke andar app apna APK khud nahi badal sakta. Raasta = App Builder wala pipeline (AppBuilder.kt + GitHubPublish.kt): 
 owner se puchhe -> CHANGE REQUEST likhe -> AI patch -> GitHub branch par push -> Actions build -> build red ho to red log padhkar retry -> green par APK download -> owner HAAN -> install.
Design rules:
- Har step par owner ki HAAN (request likhne se pehle, push se pehle, install se pehle). HAAN ke bina kuch push/install nahi.
- "Infinite try" = cap ke saath (default 5 retry, har retry par kharch dikhao; cap ke baad owner se pucho "aur try karun?"). Asli infinite nahi: token/data kharch hota hai.
- UNDO: har upgrade alag branch + tag (jarvis-up-N); undo = pichhla tag wapas + pichhla APK reinstall; undo list UI me. Main branch par seedha kabhi nahi.
- Protected: signing secrets, build.yml, token, DENY list (delete/shutdown/password) ko patch kabhi nahi chhu sakta; patch ke baad checker (tools/run_all_checks.sh) + CI hi judge.
- Honest: AI ka Kotlin patch bina compile ke 300-file app me tootne ka bada chance hai; kuch bhi PASS mat likho bina asli green build ke.
Order: (i) pehle sirf REQUEST LEDGER + undo list + HAAN gate + UI (koi network nahi) + tests; (ii) phir GitHub push + Actions poll + red-log retry loop; (iii) phir install + undo. Har sub-step ke baad zip.
Owner ne ye bhi kaha: "yha se ab koi feature add karwana ho to JARVIS hi kare" - isliye Part B ke baad naye features ka raasta JARVIS ke "feature chahiye" command se chale; JARVIS clarifying sawal puchhe.

### Step 4 - Baaki
Purane khule kaam (upar #5, #6), phir versionName bump (pehle saare version-pin tests me naya version jodo), phir poora regression + final cumulative zip + GitHub-ready README.

## UPDATE v7.182 (is zip me): Part B (i) ho chuka
UpgradeLedger.kt + UI box JARVIS UPGRADES + voice ("jarvis naya feature: ...", "upgrade haan/nahi/list") + tools/test_upgrade_ledger_contract.py + UpgradeRulesTest.kt (NOT RUN). Details: PART_STATUS_v7_182_UPGRADE_LEDGER.md.
Ab Step 3 ke (ii) se aage: GitHub push + Actions poll + red-log retry loop (cap 5) + HAAN; phir (iii) install + real undo. Kotlin compile abhi bhi GitHub CI se hi hoga.

## UPDATE v7.183: SelfKnowledge.kt (JARVIS apne baare me) bhi bana. Details: PART_STATUS_v7_183_SELF_KNOWLEDGE.md
Compile na hue naye Kotlin: FullCheck.kt, UpgradeLedger.kt, SelfKnowledge.kt (+ 3 unit test files, NOT RUN). PEHLA kaam: GitHub Actions result. Red ho to sirf failed step ki lines.
Owner ne bypass maanga tha: kaam ke seedhe raaste (intent/accessibility/shortcut/App Builder) bejhijhak; phone lock/OTP/password/captcha/service security todna NAHI (owner ko ye saaf bataya gaya). Isko mat badlo.
Part B (ii)/(iii) tabhi shuru karo jab CI green ho.
