# NOVA v7.109 - PART 6 of 8: learning + playbook update

## Goal
NOVA remembers, as COARSE counters only, which website the owner named and which blocker ended a job, and uses that at the next job of the same kind: tell the owner early instead of retrying a login wall, switch route early when an app was missing before.

## Added
- `SiteLearningRules.kt` (pure Kotlin, no Android classes):
  - Notes keyed by `site:<host>` (host the owner named in the job, `www.` removed, <= 24 chars via `PlaybookRules.cleanApp`, no path / query / port / e-mail, bank / payment / HARD-DENY hosts refused via `WebOperatorRules.isDenied`) or `kind:<job kind>` (existing Playbook kinds).
  - Counters: `ok`, blocker counts (password / code / captcha / app_missing / other), `switches` (route switches), last time. Max 16 notes (newest kept), max 2 sites per job, counters cap 999 and halve above 40.
  - `hint(...)`: <= 520 chars, advice only ("never overrides the owner's request, a login wall or safety"). Wall warning only when walls >= successes for that site, so a site that mostly works stays quiet. Also: missing-app hint (>= 2x, "never install automatically"), route-switch hint (>= 2 switches), kind-level login-wall hint (>= 2x).
  - `blockerFor(LoginRules.State)`, `summary()`, `cleanKey()` (stored keys re-validated on read).
- `SiteLearningStore.kt`: SharedPreferences `nova_site_learning_v1`; `record / hintFor / summary / clear`. Persists only key, counters, time.
- `NovaService` (all in try/catch, `ErrLog "NovaSiteLearning"`):
  - `agentSiteLearn(state)` (called from `agentFinish`): records once per job when a playbook kind exists; SUCCEEDED, FAILED, and NEEDS_OWNER only with a known blocker. CANCELLED / plain "I need your help" are neutral. Part 2 playbook rule (NEEDS_OWNER neutral) is unchanged.
  - Login wall stop sets blocker PASSWORD / CODE / CAPTCHA; missing-app dead end sets APP_MISSING; fields reset per job.
  - `siteHint` added to the job text after the route hint, from owner-named hosts of THIS job + job kind.
- `CapabilityLearningRules`: new `Gap.LOGIN` (classifies the exact Part 5 owner messages and "captcha") with guidance. Old gaps and tests unchanged.
- `SiteLearningRulesTest.kt` (14 JUnit tests), `tools/test_part6_site_learning_contract.py` (24 static checks).
- Version 7.109 / code 36. Six older contract tests (Part 1-5, capability) now also accept 7.109 / code 36 (same way earlier parts did it).

## Unchanged on purpose
HARD DENY list, `Control.blocked()` protected screens, Part 4 secret-box typing deny, Part 5 wall detection + 3-step NEEDS_OWNER stop, account-security tap deny, Parts 1-3 hooks, no automatic app install. No raw task / URL / screen text / typed text / message / contact is stored.

## Honest limits
- Site notes exist only for hosts the owner SAID (or typed) in the job. A wall in the Gmail app or a site opened by a skill without a named host is counted on the job kind (`kind:mail` etc.) only.
- Walls are counted when the job stops (3 steps). A wall that the model works around is not counted.
- The hint is advice to the model; it cannot force it. Real effect on a phone is NOT TESTED.
- The Playbook "clear" in Part 7 must also call `SiteLearningStore.clear()` (not wired yet: no UI in this part).

## Checks
- PASS: `tools/test_part6_site_learning_contract.py` (24 checks).
- PASS: 148 of 149 `tools/test_*.py` from the project root (the two *smooth* tests run alone). Only `test_part20_contract.py` fails (pre-existing on the untouched v7.103 zip).

## NOT RUN / NOT VERIFIED
- Kotlin compile (no kotlinc / internet in this sandbox), `./gradlew testDebugUnitTest` (incl. `SiteLearningRulesTest`, `LoginRulesTest`), GitHub Actions, APK, phone. If the build log shows an error, send it: reproducible compile errors are fixed first.

## Owner test after build (on phone, optional until Part 8)
1. Ask the same login job on one site 2 times while signed out: the 2nd time NOVA should say early that a password / code is needed.
2. A job on a site where you are signed in: no warning, job works as before.
