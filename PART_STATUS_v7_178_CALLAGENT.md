# v7.178 CALL AGENT — honest status

Owner request (video): JARVIS khud restaurant jaisi jagah par ASLI call lagaye, live status dikhaye
(DIALING -> ON THE CALL -> ENDED), speaker par booking ki baat bole, aur call ke baad report de
("7 baje full hai sir, 8 baje ki table milegi"). "Sab real me work kre."

## What is written (source)

### Phone (Kotlin, real work yahan hota hai)
- `app/src/main/java/com/nova/assistant/CallAgentRules.kt` (NEW): pure rules — command parsing
  ("call karke table book karo" style only; plain "X ko call karo" is NOT ours), deny set
  (payment/OTP/bank/password/Aadhaar... refuse before dialing), script builder, call-state
  machine (DIALING / ON_CALL / ENDED / FAILED), honest report text (hi/en), callback tag rule.
- `app/src/main/java/com/nova/assistant/CallAgent.kt` (NEW): the real thing —
  `Intent.ACTION_CALL` dial (CALL_PHONE permission, same pattern as the old direct-call path),
  TelephonyManager call-state polling (RINGING->DIALING shown, OFFHOOK->ON THE CALL, IDLE->ENDED),
  speaker ON during the call, TTS script spoken 2.5 s after pickup, 45 s dial timeout,
  180 s call cap, STOP ends the call (TelecomManager.endCall with ANSWER_PHONE_CALLS),
  errors to ErrLog, `snapshotJson()` for the card, one live agent at a time.
- `NovaService.kt`: `callAgentRoute()` runs BEFORE laptopRoute in process(); `wireCallAgent()`
  in onCreate (emitter -> emit, speaker -> speak, speechStop -> tts.stop); STOP ("ruko") also
  ends a live agent call; the final report is spoken + shown in chat ("📞 ...").
- `MainActivity.kt`: bridges `callAgentSnapshot()` (card pull) and `callAgentStop()` (X button).
- `HelpGuide.kt` + ui.html CAPS: topic `callagent` (Hindi + English, honest limits written in
  the help text itself).

### UI (ui.html = NOVA theme, download/JARVIS.html = JARVIS gold theme; same code)
- Live call card `#cagCard` (bottom, above the tab bar): colored state dot (blue DIALING /
  green ON THE CALL / gray ENDED / red FAILED), name + number, live text, `▶ ON SPEAKER` tag,
  `CALLBACK NEEDED` tag (FAILED), X button -> `Android.callAgentStop()`.
- Events: `onNova('callcard', json)` pushes live states; `Android.callAgentSnapshot()` pulls on
  app open + every 2 s while the call runs; final states are never overwritten by a stale poll.
- SKILLS list: new entry "Call agent (khud call karke booking)" with SAY examples.
- AGENTS tab (v7.178 earlier today, 112 agents) untouched and re-verified.

## Evidence (machine-run, this sandbox)
- tools/test_call_agent_contract.py — ALL PASS (28 checks: parsing mirror, deny, number
  extraction, ACTION_CALL present, routing order, bridges, HelpGuide, both UI files).
- tools/test_help_contract.py — PASS (80 topics equal).
- tools/test_bridge_caps_contract.py — PASS (124 String params, 0 uncapped; our bridges take none).
- tools/test_part6_skills_contract.py — PASS (80 topics mapped; callagent added to MAP).
- tools/test_laptop_link_contract.py — PASS (no regression).
- Playwright render (JARVIS.html, fake bridge): card appears from snapshot, DIALING ->
  ON THE CALL (ON SPEAKER tag) -> ENDED -> FAILED (CALLBACK NEEDED tag), X calls the bridge,
  IDLE hides the card, AGENTS tab still works (112/112), 0 JS errors.
- Kotlin brace/paren balance checked (strings stripped); no kotlinc in this sandbox.

## NOT DONE (honest)
- Kotlin compile, GitHub Actions build, APK — not run here.
- Phone test: real dial, real speaker script, real call states on a real network — NOT tested.
- The two-way AI conversation from the video (AI hears the restaurant and answers): NOT
  possible on-device. Android 10+ blocks third-party apps from hearing the other side of a
  call. That needs a paid cloud telephony line (Retell / Vapi style). This implementation does
  the real dial + real spoken script + real states + honest report, and says plainly that it
  could not hear the answer.
- RECORDING ("▶ recording" in the video): not implemented (would need call recording, which
  the same Android restriction blocks for the far side; the "▶ ON SPEAKER" tag shows instead).

## How the owner tests (phone, after GitHub build installs v7.178)
1. Bolo: "jarvis 98XXXXXXXX par call karke table book karo" (apna number dekar test karo!).
2. Pehli baar Call permission allow karo.
3. Live card dekho: DIALING -> ON THE CALL (speaker par script bolega) -> ENDED.
4. Call ke baad report suno — isme likha hoga ki jawab suna NAHI ja saka.
5. "ruko" bolo ya card ka X dabao — call kat jayegi.
