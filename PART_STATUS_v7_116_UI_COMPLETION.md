# NOVA v7.116 - UI completion + feature check

Base: v7.115 zip. Add-only except two small fixes (below). versionName stays 7.114 (old contract tests pin it).

## Kya check hua (RUN = sandbox me chalaya)
- RUN: asli Kotlin compile (kotlinc 1.9.24 + android-34 jar, `tools/local_compile_check.sh`) - v7.115 pehli baar compile hua: 0 errors, JUnit 1590/1590.
- RUN: headless chromium me har view (HOME, SKILLS, LIMITS, CONNECT, CHAT, SETUP) aur Settings ka har button / switch dabaya (stub bridge): 0 JS errors.
- RUN: ui.html ka har `Android.x` call kisi asli bridge method se match karta hai (216 naam, 0 missing).
- RUN: `tools/audit_dead_ui.py`: 0 dead control, 0 help-topic gap.

## Jo asli problem mili aur theek hui
1. UI bug: HOME ka PROFILE SEARCH chip icon table me tha hi nahi -> `<path d="undefined">` har start par console error. Icon jodha + fallback.
2. Race: reel run start check-then-set tha (friend-watch thread + voice command ek saath chal sakte the). Ab `reelLock`.
3. Gap: 4 switch sirf code me the, UI me koi control nahi: busy-listen, aur "haan" poochna (risky tap / aam kaam / connector write). Ab Settings card POOCHNA AUR SUNNA. Default wahi (poochna band, busy-listen ON). HARD DENY aur protected screens par asar nahi.

## Jo UI me jodha (pehle sirf bolkar chalta tha)
SKILLS 104 -> 116 cards: Reels / Instagram, Instagram dost + Friend watch, Family numbers, Website operator, saved-login jobs, doosra raasta, NOVA ne kya seekha, offline website, movie ka naam, TV/horn wake guard, "samajh nahi aaya" guard, Poochna aur sunna.
MADAD 58 -> 61 topics: reels, familynum, webjobs (Kotlin + UI barabar).

## Tests
- NEW UiCompletion116Test (5 JUnit), tools/test_v7116_ui_completion.py. Extended: test_part6_skills_contract map.
- Final numbers: see bottom (filled after the last run).

## NOT RUN
GitHub Actions, APK, phone. Instagram ke button naam (English) aur sab phone-side behaviour (TV wake thresholds, horn, "samajh nahi aaya", website quality, battery / lag) abhi bhi phone data maangte hain: `NEXT_ACCOUNT_PROMPT_v7_115.md` ki Open list wahi hai.

## Jo maine jaan-boojh kar nahi kiya
- 3 "poochna" switch ka default nahi badla (owner ne v6.32 / v6.38 / v7.104 me "mat pooch" kaha tha).
- NovaAssist.kt / ChatStore.kt / VoiceFlowRules.kt delete nahi kiye (add-only rule; build green hone ke baad hi).

## Final numbers (RUN, sandbox)
- Kotlin compile: 0 errors. JUnit: OK (1595 tests).
- run_all_checks.sh: 159 passed, 0 failed.
- Headless UI: SKILLS 116 cards, 0 JS errors.
