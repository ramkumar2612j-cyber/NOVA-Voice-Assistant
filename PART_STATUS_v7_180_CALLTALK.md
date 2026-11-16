# v7.180 CALL TALK — honest status

Owner request: "Call me samne wale se baat ho sake, chahe bypass ya shortcut lagana pade."

## What it does (outgoing agent calls only)
Shortcut used: NO OS bypass. The call is on SPEAKER, the phone MIC hears the far side, Whisper (Groq key)
turns it to text, ONE short reply is written by the model, rule-checked, spoken by TTS. Loop, max 6 turns.

- `CallAgent.feed()` — mic loop hands every 100 ms chunk here while `conversing` (hook in NovaService BEFORE
  wake-word logic, so the far side can never trigger a NOVA command). Our own TTS (+0.8 s tail) is ignored (no echo loop).
- `CallAgent.converse()` — utterance -> Whisper -> echo check -> money/secret check -> goodbye check -> model reply
  -> `cleanReply` -> speak. Quiet line: "are you there?" at 18 s, hang up at 40 s. STOP always ends it.
- `CallAgentRules` (pure, compiled + run here): MAX_TURNS, convSystem/convUser, cleanReply (deny words -> safe line,
  long numbers never spoken), farSideSensitive (payment/OTP/bank... from THEM -> safe line, stop, hang up),
  isGoodbye, isEcho, talkReport (honest).
- Report says what they said ("7 baje full hai, 8 baje milegi") from a model summary; fallback = their last lines.
- Mic silenced by the phone during calls: detected (12 s of digital silence) -> falls back to speak-only and the
  report says "mic band tha, jawab sun nahi paya".
- No Groq key: speaks only, and tells you so right away.
- Help text + ui.html + JARVIS.html updated (no more "cannot hear" claim for outgoing calls).

## Evidence (machine-run here)
- REAL Kotlin compile: CallAgentRules.kt with kotlinc 2.0.21 (from Gradle's lib) + 25 behaviour checks PASS.
- CallAgent.kt type-checked against Android stubs: 0 errors (stubs approximate the Android API; not the real SDK).
- tools/test_call_talk_contract.py, test_call_agent_contract (64), test_lead_finder_contract, test_help_contract (81),
  test_part6_skills_contract, test_bridge_caps_contract, test_laptop_link_contract — all PASS.
- JARVIS.html: same 2 fake-bridge JS errors as the original file (none added).

## NOT DONE (honest)
- NovaService.kt edits (4 wiring lines + 1 hook) are NOT compiled here (file too big / Android SDK missing). GitHub Actions will.
- NOT tested on a phone. Biggest unknown: whether YOUR phone lets the mic hear the speaker during a call
  (many do, some silence it). Whisper+model add ~2-5 s of pause per turn.
- Incoming auto-answer stays one-way (a talking bot answering unknown callers is a scam/abuse risk).
- Far-side audio goes to Groq (Whisper) and the transcript to your AI provider; the opener says it is the JARVIS assistant.
- Recording is not saved.

## Owner test
1. Settings: Groq key saved. 2. "jarvis <apna dusra number> par call karke table book karo".
3. Doosre phone se bolo "7 baje ka time nahi hai, 8 baje milegi" -> JARVIS jawab dega, phir report.
4. Mic block ho to report me likha aayega; tab batao, doosra tareeka (cloud line) dekhenge.
