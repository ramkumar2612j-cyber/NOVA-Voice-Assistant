# NOVA v7.51 : PART 11 POORA (media intake) - status (simple Hindi, honest)

## Part 11 progress: 100%
11-A rules, 11-B share + cache, 11-C card + bridge, 11-D voice + MADAD + SKILLS: sab DONE.

## App me kya milega
1. Kisi app se Share -> NOVA: photo / video / link mil jata hai (6 file tak). File sirf phone me, 24 ghante baad saaf.
2. Chat me "MEDIA MILA" card: DEKHO, CHAT ME JODO, HATAO, SAAF KARO. ANALYSE button nahi (Part 13).
3. Bolo "nova kya mila" -> sach status. Bolo "nova ye video analyse karo" -> "video analysis abhi nahi hai" (jhootha daava nahi).
4. MADAD me naya topic "Share karke bhejna (MEDIA)", SKILLS me "Media: Share karke bhejna".
5. Aapke kehne par: chat ke bagal ke teen button (+, files, MEDIA) ab ek hi gol button hain. Dabao to menu: Photo / Video chuno, MEDIA chuno, Connectors aur options. Chat ka size waisa hi hai.
6. Koi nayi permission nahi.

## Test status (honest)
- RUN PASS: poora project COMPILE (kotlinc 1.9.24, 0 error). MediaVoiceTest 5/5 (naya) aur MediaIntakeTest PASS.
- RUN PASS: test_media_voice_contract, test_merged_plus_runtime (headless Chromium, nakli bridge, 320/360/412), test_p11_ui_runtime, test_part5_ui_screens, help + skills contract.
- RUN FAIL (sandbox seema): JUnit 1167 me 29 fail, sab org.json stub wale (Connector / Web / Vercel / Music / FeatureVerify / NovaV73). Asli org.json jar nahi mila (maven 403). Part 11 ka koi fail nahi. Asli saboot GitHub Actions.
- Ek bug mila aur theek kiya: 11-C ka MEDIA button 320px par chat box ko 41px kar raha tha; ab teen button ek hone se theek.
- NOT TESTED: asli phone (Share, card, voice, naya ek button menu), GitHub Actions build.

## Agla: Part 12 (video se frames). Prompt: NEXT_ACCOUNT_PROMPT_v7_51_PART12.md
