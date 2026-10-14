# NOVA NEXT ACCOUNT PROMPT v56 — NO "SEND DABAUN?" QUESTION (continue from this exact ZIP, base = v55)

Same owner/style rules as v54/v55 (Hinglish, one step at a time, tap-by-tap github.com links, end with exactly one `Aapko karna hai: ...` line, never claim CI/compile/phone passed if not run, keep ZIP name `NOVA_v15_assistant.zip`, keep all old entries).

## Evidence from the owner's phone (2026-10-12 ~8:41, screenshots)
- Spoken order "Open whatsapp and mess aman biswan kab aO ga" -> agent started -> asked "'Send' dabaun? Haan ya nahi bolo" -> his short "haan" (1.7 s) was refused by the voice check ("Awaaz bahut chhoti thi") -> task cancelled ("Task cancelled. Owner verification..." notification). Root cause: project design: `AgentRules.check` returns CONFIRM for any RISKY word, and "send" is in `Control.RISKY`; WhatsApp is not in `AI_APPS`, so the plain Send tap needed a voice-verified yes that short answers cannot give.
- Setup screen: Qwen offline button + "Offline engine is build me band hai" note is correct: `LocalBrains.installEngine` is intentionally empty (llama.cpp needs Kotlin 2.x). Online Qwen (`qwen/qwen3.8-27b` via the Groq key) is already in the free chain and automatic. Do NOT claim offline Qwen works; do NOT auto-download 491 MB that cannot run.
- Voice-save screens (5/5 phrases) worked.

## Change in v56
`AgentRules.isPlainSend(label)` (1-2 words, all from {send, bhejo, bhej, message, msg, now, karo}, at least one send word). In `NovaService` agent loop the CONFIRM question is skipped when `ExtCfg.skipOrderConfirm` is on AND the label is a plain send; `tapCmd` passes `must = !isPlainSend(label)`. Pay / delete / order / buy / post / confirm / submit and every HARD word still ask (or are denied). Added `AgentRulesTest.plainSendIsOnlySendWordsNotMoneyOrDelete`. v55 note "risky Send confirmation remains intentionally required" is superseded by the owner's order.

## Evidence level
`tools/test_*.py`: 31/31 pass locally (hindi contract without -I). Kotlin compile/JUnit/CI/phone: NOT run. Next: owner uploads the ZIP at https://github.com/skfashion142/NOVA-Voice-Assistant/upload/main, then reads https://github.com/skfashion142/NOVA-Voice-Assistant/actions .
