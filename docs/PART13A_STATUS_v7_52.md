# NOVA v7.52 : PART 13A POORA (analysis ke RULES) - status (simple Hindi, honest)

## Progress: 13A = 100%   |   Part 13 = 25% (13A / 13B / 13C / 13D)   |   Parts 1-12 + 13A
Part 13 ke 4 hisse: 13A rules (DONE) -> 13B vision + ANALYSE button -> 13C web search + sourced report -> 13D voice / chat + photo-sawaal bug. Har cheez ki list: docs/PART13_TRACE_v7_52.md (32 row).

## 13A me kya bana (app me abhi KUCH NAYA DIKHTA NAHI, jaan-bujh kar)
Naya file AnalysisRules.kt: baaki 13B-13D isi par chalenge.
1. Mana karna (model se pehle): chehre se kisi ka naam, nuksaan wali cheez, OTP / PIN / password / card number, protected screen.
2. Model ke prompt (Hinglish + English) aur jawab sirf strict JSON, bahar ki key ignore, galat type reject.
3. Model URL nahi bana sakta: text me se har URL hata diya jata hai; source sirf asli search result ka NUMBER ho sakta hai.
4. Har dawa ya asli source number se bandha, ya "verify nahi hua". Teesra raasta nahi.
5. Report: summary, kya dikha, likha hua text, verify hue dawe, verify nahi hue, sources, bharosa (low / medium / high + wajah), limits.
6. Offline / key nahi: "Images offline analyse nahi ho sakti" + sirf size / length. Kuch bana ke nahi.
Version 7.52. Koi nayi permission nahi (30 hi), build.yml / ui.html / bridge ko haath nahi lagaya.

## Test status (honest)
- WRITTEN: AnalysisRules.kt, AnalysisRulesTest.kt (45 JUnit test).
- STATIC PASS: tools/run_all_checks.sh = 118 script pass, 0 fail (naya test_analysis_rules_contract.py shamil).
- Python mirror se risky logic (regex, URL, gate, batch, bharosa, size) check PASS, par ye Kotlin ka saboot NAHI.
- NOT RUN: Kotlin COMPILE aur JUnit (is chat me kotlinc + internet nahi tha). GitHub Actions / local_compile_check abhi baaki.
- NOT TESTED: phone. (13A me phone par dikhne wala kuch hai hi nahi.)
- Limits: chehre-naam filter aur phrase lists heuristic hain, guarantee nahi.

## Aapko abhi kya karna hai
Kuch nahi. Chaho to zip GitHub par daal ke build chalao; red ho to sirf laal lines bhejo.
## Agla: 13B. Prompt: NEXT_ACCOUNT_PROMPT_v7_52_PART13B.md
