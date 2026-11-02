# Continuation notes after Part 2 (v6.70) - master prompt
Base for the next part: the latest NOVA-Voice-Assistant.zip. Read docs/CONTINUATION_PART1.md too.

## Audit result for Part 2
- Gemini: keys and models are retried with the SAME body (history + system prompt + facts) -> context kept. (read, not run)
- Free chain + offline model: only the last sentence was sent -> FIXED in v6.70 (turns, task line, remember).
- Agent fallback (agentChain): already sends the whole step prompt (task + step history) -> unchanged.
- Missing configuration: scattered per feature (FeatureStatus box, health notification) -> one local report command + a clear agent message added.

## Done in Part 2
FallbackContext.kt, ConfigReport.kt, FreeProviders.buildBody/ProviderChain.run `turns`, LocalChat/LocalBrains `earlier`, NovaService (fallbackTurns, fallbackEarlier, fallbackTaskLine, configState, remember of backup answers, "setup check" command, agent no-key message), tests FallbackContextTest / ConfigReportTest / FreeProvidersTurnsTest, tools/test_fallback_context_contract.py.

## Architecture decisions
- The running conversation stays in NovaService.history (Gemini format). Backups read it through fallbackTurns(); nothing new is stored on disk for this.
- The task line comes from AgentTaskLedger.latest(), redacted again, fresh for 30 minutes only. It never contains screen text, audio or full prompts.
- ConfigReport is pure and reports "configured / not configured", never "working".

## Known limits / pending
1. History TTL is 2 minutes (Logic.HISTORY_TTL_MS): a chat resumed from the history list loses the model context after 2 quiet minutes, and so does a typed chat. Decide with the owner whether typed chat should get a longer TTL (voice commands must keep the short one so old commands never come back).
2. The free providers never get the picture (text only) - platform/provider limit, unchanged.
3. Nothing of Parts 1-2 was compiled or run on a phone.
4. Next: Part 3 = task memory restore after app restart / model switch for the AGENT (ledger resume is manual and safe-only; audit what is missing) + Verify/Recover gaps; Part 4 = regression sweep, build instructions, final consolidated report.

## Update v6.71 (owner: "AI ko poori history chahiye")
chatWindow() in NovaService feeds Gemini (120k chars) and the free chain (12k chars) from ChatSessions (full current chat, newest kept). Old pending item 1 (2-minute TTL) is solved for stored chats; the in-memory `history` + TTL now only matter when no chat is stored. Open risk: a free provider with a smaller context may answer 400 -> the chain tries the next model; if that is common on the phone, lower FULL_FREE_CHARS. Offline model still gets 2 turns.
