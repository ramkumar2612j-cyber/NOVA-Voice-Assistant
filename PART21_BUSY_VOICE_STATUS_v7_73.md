# v7.73 - VOICE WHILE BUSY - status
Evidence words: RUN = sandbox me chalaya. NOT RUN = nahi chalaya. NOT TESTED = asli phone chahiye.

## Owner faisle (19 Oct 2026)
1. Locked phone par wake word / command = **NAHI**. Lock Guard bilkul waisa hi hai.
2. NOVA busy ho tab bhi bola hua command yaad rakhe (queue) = **HAAN**. Ye kiya.

## Kya badla (add-only)
- BusyListenRules.kt (naya), ExtCfg.busyListen (naya), NovaService mic loop me 2 jagah (busy-listen check; capture khatam hone par queue / stop).
- Naye tests: BusyListenRulesTest.kt (JUnit), tools/test_busy_listen_contract.py. versionName 7.73.

## Kaise kaam karta hai
- NOVA kaam kar raha hai par bol nahi raha -> wake word sunta hai -> "nova ..." bolo -> command queue me (max 10) -> abhi wala khatam, phir agla.
- NOVA bol raha hai -> pehle jaisa: audio fenka jaata hai (echo se bachne ke liye). Isliye bolna khatam hone ka intezaar karo ya queue me typed daalo.
- "ruko / stop" busy me bhi kaam karta hai.
- Locked phone = kuch nahi sunta (pehle jaisa).

## RESULT
- RUN: python contract scripts (neeche run_all_checks ka count dekho).
- NOT RUN: Kotlin compile + JUnit v7.73 (sandbox me kotlinc nahi). Naya Kotlin code haath se likha hai, isliye Actions me compile error aa sakta hai; aaye to sirf laal text bhejo.
- NOT RUN: GitHub Actions APK build. NOT TESTED: phone.
