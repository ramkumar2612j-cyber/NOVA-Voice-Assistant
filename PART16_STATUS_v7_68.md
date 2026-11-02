# PART 16 MISSING WIRES - status (v7.68)
Evidence: RUN = executed here; NOT RUN = could not run here (no kotlinc, no internet); NOT TESTED = needs the real phone.

## Mila aur theek kiya
1. **ANSWER_PHONE_CALLS kabhi maangi hi nahi jaati thi** (manifest me thi, PhoneActions check karta tha, par MainActivity ne kabhi request nahi ki). Matlab "call uthao" hamesha "no_permission" deta. Ab pehli permission round me maangi jaati hai; SetupChainRules.VERSION 1 -> 2 taaki purane phones ko bhi ek round aur mile. (RUN: static contract. NOT TESTED: phone.)
2. FeatureStatus me nayi row "callanswer" (Call uthana / kaatna).
3. MADAD topics "callanswer" aur "calendar" (feature pehle se tha, help text nahi tha). PRIORITY me bhi, taaki purana "call" topic unhe dhak na de. 47 topics, Kotlin == ui.html (RUN: test_help_contract PASS).
4. Naya `tools/audit_dead_ui.py`: dead control / unused bridge / help topic / retired-rule leftover. Result: 0 real gap. Mutation check (nakli dead checkbox) PASS (RUN).
5. `tools/test_part16_contract.py` (RUN, mutation check PASS) + `HelpGuidePart16Test.kt` (4 JUnit, NOT RUN).

## Jaanch ke baad koi badlav nahi chahiye tha
nc_on (v7.50 me wired), SocialReplyRules (Kotlin / ui.html me koi leftover nahi), docs/pending_G (dono file live code se byte-identical), OTP / password / PIN / CVV rok (SmartReplyRules), overlay nudge 10 min repeat, recording writer error (RecordSession.Saved.writerError). docs/retired_guards dobara chalaye: jo FAIL hain wo doosri code-line ki exact strings hain, feature khud maujood hai.

## JAAN-BOOJHKAR NAHI BADLA (owner faisla chahiye)
- **Voice command jab NOVA busy ho queue me nahi jaata** (sirf typed jaata hai). Wajah: mic loop busy / bolte waqt audio fenk deta hai (apni awaaz ka echo rokne ke liye). Isse badalna compile + phone test maangta hai; is account me kotlinc nahi tha.
- **wb_ll (locked phone par wake word)**: Lock Guard se takrata hai, owner ka faisla.

## NOT RUN
Kotlin compile, JUnit (naya HelpGuidePart16Test + purane), GitHub Actions, phone. Version-pinned 4 guards add-only 7.68 maanne lage.

## Phone test (owner, sirf lal lines bheje)
1. App update karo, kholo: permission round me "Phone calls answer" wali permission aani chahiye (ek baar).
2. Kisi doosre phone se call karwao, bolo "nova call uthao" / "call kaato".
3. SAARE FEATURES / status me "Call uthana / kaatna" row OK dikhe.
4. "kya tum call utha sakte ho" -> naya MADAD jawab.
