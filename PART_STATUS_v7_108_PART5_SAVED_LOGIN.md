# NOVA v7.108 - PART 5 of 8: Gmail / Chrome saved-login jobs

## Goal
Jobs in Gmail, Chrome and websites that need a signed-in account work through accounts ALREADY signed in on the phone (Gmail app, Chrome saved sign-in, the account chooser "Continue as ..."). NOVA never types or stores a password, OTP, card number or UPI PIN, never solves a CAPTCHA, and never fakes success.

## Added
- `LoginRules.kt` (pure Kotlin, no Android classes):
  - `detect(lines, secretEditable)`: PASSWORD / CODE / CAPTCHA walls (block), CHOOSER (normal step), NONE. Only short lines (<= 140 chars) count and words are matched whole. Words that also occur in normal mail text ("verification code", "OTP", "6 digit code", ...) count ONLY together with a password/code text box on screen, so reading a mailbox that contains an OTP mail is not a wall.
  - `isAccountDanger`: remove / delete account, recovery, 2-step, passkey, security key, backup codes, "sign out of all" are never tapped.
  - `wantsHint` / `hint`: login rules added to the job text for mail / login / Google / Chrome jobs only.
  - messages that name the exact thing needed (Hinglish / English).
- `AgentRules.check` (tap branch): account-security taps are DENIED ("account security item"). Additive.
- `NovaService` (try/catch, `ErrLog "NovaLogin"`): each step the screen is checked (not on protected screens). First wall step: a LOGIN WALL hint + chat note ("do not type, try the account chooser / another signed-in route"). Account chooser: one hint "tap the owner's account / Continue as". If the SAME kind of wall is still there after 3 consecutive steps: the job stops as NEEDS_OWNER with the exact thing the owner must do (password / code or phone approval / CAPTCHA) plus the progress so far. NEEDS_OWNER is neutral for the Part 2 playbook and is recorded as a coarse blocker by v7.103 learning. Nothing typed, nothing stored (counter only).
- `LoginRulesTest.kt` (8 JUnit tests), `tools/test_part5_login_contract.py` (17 static checks).
- Version 7.108 / code 35; older contract tests accept it.

## Unchanged on purpose
HARD DENY list ("password", "otp", ... taps still denied), `Control.blocked()` protected screens, Part 4 secret-box typing deny, Parts 1-3, no raw task / URL / screen text / typed text stored, no automatic app install.

## Honest limits
- Chrome's "Use saved password?" / autofill suggestions contain the word "password": the existing HARD DENY blocks tapping them, so a job that needs password autofill ends as NEEDS_OWNER ("sign in yourself"). Gmail app and Chrome profiles that are already signed in are not affected. Changing that would mean touching the HARD DENY list, so it was NOT done.
- A blocked login can end up as NEEDS_OWNER after 3 steps even if the owner's job could have continued elsewhere; the wall hint is sent first so the model can try another route.
- Detection is text-based (English); a login screen in another language may not be recognised and then ends by the old stuck/step limits.

## Checks
- PASS: `tools/test_part5_login_contract.py` and Part 1 / 2 / 3 / 4 / capability contracts.
- PASS: 147 of 148 `tools/test_*.py` from the project root (the two *smooth* tests run alone). `test_part20_contract.py` fails on the untouched v7.103 zip too (pre-existing).

## NOT RUN / NOT VERIFIED
- Kotlin compile, `./gradlew testDebugUnitTest` (incl. `LoginRulesTest`), GitHub Actions, APK, phone. Send the build log if anything fails; fix reproducible failures first.
- Real Gmail / Chrome sign-in screens vary by phone and language: untested.

## Owner test after build (on phone)
1. With Gmail signed in: "gmail kholo aur naya mail padho" -> works, no password asked.
2. Sign out of a site in Chrome, then "<site> par login karke ... dekho": NOVA must stop with "password chahiye / code chahiye", never type a password.
3. A job that shows a CAPTCHA: NOVA says CAPTCHA aaya hai, aap solve karo.
4. Account chooser: NOVA taps your account / "Continue as ...".
