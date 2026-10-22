# NOVA v7.105 - PART 2 of 8: Playbook store (route memory)

## Goal
NOVA remembers WHICH app route worked or failed for each coarse KIND of job, and tells the agent next time ("try youtube first, oldplayer often failed"). Hint only: it never overrides the owner's request, the HARD DENY list or any safety check.

## Added (new files, nothing old removed)
- `PlaybookRules.kt` (pure Kotlin, no Android classes): `Kind` (video, music, mail, message, maps, photo_ai, shop_lookup, form, search, generic) from keyword tokens; `cleanApp` (short plain label only: no URL/path/query/e-mail/long number/sentence); `record` (success credits every app opened, up to 3; failure charges only the LAST app opened; never-worked route with 5 failures is pruned; max 5 routes per kind; counters decay above 40); `score` (Laplace); `hint` (<= 520 chars, bilingual); `summary`.
- `PlaybookStore.kt`: SharedPreferences `nova_playbook_v1`, JSON of kind + app label + ok/fail/last; re-sanitises on read; `clear()` available for a later UI.
- `PlaybookRulesTest.kt` (7 JUnit tests) and `tools/test_part2_playbook_contract.py` (16 static checks).

## Hooks in NovaService (4 small blocks, all try/catch, old v7.103 learning hooks untouched)
1. `agentRun`: kind = `PlaybookRules.classify(task)` (only the kind is kept); `routeHint` appended to the agent task after the capability guidance.
2. `agentDo` `open_app`: if the app really opened, its label is noted.
3. `agentFinish`: SUCCEEDED -> success, FAILED -> failure, recorded once. NEEDS_OWNER (OTP/CAPTCHA/permission waits) and CANCELLED are neutral, so a blocker that is not the route's fault does not punish the route.
4. Per-job reset next to `agentFailureRecorded = false`.

## Privacy
Stored: kind key, sanitised app label (<= 24 chars of a-z 0-9 space . -), counters, timestamp. NOT stored: task text, screen text, typed text, messages, contacts, URLs with path/query, keys, credentials.

## Unchanged on purpose
HARD DENY list, protected screens, Part 1 switches, one-time connector setup prompts.

## Checks
- PASS: `tools/test_part2_playbook_contract.py` 16/16; `tools/test_part1_approval_contract.py` 11/11.
- PASS: all 145 `tools/test_*.py` run from the project root, except `test_part20_contract.py` (also fails on the untouched v7.103 zip: pre-existing). `test_native_smooth_contract.py` and `test_part6_smooth_contract.py` are timing-based and failed once under a 12-way parallel run, then PASSED when run alone: run them alone.
- Version checks in the older capability/Part 1 tests now accept 7.105.

## Not verified
- Kotlin was NOT compiled here (no kotlinc/Gradle in this sandbox). `PlaybookRulesTest` has not been run. Run `./gradlew testDebugUnitTest` / GitHub Actions and send the log if anything fails.
- No APK, no phone test, so the real effect on the agent's choices is untested.

## Owner test after build
1. Run 3 jobs like "youtube kholo aur <kuch> chalao": after 2 successes the next run should start with the same app.
2. Make one job fail on purpose (wrong app): the failed app should stop being preferred.
3. Everything must still refuse pay / order / password / OTP.
