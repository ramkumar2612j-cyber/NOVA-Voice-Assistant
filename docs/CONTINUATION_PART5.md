# Continuation notes - Part 5 (v6.75) DONE in source, NOT compiled: voice + Android control audit

Base: this ZIP. Read CONTINUATION_PART1-4.md and ROADMAP_PARTS_4_TO_10.md first.

## DONE (all written; sandbox had no kotlinc and no internet, so NOTHING below was compiled or run in Kotlin)
- v6.74 files: `VoiceDiagnosis.kt` (real reasons for wake / TTS / foreground-service start / battery), `UnsupportedRules.kt` (power off / restart / factory reset answered clearly; mobile data, hotspot, airplane, location, DND open the right Settings page), hooks in `Logic.classify`, `NovaService`, `ConfigReport`, `FeatureStatus`, `MainActivity.openTtsSettings`.
- v6.75 bug 1: negation guard in `Logic.classify` (torch / volume / brightness / media / global-nav blocks only; not around "do not disturb").
- v6.75 bug 2: open-app tail regex (`OPEN_TAIL_RE`): "instagram open karo", "chrome khol do", "chrome ko kholo"; bare trailing "open" still rejected.
- v6.75 bug 3: `Understand.simplify` returns null for "play music" / "pause music" when the sentence has more than 4 words ("gaana bajao tum hi ho" goes to the AI).
- v6.75 bug 4: `UnsupportedRules` answers "cancel alarm" / "timer cancel karo" honestly; "alarm band karo" (wake_off) untouched.
- `CommandCorpusTest.kt` (181 phrases, 7 tests), `VoiceDiagnosisTest.kt` (5 tests), `tools/test_command_corpus_contract.py`, `tools/classify_mirror.py` updated.
- Docs: `VOICE_AND_BATTERY.md`, `TEST_REPORT_PART5.md`, CHANGELOG v6.75, FEATURE_REGRESSION delta.
- Wake false-trigger audit: already covered by WakeStrictTest / WakeAdaptTest / ListenGuardTest / WakeRulesTest (no change needed).

## Decisions
- Negated sentences return null (go to the AI) instead of being refused locally: the AI can ask what the owner meant.
- "cancel alarm / timer" is answered as "not possible, use the Clock app" because NOVA keeps no handle to an alarm it set (same rule as `Timers.parse`, which already refuses these sentences).
- The corpus expected values came from the Python port and were read through by hand; where the port and Kotlin disagree, find out which one is wrong before editing a test.

## First step next time
Run GitHub Actions on this ZIP (`./gradlew assembleDebug testDebugUnitTest`). If red, send the log. Likely places: `UnsupportedRules.kt` / `VoiceDiagnosis.kt` (never compiled before v6.75 either), `Logic.kt` (`if (!negated) when (nav) {...}`, `OPEN_TAIL_RE`), the new tests. Then Part 6.
