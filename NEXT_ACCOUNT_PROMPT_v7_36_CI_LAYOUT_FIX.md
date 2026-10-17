# NOVA v7.36 - CI LAYOUT FIX (small patch on v7.35). Read together with NEXT_ACCOUNT_PROMPT_FULL_v7_35_PART5_VERIFY.md.
Owner rules unchanged: owner is NOT a programmer, phone only, HINGLISH, SHORT, one step at a time, add-only, never touch .github/workflows/build.yml, report PASS / FAIL / NOT TESTED with evidence, no push by Claude.

## How the owner's real CI works (learned from the first real run, 2026-10-17)
- Repo ramkumar2612j-cyber/NOVA-Voice-Assistant, workflow .github/workflows/unpack.yml ("Unpack zip and build NOVA").
- It runs ONLY when a file named exactly NOVA-Voice-Assistant.zip is pushed to main (paths filter). Any other zip name = workflow never starts.
- Do NOT rename a zip on the GitHub website: a web rename of a binary corrupted it to 2 bytes ("End-of-central-directory signature not found"). Rename on the phone first, then Upload files.
- Unpack step: unzip to /tmp/u, if the zip has ONE top-level folder that folder is used, otherwise /tmp/u; rsync -a --exclude='/.github' onto the repo (zip's .github is never copied, old files are never deleted), deletes the zip, commits, pushes. A small patch zip works the same way: put the changed files in their normal paths and give the zip at least 2 top-level entries (or one wrapper folder), otherwise a lone folder like tools/ would be flattened to the repo root.
- Then unpack.yml copies ITSELF to .github/workflows/build.yml (not committed) so the Python guards can read a build.yml, runs tools/run_all_checks.sh, prepare_vosk_model.sh, then `gradle --no-daemon testDebugUnitTest assembleDebug`, then publishes the APK to release nova-latest.

## First real run result
- unpack OK, contract checks 96 pass / 12 fail. 11 = KNOWN_STALE_GUARDS.txt. The 12th = tools/test_part5_contract.py ("build.yml changed!": its sha256 check of build.yml cannot match the unpack.yml runtime copy). run_all_checks.sh returns 1 when fail > stale, so the build stopped there (Prepare Vosk, Gradle, APK never ran).
- v7.36 fix: ONE added block in tools/test_part5_contract.py (skips only that hash line when build.yml is the unpack copy; real zip build.yml is still hash-checked). Simulated in the sandbox with the exact CI layout: before fix 96/12 exit 1, after fix 97/11 exit 0.

## NEXT (do in this order, one step at a time)
1. Owner uploads the v7.36 patch zip (named NOVA-Voice-Assistant.zip) -> watch Actions.
2. If red at "Run unit tests and assemble debug": expect the 3 known stale JUnit tests (tools/KNOWN_STALE_JUNIT.txt) and/or real-Gradle-only problems (android.jar "not mocked", resources, manifest merge, R8). Fix ONLY the red lines, add-only, ask the owner before hiding any test. Gradle / APK / phone are still NOT TESTED.
3. Green = APK in Releases (nova-latest) or artifact nova-apk -> owner runs the PHONE TEST LIST from the v7.35 file.
