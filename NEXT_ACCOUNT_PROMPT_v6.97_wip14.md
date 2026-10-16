# NOVA next account prompt (v6.97, after wip13) - paste as first message and attach this ZIP

Owner: not a programmer, Android phone only (Chrome + GitHub website). Reply in Hinglish, short, ONE step at a time, tap-by-tap with exact github.com links. Full files in chat or a full ZIP every time, with a direct download. Say clearly PASS / FAIL / NOT TESTED. NEVER remove an existing feature: only fix a bug when one is found. Do NOT add speaker recognition or voice lock.

Repo: github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant, branch wip13 (16 commits ahead of main). Workflow "NOVA Android CI" (.github/workflows/build.yml).

## CURRENT STAGE (verified from the owner's screenshots, 16 Oct 2026)
- Run #20 "Add files via upload" (commit a5d1ca0, branch wip13): SUCCESS, 1m 47s. Artifacts: nova-apk (55.8 MB), test-report (129 KB). "NOVA v6.97 built, versionCode 2020", testDebugUnitTest + assembleDebug green.
- An earlier run on wip13 FAILED only because the owner uploaded `app` before `tools`: 3 Python contract scripts were stale (test_hud_controls, test_part3_stop_silent_contract, test_part5_ui_screens). They pass in the ZIP you are reading: `bash tools/run_all_checks.sh` = 58 passed, 0 failed (run in the sandbox this session).
- Owner installed the APK on the phone: STOP / SILENT / HAAN-NAHI work. Everything else in v6.97 is as in CHANGELOG.md.
- main still holds the old "v6.97 wip10" commit. wip13 is NOT merged yet (owner will press "Compare & pull request" himself after the phone test).

## SIX-PART PLAN (owner wants a ZIP + this prompt after EVERY part; do not skip a part, do not delete any old feature)
- PART 1 DONE: OpenWebRules.kt (pure rules: which sites, Chrome vs app, many sites, download wording) + OpenWebRulesTest.kt. Compiled with real kotlinc, 15/15 new tests PASS. NOT wired into NovaService yet. NOT TESTED on a phone.
- PART 2 WRITTEN, NOT COMPILED, NOT RUN (owner stopped the session to take the ZIP): edits in NovaService.kt only:
  (a) handle(): new block `val webReq = OpenWebRules.parseOpen(text)` -> `openWeb(webReq)` placed right before `AgentRules.isTask(text)`;
  (b) runCommand "open_app" and runTool "open_app" now call `openAppOrSite(name)` (old `openApp` is unchanged and still called first when an installed app matches);
  (c) new private functions before the "contacts, call, sms, WhatsApp" section: `CHROME_PKG`, `appInstalled`, `openChromeTab`, `openAppOrSite`, `openWeb`.
  Also NEW test file OpenWebRoutingTest.kt (earlier handlers must not steal the web phrases and vice versa) - never compiled either.
  FIRST JOB FOR THE NEXT ACCOUNT: run the full compile (bash tools/local_compile_check.sh <workdir>), fix any compile error or failing OpenWebRoutingTest in Part 2, confirm the same 24 org.json-only failures, then continue. Known thing to double check: openAppOrSite guesses https://www.<word>.com for an unknown single word when no app matches (owner asked "koi bhi website"); say so honestly in the reply (already worded that way).
- PART 3: PlayInstallRules.kt (pure: 60 s armed window per package, decide(lines) -> TAP / STARTED / INSTALLED / NEED_LOGIN / PAID / WAIT, pageMatches, search-result name match) + tests.
- PART 4: NovaAccessibilityService.playInstall + NovaService.downloadApp. Known package -> market://details?id=<pkg>. UNKNOWN app name (owner: "koi bhi app") -> market://search?q=<name>&c=apps, tap the first result ONLY if its title matches the spoken name exactly (cleaned words), then the same page-match + exact "Install" press. No match -> say so and leave the page open, never guess.
- PART 5: verify WhatsApp direct send is untouched (skipOrderConfirm/skipRiskyConfirm default ON, ambiguous contact still asks), speed review of the new path (no AI call, no double transcription), full regression: Python 58+, JUnit same 24 org.json-only failures. Fix only real bugs found.
- PART 6: tools/test_open_web_download_contract.py, CHANGELOG "v6.97 wip14", docs test report with PASS/FAIL/NOT TESTED, final ZIP + prompt.

## WHAT THE OWNER ASKED NEXT (Part 1 rules are in the ZIP; wiring is Parts 2-4)
Owner words (Hinglish): NOVA must do the job first and tell afterwards, fast, no delay; no permission questions.
1. "youtube kholo" -> the YouTube APP opens. "youtube Chrome pe kholo" -> opens in Chrome. Any website name -> Chrome. 4-5 websites in one sentence -> each in its OWN Chrome tab ("youtube, google aur flipkart chrome pe kholo").
2. "whatsapp download karo" (any app name) -> Play Store app page opens at once, NOVA presses Install, and says "download start ho gaya sir" ONLY after it really saw the download begin (honest, no fake success).
3. "Anand ko WhatsApp bhejo, kitni der mein niklo ga" -> sent without asking, then NOVA reports. ALREADY TRUE: ExtCfg.skipOrderConfirm / skipRiskyConfirm default to ON, so askFirst(..., must = true) runs at once. Do not change; just keep the two contract checks (test_part2_security, test_tool_verify_contract) passing. Ambiguous contact names (two Anands) must still ask which one.

## DESIGN ALREADY DECIDED (read the code first, it matches these places)
- Handler placement: NovaService.handle(), right BEFORE `AgentRules.isTask(text)` (after MusicPlayRules and AppSearchRules so "youtube kholo aur song play karo" keeps working). Local rules, no AI call = fast.
- New pure Kotlin object `OpenWebRules` (parseOpen, parseDownload, siteUrl): reject negations and any sentence with play/search/message/call/bhejo/download/alarm/camera/etc words so other handlers keep ownership. Single name without "chrome/website/site/tab" -> return null (existing open_app path stays). Split on aur / and / comma; greedy match of known site names when no separator is spoken. Cap 8 targets.
- Chrome tab: Intent(ACTION_VIEW, uri).setPackage("com.android.chrome"), NO Browser.EXTRA_APPLICATION_ID extra (that would reuse one tab), 600 ms between tabs, fallback to the default browser if Chrome is missing. Unknown single word in website mode -> https://www.<word>.com; unknown multi-word -> Google search.
- open_app fallback: in runCommand "open_app" (and runTool open_app) call openApp first; only when NO installed app matches AND the name is a known site/domain, open it in Chrome. Never change openApp itself.
- Play Store download: Control.blocked() keeps com.android.vending BLOCKED (SafetyInvariantsTest, ScreenSessionTest, AgentRulesPart6Test pin it; voice "tap" must stay refused there). Add a SEPARATE narrow path: `PlayInstallRules` (pure: arm/armedFor 60 s window per package, decide(lines) -> TAP / STARTED / INSTALLED / NEED_LOGIN / PAID / WAIT, pageMatches) and `NovaAccessibilityService.playInstall(pkg, label)`. It may press ONLY an exact "Install" label, ONLY in com.android.vending, ONLY for a package from the built-in known-package map (details page market://details?id=<pkg>), ONLY while armed, ONLY when the page title matches the app name; the top-most exact Install node wins. Never presses a price/Buy button, Sign in, "Install anyway", Update. Unknown app name -> open Play Store search (market://search?q=...&c=apps) and say honestly that the owner must pick and press Install.
- Result words must be honest: "download start ho gaya sir" only when Cancel/Installing/Downloading/Pending (or Open+Uninstall) was seen after the tap; otherwise "Install dabaya par pushti nahi hui, Play Store mein dekh lo". Already installed -> say so, do not open Play Store. Several apps in one sentence -> do the first, tell the owner to ask for the rest.
- Tests to add: OpenWebRulesTest.kt, PlayInstallRulesTest.kt, a routing test proving SiteIntent/ConnectorRules/GmailRules/AppSearchRules/MusicPlayRules/LearnRules/ImageIntent/LocalSkills/HelpGuide return null for the new phrases, and tools/test_open_web_download_contract.py (static: Control.blocked untouched, playInstall only exact "Install", handler before AgentRules.isTask, 60 s window).
- Add a CHANGELOG entry "v6.97 wip14" (do NOT bump versionName: tools/test_android_runtime_contract.py pins it), and docs/TEST_REPORT note with real PASS/FAIL/NOT TESTED.

## HOW TO COMPILE + TEST IN THE SANDBOX (worked this session)
- `tools/run_all_checks.sh` -> Python contracts (58/58 PASS at wip13).
- Real compile + JUnit without Gradle: github.com is reachable, Maven Central is NOT. kotlinc 1.9.24 from the GitHub release zip, android.jar from `git clone --depth 1 --filter=blob:none --sparse https://github.com/Reginer/aosp-android-jar.git` then `git sparse-checkout set android-34` (raw.githubusercontent.com is blocked). Run `bash tools/local_compile_check.sh <workdir>` (about 2 minutes).
- Baseline result on THIS zip: 834 tests run, 24 failures. ALL 24 are org.json problems (ConnectorParseTest 11, WebBrowsersTest 6, WebSourcesTest 6, ConnectorRulesTest 1): the sandbox has only the android.jar stub of org.json because the real jar is on Maven Central (blocked). GitHub Actions uses the real `org.json:json:20240303` and run #20 is green, so these are an environment limit, NOT app bugs. After any change the failure count must stay at the same 24 (same classes) and every other test must pass.

## STATUS TABLE (honest)
- GitHub Actions build + unit tests (wip13 #20): PASS
- Python contracts 58/58 (sandbox): PASS
- Kotlin compile + JUnit (sandbox): 810 pass, 24 fail = org.json stub only
- Phone: STOP, SILENT, HAAN/NAHI = owner says they work; all other v6.97 features NOT TESTED by Claude
- OpenWebRules (Part 1): compile + 15 JUnit PASS in sandbox. Part 2 wiring (NovaService): WRITTEN, NOT COMPILED, NOT RUN. Play Store download (Parts 3-4): NOT STARTED. Python contracts 58/58 were re-run after Part 1 only.

## OLDER PENDING (from NEXT_PART_PROMPT.md, unchanged)
Part B more free AIs in the chain; Hinglish+Hindi+English mixed understanding; separate Groq key for self-improve; scheduled voice tasks; silent automatic fallback + notification; NOVA own issue-check report. Tell the owner the NEW requests above come first.

## ARCHITECTURE NOTES FOR THE NEW WORK
- NovaService.launch(i, expectPkg, what) is the one way to start an activity (service, Accessibility and PendingIntent fallbacks, honest result). Use it for every tab.
- R(ok, msg) is the result type; tr(hindi, english) picks the language; claimAction() blocks double sends; WhatsAppTarget has the two WhatsApp package ids.
- QUERY_ALL_PACKAGES is in the manifest, so getApplicationInfo / getLaunchIntentForPackage work for any package.
- Accessibility config only listens to window-state events, but rootInActiveWindow works anyway; `collect()` and `findLabel()` in NovaAccessibilityService.companion are private helpers you can reuse inside playInstall.
