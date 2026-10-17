NOVA Android app (zip: NOVA-WIP-v75-PART1-AUTOPSY.zip). Part 1 (autopsy) DONE: AUTOPSY_PART1.md padho.
Owner Hinglish bolta hai, sirf phone se kaam karta hai, non-technical: jawab chhota, simple Hindi.

RULES (hamesha): purana kuch mat hatao / disable mat karo / replace mat karo (feature, screen, setting, API, fallback, permission). Package name aur structure wahi. Koi demo/mock/fake code nahi. Fake success claim nahi. Har part ke baad: poora ZIP + download link, build/test status simple Hindi mein, agle part ka NEXT_ACCOUNT_PROMPT_PARTn.md.

5 PARTS: 1 Autopsy (DONE), 2 Skills list + missing poora karna, 3 Popup STOP button + Silent switch + messages screen par, 4 Modern UI redesign, 5 Build + test + final ZIP.

PART 2 KAAM:
1) AUTOPSY_PART1.md ke section B ki 15 cheezein: har ek ko ui.html CAPS (MADAD), HelpGuide.TOPICS aur aiFacts mein jodo. tools/test_help_contract.py Kotlin aur popup ko equal rakhta hai, use bhi update karo.
2) Har feature ke liye saabit karo: asli code chalta hai, NovaService/MainActivity se juda hai, skills list mein dikhta hai, NOVA ka AI jaanta hai. Jo adhura mile use poora karo (purana mat todo).
3) Naye HelpGuide topics ke saath HelpGuideTest.kt aur python contract update.
4) tools/run_all_checks.sh chalao (abhi 59/59 pass). Internet ho to kotlinc compile + JUnit, warna saaf likho ki compile GitHub Actions par baaki hai.
5) Delivery: ZIP + link, Hindi status, NEXT_ACCOUNT_PROMPT_PART3.md (Part 3: popup STOP button, Silent switch, messages dikhna).
