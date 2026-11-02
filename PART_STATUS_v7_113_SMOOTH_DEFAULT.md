# NOVA v7.113 - hang / battery fix (SMOOTH default + no per-frame wake-ups)

Base: v7.112 CONTACTFIX. Changed: `app/src/main/assets/ui.html` only (+ 4 old tests adjusted, 1 new test). Kotlin untouched.

## Measured (headless chromium, 412x800, CPU throttled 4x as a phone stand-in, 12 s window)
| mode | main-thread busy | rAF wake-ups/s | timer callbacks/s |
| AUTO with effects on (v7.112) | 43.3 % | 293 | 18 |
| SMOOTH (v7.112)               |  6.2 % | 300 | 18 |
| SMOOTH (v7.113)               |  1.2 % |   0 | 11 |
Desktop headless, not a phone: relative numbers are the evidence, absolute % are not. Battery on a real phone is NOT measured.

## What changed
1. Fresh install default = SMOOTH. One-time migration (`nova_q_mig1`): a stored AUTO becomes SMOOTH once; SHARP is kept; owner can pick AUTO again any time.
2. In SMOOTH the 7 looping animations (2D orb, 3D orb, starfield/NOVA core, particles, perf tick, matrix rain) re-arm with a ~333 ms timer (1.5 s hidden) instead of requestAnimationFrame (`__qsRaf`, `cancelAnimationFrame` wrapper handles both).
3. In SMOOTH timers under 600 ms run at 600 ms, 1-5 s polls run 1.5x slower.
4. perf governor (v7.37) does not run in SMOOTH (still theme, nothing to measure; also would mis-read 333 ms ticks as stalls).
## Tests
run_all_checks.sh: 156/156 PASS (new `tools/test_v7113_smooth_default.py`). 4 old tests now choose AUTO explicitly because the default changed. `test_part4_fx_render.py` (listed as failing in v7.110 notes) passes.
Gradle / JUnit / APK / phone: NOT run here (no Android SDK, no network). Kotlin files are byte-identical to v7.112.
## Owner phone test (30 min)
Install, open NOVA -> SETUP -> GRAPHICS QUALITY should already show SMOOTH. Use 30 min, compare hang + battery % with before. If you want the live look back choose AUTO or SHARP.

## Addendum: car horn / beep wakes NOVA (owner report)
Cause: the wake recognizer has a 3-entry grammar ("nova", "hey nova", "[unk]"), so any loud sound is forced into one of them; a long partial "nova" confirmed after ~0.4 s.
Fix (Logic.kt `wakeVerified`, NovaService wake loop): `setWords(true)`; only a FINAL result wakes, and the word needs conf >= 0.75 and length 0.18-1.10 s. No word data = allowed (never blocks a real wake on old engines).
Trade-off: wake now waits for the final result (about 0.3-0.5 s later than before). If too slow in real use, tell me and the partial path can come back for quiet rooms only.
Tests: compile 0 errors (kotlinc 1.9.24, real android-34 jar), JUnit 1538/1538 OK (7 new in HornGuardTest), run_all_checks all PASS.
NOT verified: real horn / real mic on a phone. Thresholds 0.75 and 0.18-1.10 s are my estimate, tune from the phone result. Porcupine path (if a key is set) is unchanged.
