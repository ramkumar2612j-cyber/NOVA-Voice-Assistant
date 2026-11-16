# v7.176 (c) APP BUILDER - status and prompt for the next account

## What it does
"nova chhath calendar app banao" (any app): PLAN > deep web RESEARCH > source-pinned FACTS > CODE > checker > PUSH to the owner's GitHub > BUILD (GitHub Actions) > DOWNLOAD > INSTALL.
Live terminal-style screen (Home card APP BUILDER, Skills entry, or "nova app builder kholo"). STOP / "app banana band karo" stops only the build.

## Owner rule (2026-11-13): every app gets deep research, no detail missed, NOTHING fake
- planner AI lists 5-8 sections the app must cover; one web search per section (AnalysisSearchRules.runSearch); top 6 pages are read;
- facts are extracted with source numbers; code drops a fact with no valid source or with a 2+ digit number that no cited source contains;
- fewer than 6 verified facts / 2 sources on a fact topic = app NOT built (honest message);
- gaps are written into the app as "not verified"; SOURCES screen + research date are required in the app;
- checker flags years / phones / e-mails / links not in the fact sheet, one repair pass, leftovers are reported.
Limit (be honest): the code checks numbers, years, phones, mails, links. Plain-text claims are only as good as the AI's reading of the sources.

## Modern UI + complete (owner order 2026-11-14)
Planner gives FEATURE LIST + style; APP_SYSTEM demands modern UI and every feature working; checkHtml flags too-small / placeholder / no theme / no transitions / no safe-area / no localStorage; 2 repair passes. Limit: it is still an HTML app, so no real background services, push notifications or server accounts.

## Evidence
| Item | Result |
|---|---|
| AppBuilderRules.kt compiled with real Kotlin 2.0.21 (-language-version 1.9) and 60 unit checks | PASS |
| AppBuilder.kt compiled against hand-written Android stubs (types / syntax only) | PASS |
| Generated project: manifest + icon XML well formed, no placeholder left, workflow dispatch-only | PASS |
| tools/test_app_builder_contract.py + all 166 older contract tests (same result as the untouched v7.176 zip; 4 browser-render tests + smooth_default time out here in BOTH trees) | PASS / same as before |
| New screen rendered in headless Chromium with a fake bridge (no JS errors, STOP shown while running) | PASS |
| Whole-app Gradle compile (no Android SDK / network here) | NOT TESTED |
| Real phone run, real GitHub token, real Actions build of the generated project, installer | NOT TESTED |
| GitHub token scope: needs repo + workflow (fine-grained: Contents + Workflows write) | NOT TESTED |

## First things to check after the next build
1. NOVA CI compiles (AppBuilder.kt / AppBuilderRulesTest.kt are new; SiteGen.requestHtml is now internal).
2. Say "nova ek chhath calendar app banao" with a GitHub token connected: watch the screen; the generated repo's Actions run must be green.
3. Release asset is an APK; installer opens; app opens and shows SOURCES.
4. Process can be killed while the screen is off (no wake lock): if builds die in the background, add a foreground notification for the build.

## Prompt for the next account
"Take NOVA v7.176 (c) APP BUILDER. Do not remove any feature. Phone-test the App Builder end to end (token with repo + workflow), fix whatever breaks, keep the rule that apps contain only source-backed facts and report PASS / FAIL / NOT TESTED honestly. Then add: a list of built apps, update-in-place of an existing built app, and a wake lock / foreground service for long builds."
