# PART STATUS v7.213 - CARE (security / hang / faltu)

## Kya bana (add-only)
1. `CareRules.kt` (pure Kotlin): bolne wale vakya ko 4 type mein todta hai: SECURITY, HANG, CLEAN, DELETE. Screen scan / reel scan ko security scan nahi samajhta.
2. `JarvisService.handle()`: CareRules ka hook memory check ke baad, recording rules se pehle. Jawab real numbers se (free RAM, free storage) ya honest text.
3. `CareRulesTest.kt`: 6 unit tests (compile aur run nahi hue).
4. `HelpGuide.kt`: naya topic "care" (9 arguments, format check kiya). "Jarvis kya kya kar sakta hai" mein ye bhi aayega.

## Jo JARVIS NAHI karta (jaan-bujh kar)
- Poora virus scan: Android apps ko poora phone scan karne ki permission nahi hoti. Ye claim fake hota.
- Awaaz se file ya app delete: project ka hard-no rule hai. JARVIS sirf Settings > Storage kholne ka option deta hai.
- Hang ka asli karan batana: sirf RAM aur storage ke numbers; kaunsa app bhaari hai ye Android se nahi dikhta.

## Honest
- Kotlin COMPILE NAHI hua; unit tests NOT RUN. Brace/bracket balance (568 files) OK.
- Phone par NOT RUN.
- "Jo bole wahi kare" aur "har baat samjhe" abhi AI brain (keys) par tika hai; bina keys ke sirf local rules.
- Action ke dauran sunna (barge-in) is version mein nahi badla.

## Agla
GitHub Actions build -> error paste karo -> phone par "security scan karo", "phone hang kyu kar raha hai", "faltu cheeze delete karo" bolkar dekho -> TEST_STATUS.md mein PASS/FAIL.
