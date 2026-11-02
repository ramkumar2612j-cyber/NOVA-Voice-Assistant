# PART 18 HANG PROTECTION - status (v7.70)
Evidence: RUN = executed here; NOT TESTED = needs the real phone.

## Pehle se tha (jaanch ke baad badla nahi)
Har kaam ka watchdog (turn 120 s, analysis 120 s, frames 60 s, TTS deadline, Auto-Heal 25 s), CrashNote black box, memory pressure par page halka (NativeSmooth) + LocalBrains.release().

## Naya (add-only)
- **SAFE MODE**: 10 minute me 2 crash => 30 minute safe mode. Halke effects, laser overlay band, upar banner + "SAFE MODE BAND KARO" button. Call / message / data par asar nahi. 30 min baad apne aap band.
- CrashGuard.kt (pure rules + prefs store), crash handler me hook (crash time commit() se, normal crash handling se PEHLE), bridge getSafeMode / exitSafeMode, MADAD topic #49 + SKILLS entry.
- Memory trimming ke liye naya code nahi likha: maujooda trim (page + offline model) hi kaafi laga; ise dobara badalna phone test maangta hai.

## Result
- RUN: Kotlin compile main + test 0 errors. JUnit 1352 run / 29 fail = wahi purane org.json-stub fail; CrashGuardTest (5) PASS.
- RUN: test_part18_contract (static + headless 320 px) PASS; mutation check (crash hook hataya) = FAIL aaya.
- RUN: run_all_checks 135 scripts: 135 pass, 0 fail.
- NOT RUN: GitHub Actions. NOT TESTED: phone.

## Phone test (owner)
Safe mode dekhne ke liye app ko 10 min me 2 baar crash karna padega (normal use me nahi hoga). Asli test: normal chalne par banner NA dikhe; Part 17 ke switches aur Part 16 ka "call uthao" bhi dekho.

## Owner faisla pending (bina poochhe mat badlo)
Voice command busy NOVA par queue nahi hota; wb_ll locked phone par wake word.
