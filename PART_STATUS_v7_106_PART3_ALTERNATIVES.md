# NOVA v7.106 - PART 3 of 8: Alternatives engine (route switching inside one job)

## Goal
When a route fails inside a job (app missing, screen stuck, taps failing), NOVA picks the NEXT safe route by a fixed, bounded rule and tells the agent, instead of just saying "I can't". Hint only: the model still emits the action and `AgentRules.check` still vets it.

## Added
- `AlternativesRules.kt` (pure Kotlin, no Android classes). Ordered candidate routes per `PlaybookRules.Kind` (app labels / site hosts only):
  VIDEO youtube -> youtube.com -> google.com | MUSIC gaana -> spotify -> youtube -> youtube.com | MAIL gmail -> mail.google.com | MESSAGE whatsapp -> whatsapp business -> messages | MAPS maps -> maps.google.com -> google.com | PHOTO_AI gemini -> chatgpt -> gemini.google.com -> chatgpt.com | SHOP_LOOKUP amazon -> flipkart -> google.com | FORM / SEARCH browsers (chrome, samsung internet, firefox, edge / google app) | GENERIC: none (old behaviour unchanged).
- `next(kind, triedThisJob, playbookEntries, failureGap, switchesDone)`: skips routes already tried in this job, orders by `PlaybookRules.score` (good first, often-failed last, unknown 0.5, ties keep list order), max 4 switches per job, never returns a denied/protected label. Deterministic. `failureGap` only changes the wording of the hint, not the order.
- `isDenied`: same words as `AgentRules.HARD` + bank/wallet/UPI/payment/paytm/phonepe/settings/permission/installer/Play Store/NOVA (a contract test checks the word list stays in sync with `AgentRules.HARD`).
- `PlaybookStore.entries()`: read-only copy of the sanitised entries.
- `NovaService` (all try/catch, `ErrLog "NovaAlternatives"`):
  1. per-job reset (tried routes, switch count, dead-end count);
  2. `agentDo open_app`: every attempted label is noted as tried; if the app is missing AND the label is a fixed host from the tables (e.g. youtube.com), it opens through the existing `openAppOrSite`. No install, ever;
  3. after each step: `open_app` FAILED -> APP_MISSING; 3 failed taps in a row (`FAILED (...)`) -> TAP_FAILED; existing stuck path (`stuckHints`, still max 4) -> STUCK. Each adds `ROUTE SWITCH: <label> did not work; try <next>` to `hist` and a Hinglish/English `agentNote`;
  4. no route left: hint says "do not pretend, use fail"; if an `open_app` fails again after a dead end (2nd dead end) the job stops as FAILED with the real blocker. GENERIC jobs are never stopped by this;
  5. ledger progress line gets only `; route switches N` (a number, no text).
- Part 2 credit is unchanged: a route that succeeded after a switch is credited by the normal `agentFinish` hook.
- `AlternativesRulesTest.kt` (12 JUnit tests) and `tools/test_part3_alternatives_contract.py` (29 static checks).
- Version 7.106 / code 33. Older version checks now accept 7.106.

## Unchanged on purpose
HARD DENY list in `AgentRules`, `Control.blocked()` protected screens, Part 1 switches, Part 2 playbook, screen/web text is untrusted data, no raw task/screen text stored, CAPTCHA/OTP -> NEEDS_OWNER, no automatic app install.

## Checks
- PASS: `tools/test_part3_alternatives_contract.py` 29/29 (run from project root).
- PASS: 145 of 146 `tools/test_*.py` from the project root (the two *smooth* tests run alone). `test_part4_fx_render.py` failed once under load (60 s timeout) and PASSED alone, also on the untouched v7.105 zip.
- `test_part20_contract.py` fails on the untouched v7.103 zip too (pre-existing).

## NOT RUN / NOT VERIFIED
- Kotlin compile, `./gradlew testDebugUnitTest` (incl. `AlternativesRulesTest`, `PlaybookRulesTest`), GitHub Actions, APK, phone. Send the build log if anything fails and fix reproducible failures first.
- Whether the real model follows the ROUTE SWITCH hint: untested on a phone.

## Owner test after build (on phone)
1. Say "youtube kholo aur <kuch> chalao" with YouTube app disabled/missing: NOVA should note "Raasta badal raha hu ... youtube.com" and open it in Chrome.
2. Ask for a job in an app that is not installed (kind with routes, e.g. gmail): it should try the next route, and if nothing is left say the exact blocker, not "done".
3. Everything with pay / order / password / OTP must still refuse.
