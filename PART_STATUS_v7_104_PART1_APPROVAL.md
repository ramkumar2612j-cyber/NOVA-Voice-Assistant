# NOVA v7.104 - PART 1 of 8: approval audit (no per-task yes/no)

## Owner request
Everything the owner asks (phone apps, websites, messages, forms, sign-in through an account already saved on the phone) must run WITHOUT asking for approval each time. Old code stays; add/modify only.

## What the audit found (v7.103 source)
- Agent risky taps and message/call confirmations were ALREADY off by default: `ExtCfg.skipRiskyConfirm` (default true, v6.38) and `ExtCfg.skipOrderConfirm` (default true, v6.32). `askFirst(...)` returns run() when skipRiskyConfirm is on, so publish / bulk-send / "delete all" prompts built on askFirst were already skipped.
- Two real gaps remained:
  1. Connector WRITE actions (GitHub, Vercel, ...) always used `confirmStrict` (never skippable).
  2. The task-planner prompt in `AgentRules.planSystem()` told the model to plan "messages to other people ... only with the required local spoken approval", which makes the model hesitate or ask.

## Changed (small, surgical)
- `ExtCfg.kt`: new `skipConnectorConfirm(ctx)`, pref key `skip_connector_confirm`, default TRUE.
- `NovaService.kt` (connector action path): when the switch is on, `connectorRun(...)` runs at once; the old `confirmStrict` line is kept as the OFF-path. The `perm_write` wording no longer promises "asks before each action" when the switch is on.
- `AgentRules.kt` `planSystem()`: payments/purchases/subscriptions/permission grants/password-security changes are still forbidden; messages, forms and sign-ins through an account already saved on the phone are now planned without asking for extra approval.
- `build.gradle.kts`: versionName 7.104, versionCode 31.
- `tools/test_capability_learning_contract.py`: version check now accepts 7.103 or 7.104.
- NEW `tools/test_part1_approval_contract.py` (11 static checks, all PASS).

## Deliberately NOT changed (and why)
- HARD DENY list in `AgentRules` (pay, payment, buy, purchase, transfer, order, uninstall, erase, reset, upgrade, subscribe, checkout, donate, password, otp, passcode) and `Control.blocked()` protected screens (bank/payment apps, Settings, permission screens, installer, Play Store, NOVA itself). A web page can hide text that tricks an agent into paying; banks also block automation, and a mistake there cannot be undone.
- One-time setup prompts: connect connector, disconnect connector, "write on" permission. They are setup, not per-task approval.
- OTP / CAPTCHA / 2-step codes: NOVA cannot and must not solve them. It should finish everything else, then tell the owner exactly which code is needed.
- `confirmStrict` itself (an older test requires it stays free of ExtCfg).
- Update-install question (not a task).

## Checks
- PASS: `tools/test_part1_approval_contract.py` (11/11).
- PASS: all 144 `tools/test_*.py` run from the project root EXCEPT `test_part20_contract.py`, which fails identically on the untouched v7.103 zip (pre-existing, not caused by Part 1).
- NOTE: run tests from the project root (`python3 tools/test_x.py`), not from inside `tools/`.

## Not verified
- No Kotlin compile here (no kotlinc/Gradle in this sandbox): the Kotlin edits are 3 small blocks, but a real `./gradlew testDebugUnitTest` / GitHub Actions run is required.
- No APK, no phone test.

## Owner test after build (on phone)
1. Say: "github par <connector write action>" with WRITE already on -> it must run with NO yes/no.
2. Give a multi-step task that includes sending a message in an app: it must not stop to ask for approval.
3. Ask for anything with "pay"/"order"/password/OTP: it must still refuse (expected).
