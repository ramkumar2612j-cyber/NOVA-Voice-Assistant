# NOVA v7.51c : PART 11-C (MEDIA MILA card + bridge) - status (simple Hindi, honest)

## Part 11 progress: 75%
- 11-A DONE rules / 11-B DONE share + cache / 11-C DONE (ye zip) card + buttons
- 11-D BAAKI: MADAD topic + SKILLS entry + voice "nova kya mila" + final status

## Ab app me kya dikhega
1. Chat kholo: bar me naya chhota **MEDIA** button -> system picker -> photo / video chuno (kai ek saath).
2. Kisi app se Share -> NOVA -> NOVA khulta hai, chhota message aata hai, chat me **MEDIA MILA (n)** card dikhta hai.
3. Har file: thumbnail, naam, PHOTO / VIDEO / LINK, size. Buttons: **DEKHO** (photo bada, video phone ke player me), **CHAT ME JODO**, **HATAO**.
4. Upar **MEDIA CHUNO**, **SAAF KARO** (sab turant delete). Copy chale to progress bar + **ROKO**.
5. Link ke neeche: "Video file nahi mili, sirf link ki jankari se kaam karunga."
6. ANALYSE button jaan-boojhkar nahi (Part 13).
7. File sirf phone me, 24 ghante baad apne aap saaf. Koi nayi permission nahi.

## Test status (honest)
- RUN PASS: poora project COMPILE (0 error); MediaIntakeTest 16/16; test_media_card_contract, test_media_store_contract, test_p11_ui_runtime (headless Chromium, nakli bridge, 320 / 360 / 412). Screenshots: docs/ui_shots/p11_media_*.png
- RUN FAIL (sandbox seema): poore JUnit 1162 me 29 fail, sab org.json se jude (Connector / Web / Vercel / Music ...); Part 10 / 11 ka koi nahi.
- NOT RUN (agla account kare): tools/run_all_checks.sh poora (11-B par 111 PASS tha, 11-C ke baad poora nahi chalaya), asli org.json ke saath JUnit, GitHub Actions build, asli phone.

## Agle account ke liye pehla kaam (is order me)
1. tools/run_all_checks.sh -> 0 fail. 2. JUnit asli org.json jar ke saath (JSON_JAR=...). 3. Phir 11-D. Detail: NEXT_ACCOUNT_PROMPT_v7_51c_PART11D.md
