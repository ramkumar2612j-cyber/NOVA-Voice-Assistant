# NOVA v7.51b : PART 11-B (share + store) - status (simple Hindi, honest)

## Part 11 progress: 50%
- 11-A DONE (rules, 16 JUnit PASS)
- 11-B DONE (ye zip): Share -> NOVA + private cache copy + 24 ghante saaf
- 11-C baaki: bridge (mediaIntakeList / mediaIntakeRemove / mediaIntakeClear / mediaPick) + chat "MEDIA MILA" card (DEKHO / CHAT ME JODO / HATAO)
- 11-D baaki: MADAD + SKILLS + CAPS + SCR, voice "kya mila", contract + headless test

## Kya juda (add-only)
1. Kisi bhi app (Gallery, WhatsApp, Instagram, Chrome) me Share -> NOVA dikhega (photo, video, link). Ek baar me 6 file tak.
2. File app ke private cache me aati hai (bina gallery permission, bina internet), bade video bhi tukdon me copy hote hain (memory nahi bharti). Limit: photo 25 MB, video 300 MB; zyada ho to saaf karan ke saath mana.
3. 24 ghante baad apne aap saaf. Link sirf link ki tarah rakha jata hai (video download nahi hota).
4. Abhi screen par sirf ek chhota Toast aata hai ("2 file mili hai."). Asli card + buttons 11-C me.
5. Part 10 ka ek bug theek: "usi ko dobara call karo kal" ab repeat nahi maana jata.
6. Koi nayi permission nahi (30 hi).

## Test status (honest)
- RUN PASS: poora project kotlinc se COMPILE (0 error); MediaIntakeTest 16/16; run_all_checks (111 scripts); test_media_store_contract.py.
- RUN FAIL (sandbox seema): poore JUnit 1162 me 29 fail, sab org.json se jude tests (ConnectorParse / WebSources / WebBrowsers / VercelPublish / Music / Notion ...); sandbox me asli org.json jar nahi mila. GitHub Actions me asli jar hota hai: wahi final saboot.
- NOT RUN: asli phone par Share karke dekhna, GitHub Actions.
