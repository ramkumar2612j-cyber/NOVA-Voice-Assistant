NOVA v7.72 zip (repo skfashion142/NOVA-Voice-Assistant). PART 1-20 SAB LIKHE ja chuke hain (20-part plan khatam). Ab koi naya "Part 21" nahi hai: ab kaam = owner ke PHONE / ACTIONS result ke hisaab se FIX. Padho: PART20_STATUS_v7_72.md, OWNER_PHONE_TEST_LIST_v7_72.md, NOVA_20_PARTS_PLAN.md, CHANGELOG.md (upar ke 3-4 entries).

## Rules (owner ke, kabhi mat todna)
- Add-only: purani line kabhi mat hatao. Hinglish. Har baat honest: **RUN / NOT RUN / NOT TESTED** alag likho. Jo nahi chalaya use "PASS" mat likho.
- Har naye version par 4 version-pinned guards me versionName add karo: tools/test_analysis_rules_contract.py, test_analysis_voice_contract.py, test_search_compose_contract.py, test_android_runtime_contract.py (aur tools/test_part20_contract.py me 7.72 wali line ko naye version ke saath add-only badlo).
- Owner sirf phone se kaam karta hai (Chrome + GitHub, PC nahi). Ek step ek baar, exact github.com link ke saath. Owner non-technical hai, uska client JARVIS jaisa voice assistant chahta hai (hath disabled hain, haath se phone nahi chala sakta) => hands-free kaam sabse zaroori.
- Progress har 5% par batao, 95% par ruko mat.

## Pehle kya karna hai (is order me)
1. Owner ne kya bheja hai dekho:
   a) GitHub Actions ka red (laal) text -> sirf wahi compile / Gradle error theek karo. Link: https://github.com/skfashion142/NOVA-Voice-Assistant/actions
   b) OWNER_PHONE_TEST_LIST_v7_72.md ke FAIL items (PASS / FAIL + screenshot) -> har FAIL ko root cause tak dekho, chhota fix, naya contract / JUnit test.
   c) Owner ke 2 PENDING faisle (neeche).
2. Kuch bhi na aaya ho to **kuch nayi feature mat banao**. Sirf poochho: "APK ban gayi? Phone test list (OWNER_PHONE_TEST_LIST_v7_72.md) chalayi?" aur ek hi sawaal.
3. Compile / tests: `bash tools/local_compile_check.sh /tmp/nova_check` (background; real org.json khud banta hai; internet chahiye: kotlinc 1.9.24 + android-34 android.jar. raw.githubusercontent.com blocked ho to Reginer/aosp-android-jar ko sparse git clone karo, android-34/android.jar). `bash tools/run_all_checks.sh` (background ~7 min; setsid nohup se chalao taaki tool-call khatam hone par band na ho). Part 20 me sandbox me kotlinc + internet NAHI tha, isliye Kotlin compile / JUnit v7.72 par NOT RUN hai (v7.71 par 1352/1352 PASS tha; v7.72 me Kotlin code NAHI badla, sirf versionName + docs + 1 naya Python test).

## Owner faisle PENDING (BINA POOCHHE MAT BADLO)
1. **Voice command jab NOVA busy ho (bol raha ho / kaam kar raha ho) queue me nahi jaata**, sirf typed jaata hai. Wajah: mic loop busy / speaking me audio fenk deta hai (apni awaaz ka echo rokne ke liye). Queue banana = mic loop + echo logic chhedna = compile + phone test zaroori. Pehle owner se poochho: "chahiye ya nahi?"
2. **wb_ll = locked phone par wake word**: Lock Guard se takrata hai. Owner se poochho. Haan bole to risk samjhao (lock par mic sunna) aur phir hi karo.

## Jaane hue honest limits
- Phone par kuch TEST NAHI hua (Parts 5-19 ke sab owner checkpoints). GitHub Actions APK build NOT RUN.
- Parts 7-10 ka Kotlin pehle bina compile ke likha gaya tha (unke status files me likha hai); baad me v7.61 aur Part 19 (v7.71) me poora compile + JUnit PASS aaya (sandbox, stubs ke saath). Asli Gradle build alag baat hai.
- NOVA chehre se naam nahi batata (jaan-boojhkar, AnalysisRules). Instagram jaise apps screen capture rok sakte hain => "screen padh nahi paya".
- MemoryScreenOpsTest.kt me redaction test ke liye key jaise nakli strings hain (AIza..., sk-..., ghp_..., gsk_...). Ye test-data lagta hai, asli key hone ka saboot nahi; agar owner ki asli key hai to usse rotate karwao.
- Repo naam: purani docs me ramkumar2612j-cyber/NOVA-Voice-Assistant likha hai, naye prompts me skfashion142/NOVA-Voice-Assistant. Owner se confirm karo ki asli kaun sa hai, bina poochhe mat badlo.

## Naya Part banana pade to
Pehle NOVA_20_PARTS_PLAN.md me add-only ek "UPDATE" table jodo (kaam, done kab = kaunsa test), owner ko dikhao, haan milne par hi shuru karo. Har part ke baad: 1 zip + 1 status + 1 prompt.

## UPDATE v7.73 (19 Oct 2026, add-only)
- Owner ne 2 PENDING faisle de diye: (1) busy NOVA par voice command queue = HAAN -> v7.73 me bana (BusyListenRules.kt, PART21_BUSY_VOICE_STATUS_v7_73.md). (2) locked phone par wake word = NAHI -> kuch mat badlo, Lock Guard waisa hi rahe.
- v7.73 ka Kotlin NOT RUN (kotlinc nahi tha). Pehla kaam: GitHub Actions ka red text aaye to wahi compile error theek karo.
