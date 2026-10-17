NOVA Part 4 — modern UI redesign, strict preservation

Use `NOVA-WIP-v75-PART3.zip` as the only source of truth and read `PART3_STATUS.md`, `PART2_STATUS.md`, and `NEXT_ACCOUNT_PROMPT_PART3.md` first.

Owner speaks Hinglish and is non-technical/mobile-only. Return the complete ZIP after the part, named exactly `NOVA-WIP-v75-PART4.zip`, plus `NEXT_ACCOUNT_PROMPT_PART5.md` and a short Hindi status report.

STRICT RULES: do not delete, disable, replace, or redesign away any existing feature, screen, setting, API provider, fallback, permission, command, or safety confirmation. Keep package, project structure, and existing behavior. No demo/mock/fake success. Keep API keys out of logs and reports. Do not send calls or messages without the existing required confirmation. Preserve the Part 3 STOP/SILENT work and all Part 1/2 functionality. If compile/tests/device behavior cannot be verified, label NOT TESTED; never claim PASS without running it.

PART 4: modernize the UI only while preserving every existing capability and JS bridge contract. Improve layout responsiveness, accessibility, legibility, smooth transitions, and clear status/error/loading states. Do not change action semantics. Keep Home, Settings, Chat, History, Help/CAPS, FeatureStatus, Update Center, popup, CHUP, quiet mode, chat-speak, SILENT and STOP. Ensure every feature remains reachable and text remains readable in SILENT mode. Keep HAAN/NAHI and STOP separate; stray taps must not dismiss confirmations. Add/update UI contract tests. Run `bash tools/run_all_checks.sh`, Android Gradle build and JUnit if the environment permits; clearly report each PASS/FAIL/NOT TESTED.
