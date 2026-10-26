# NOVA v7.119 - Part A (lite series: v7.117 wake gate + v7.118 chat paint made testable)

Base: zip NOVA-v7_118 (CHANGELOG v7.118; build.gradle versionName still 7.114 on purpose, old contract tests pin it).

## Kya kiya
1. v7.117 wake gate (Vosk sirf awaaz ke time chale) ka logic NovaService se nikal kar `WakeGateRules.kt` (pure Kotlin) me rakha. Behaviour same: voice = rms > max(300, noise*1.8), hold 2 s, pre-roll replay sirf gate khulte waqt, reset sirf gate band hone par.
2. NovaService me inline numbers hata kar WakeGateRules ke 5 calls (voice / isOpen / extend / replayCount / closing).
3. NEW tests: `WakeGateRulesTest` (8 JUnit, ek simulated sentence ke saath) + `tools/test_v7119_gate_contract.py` (static; v7.118 content-visibility bhi check karta hai).

## Evidence level
- written: YES
- statically checked: `tools/test_v7119_gate_contract.py` RUN, sab PASS. JUnit ke expected numbers (replay 4, reset 1, fed 22) haath se / Python me ginti kiye.
- Kotlin compile: NOT RUN (is sandbox me kotlinc aur internet nahi). v7.117 ka gate abhi tak kabhi compile nahi hua.
- JUnit: NOT RUN. GitHub Actions: NOT RUN. Phone: NOT RUN.

## Jo badla nahi
versionName, thresholds, Logic.wakeVerified, WakeGuardRules, WakeAdapt, ui.html.

## Phone par kya dekhna (Part A)
- "Nova" bolne par pehle jaisa hi jaagna chahiye (pehla akshar kata nahi).
- Phone kam garam / battery kam khaye (ye andaaza hai, naapa nahi).

## Final numbers (RUN, sandbox)
- run_all_checks.sh: 159 passed, 1 failed. Fail = `test_part4_fx_render.py` (headless page load timeout). Wahi test untouched original zip par bhi fail hota hai, to mere badlav se nahi.
- test_v7119_gate_contract.py: 11/11 PASS.
