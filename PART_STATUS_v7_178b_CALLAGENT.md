# v7.178b CALL AGENT — honest status (extension)

Owner follow-up request: "Bolne pe call pe baat bhi kare, aur mere bolne pe call kar ke message
bhi de. Koi call aaye to utha ke baat kare, hamein bataye agar zaroori ho, aur unhe bole
call back karega abhi hum aapko."

## What is written (source)

### Outgoing — CUSTOM MESSAGE call
- `CallAgentRules.kt`: new `customMessage(text)` parser — extracts the words after "bolo"
  ("call karke bolo <msg>"), sanitizes (deny words removed, capped at 160 chars).
  `script(place, detail, en, custom)` now builds three script kinds: custom message,
  callback request ("call back karenge"), booking (old behaviour).
- `NovaService.callAgentRoute`: extracts `custom` and passes it to `CallAgent.start(..., custom)`.
- Example the owner can speak:
  - "jarvis papa ko call karke bolo main sham tak aaunga" -> real call to Papa, then the
    speaker says: "Namaste, main JARVIS assistant bol raha hoon, sahab ki taraf se.
    Paigam: main sham tak aaunga. Shukriya."
  - "jarvis rahul ko call karke bolo callback karenge" -> the "we will call you back" script.

### Incoming — AUTO-ANSWER + greet + report + importance filter
- `CallAgentRules.kt`: new `InState` enum, `incomingDecision(name, autoAll, autoFam)` ->
  "answer" / "tell" / "skip"; `incomingGreeting(ownerName, en)` and `incomingReport(caller, secs, en)`
  builders; `isImportantCaller(name)` (Papa / Mummy / family hints).
- `CallAgent.kt`: new `answerIncoming()` + `runIncoming()`: `TelecomManager.acceptRingingCall`
  (ANSWER_PHONE_CALLS permission), speaker ON, speak the greeting, hold for INCOMING_GREET_MS,
  `TelecomManager.endCall`, honest report.
- `ExtCfg.kt`: two new settings slots — `callAutoAll` (off by default) and `callAutoFam` (off).
- `NovaNotificationService.kt`: new isolated `handleIncomingCall(sbn)` hook runs in `onPosted`.
  On `CALL_RINGING` it reads the caller name, calls `CallAgentRules.incomingDecision`, then
  either answers (NOVA must be alive to speak) / tells the owner once / stays silent.
- `MainActivity.kt`: three new bridges — `callAgentSettings()` (get JSON), `setCallAgentSetting(key, on)`,
  `requestAnswerCalls()` (ANSWER_PHONE_CALLS prompt), plus `REQ_ANSWER = 49`.

### UI (ui.html = NOVA theme, JARVIS.html = gold theme; same code)
- Skills entry updated: "Call agent (call + incoming + custom msg)" with new SAY examples.
- CAPS entry + HelpGuide topic updated to match (custom msg + incoming + importance).
- New CALLTYPE AGENT settings block: two checkboxes (auto-answer family / all) + a permission
  button + a one-line honest note. Wired to the three new bridges.
- The existing live call card already shows DIALING / ON THE CALL / ENDED / FAILED — the same
  card is reused for an incoming auto-answered call (the greeting text shows on it).

## Evidence (machine-run, this sandbox)
- tools/test_call_agent_contract.py — ALL PASS (now ~50 checks: custom message parsing,
  callback-request script, isAgentJob accepts "bolo" jobs, incoming importance decisions,
  all new bridges, ExtCfg slots, NovaNotificationService hook, both UIs).
- tools/test_help_contract.py — PASS (80 topics equal in Kotlin and ui.html).
- tools/test_bridge_caps_contract.py — PASS (125 String params, 0 uncapped; our bridges take none).
- tools/test_part6_skills_contract.py — PASS (80 mapped; callagent title updated).
- tools/test_laptop_link_contract.py — PASS (no regression).
- Playwright render (JARVIS.html, fake bridge):
  - Custom-message DIALING card shows caller "Papa" + the message text.
  - Incoming ON THE CALL card shows the greeting text.
  - Settings: ca_fam, ca_all checkboxes + ca_perm button present; toggling fires
    setCallAgentSetting('fam', true) and requestAnswerCalls().
  - AGENTS tab still 112/112 (regression-checked separately).
  - Original call card still passes DIALING -> ON THE CALL -> ENDED -> FAILED + X + IDLE.
  - 0 JS errors.
- Kotlin brace/paren balance: all my edited regions are balanced (checked with a state
  machine that handles //, /* */, "..." and """...""" raw strings).

## NOT DONE (honest)
- Kotlin compile, GitHub Actions build, APK — not run here.
- Real phone test: real outgoing custom-message call, real incoming auto-answer on a real
  network — NOT tested.
- The two-way AI conversation (JARVIS hears the caller and answers): NOT possible on-device.
  Android 10+ blocks third-party apps from hearing the far side of a call. The incoming
  auto-answer is a ONE-WAY announce: JARVIS greets, cannot hear the reply, hangs up, reports.

## Owner test (after GitHub build installs v7.178b)
1. Outgoing custom message:
   - "jarvis <apna number> par call karke bolo main sham tak aaunga"
   - Real call, speaker par aapka paigam bolega, phir report.
2. Outgoing callback request:
   - "jarvis <number> par call karke bolo callback karenge"
3. Incoming auto-answer:
   - Settings > CALLTYPE AGENT > AUTO-ANSWER family ON + ALLOW the ANSWER-CALLS permission.
   - Apne doosre number se family contact wale number par call karo.
   - JARVIS khud uthayega, greeting dega ("Namaste, main JARVIS..."), thodi der baad rakh dega,
     phir aapko bolega "Papa ki call aayi thi: utha kar 12 second tak greeting di, phir rakh di.
     Unka jawab main sun nahi paya."
4. Importance filter (auto OFF):
   - Family (Papa/Mummy) ki call aaye to bina auto-answer ke bhi JARVIS bolegi
     "Papa ki call aayi hai." Unknown number ki call pe chup rahegi.
