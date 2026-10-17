NOVA Android app, Kotlin package com.nova.assistant. Base ZIP: NOVA-WIP-v75-PART3-PARTIAL.zip. Read AUTOPSY_PART1.md, PART2_STATUS.md and PART3_STATUS.md first. Owner is non-technical, Hinglish, phone-only. Deliver whole ZIP, no manual edits.

STRICT: preserve all old features/screens/settings/API/fallbacks/permissions and v6.57 lead-routing fix. No mock/demo/fake success. Any unrun build/JUnit/device test must be marked NOT TESTED. Run `bash tools/run_all_checks.sh`; keep `ui.html` CAPS, HelpGuide.TOPICS and aiFacts topic contract equal. Keep prior ZIP untouched.

PART 3 REMAINING — finish before Part 4:
1. Implement real STOP button in NovaHud. Must stop current TTS, safely cancel current agent/background job and stop listening. Do not accidentally execute/send a pending call/WhatsApp halfway; pending confirmations must be cancelled safely. HAAN/NAHI must remain visible while waiting, and stray taps must not dismiss question. Wire through NovaService, not just visual.
2. Add persistent SILENT switch on home, settings and popup; ON suppresses ordinary speech but work continues and answers stay visible in chat/card. Voice silent on/off. Preserve old CHUP, quiet mode and chat-speak setting separately. Safety confirmations and genuinely critical alerts policy must be explicit and tested. Restart persistence.
3. Messages screen should show full readable answer card/chat in silent mode; use existing holdMs safely.
4. Finish Part 2 remaining items only if safe: voice routes for Auto-reply, Routines, Lock Guard, Battery guide, Offline AI pack, API limits; add honest FeatureStatus rows.
5. Add tests for STOP (speech+job+listening), SILENT on/off, visible silent answer, restart persistence, and STOP during HAAN/NAHI. Add tests to run_all_checks where appropriate.
6. Verify full Python checks. Attempt Gradle build/JUnit only if environment supports it. Do not claim build pass without actual result.
7. Update PART3_STATUS.md honestly. Once Part 3 truly done, create NEXT_ACCOUNT_PROMPT_PART4.md for modern UI redesign and deliver full ZIP with exact name NOVA-WIP-v75-PART3.zip. Do not start redesign until Part 3 is complete.
