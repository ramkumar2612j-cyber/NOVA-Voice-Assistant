# Continuation notes - Part 6 (v6.76) DONE in source, NOT compiled: agent loop hardening

Base: this ZIP. Read CONTINUATION_PART1-5.md and ROADMAP_PARTS_4_TO_10.md first.

## DONE (written; sandbox had no kotlinc and no internet, so NOTHING below was compiled or run in Kotlin)
- `ToolVerify.kt`: levels, failure kinds, bounded recovery, sensitive table, `report()`.
- Hooks: `NovaService.runLocalTool` (one retry for torch / volume / brightness; `finish`), `NovaService.runTool` (`finish`).
- `AgentRules.isAiSend` + stricter `check` for AI-app send ("Send money" no longer free).
- Tests: `ToolVerifyTest`, `AgentRulesPart6Test`, `tools/test_tool_verify_contract.py`. Docs: `TEST_REPORT_PART6.md`, CHANGELOG v6.76.

## Decisions
- Only tools that set an explicit state and read it back may be retried (once). Repeating a call, message, alarm or app open is never automatic.
- A tool that Android cannot confirm says so; nothing is upgraded to "verified".
- Guides point to things that exist ("setup check" is answered by `ConfigReport`).

## NOT DONE
- Speak `ToolVerify.report` at the end of every agent job; per-step verification in the screen agent.

## First step next time
Run GitHub Actions on this ZIP (`./gradlew assembleDebug testDebugUnitTest`). If red, send the log. Likely places: `ToolVerify.kt`, `NovaService.runLocalTool` (local `fun exec(): R`), `UnsupportedRules.kt` / `VoiceDiagnosis.kt` (Part 5, never compiled), the new tests. Then Part 7.
