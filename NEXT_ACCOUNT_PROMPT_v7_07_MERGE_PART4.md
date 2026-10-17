# NOVA - NEXT ACCOUNT PROMPT (v7.07 MERGE, state after PART 4 of 6)
Paste FIRST + attach (1) NOVA-Voice-Assistant.zip (this zip = repo wip13 + merge parts 1-4 already applied) and, if you want to verify, (2) nova_part4_patch.txt (cumulative patch against the owner's real repo wip13).
Also read inside the zip (older rules, all still apply): NEXT_ACCOUNT_PROMPT_v7_06_NOCOVER_FIX.md, NEXT_ACCOUNT_PROMPT_v7_05_H_I_NOCOVER.md, docs/MERGE_PART1_REPORT.txt (full file-by-file compare).

## OWNER + RULES (compulsory, repeat in every prompt)
- Owner is NOT a programmer. Android phone only (Termux from F-Droid, Chrome, GitHub web). Language: Hinglish. SHORT answers, ONE step at a time, clickable links, copyable files (no long pastes).
- RULE #1: NO DELETION. Only add / upgrade. Do not change old files unnecessarily. Old features stay. New features must be fully REAL and working, nothing half-done, nothing faked.
- Report PASS / FAIL / NOT TESTED with evidence. Never claim a compile or phone test that was not run. Never touch .github/workflows/build.yml.
- New JS: document.getElementById (not $('x') / g('x')). Bridge String params capped with .take(N).
- Owner decisions in force: NO voice-lock restore, NO restore of the removed payment / password block or the risky-button confirmation. Duplicate left HOME tab: do not hide without asking.
- Every hand-over = full ZIP named NOVA-Voice-Assistant.zip + this kind of full next-account prompt.
- GitHub account for the repo: ramkumar2612j-cyber (NOT skfashion142). Repo https://github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant, branches main, wip13, backup-old. Owner is on wip13 locally (~/NOVA-Voice-Assistant in Termux). OPEN QUESTION: which branch the CI / APK builds (main or wip13). Ask once if still unknown.

## WHY A MERGE
The owner's repo (wip13) and my earlier zip were TWO DIFFERENT LINES. Decision taken: the REPO is the base (it is the owner's real line). Everything in the repo stays. Only my NEW FEATURES are ported on top, feature by feature, each hook checked by symbol grep. Where both lines implemented the same thing in different ways, the REPO version stays and mine is skipped (list below). No kotlinc exists in the sandbox (no network), so nothing here is compiled.

## 6-PART PLAN AND STATUS
1. PART 1 file compare: DONE. docs/MERGE_PART1_REPORT.txt. Repo-only 60 files (kept), mine-only 94 files, 86 files differ (all real content differences, none whitespace-only).
2. PART 2 add the 94 mine-only files: DONE. +4836 lines, 0 deleted. Includes OpenWebRules, PublishTarget, VercelPublish, PhotoPro, PhotoEditIntent, MediaPlace, SiteMediaStore, MediaIntent, PlayInstallRules, StopRules, SocialReplyRules, PaymentReplyRules, PhoneActions, PhoneFacts, PhoneVoiceRules + their JUnit tests + many tools/*.py guards + docs. NOTE: many of these are NOT wired yet (they sit unused until parts 5-6 hook them).
3. PART 3 Vercel / Lovable publish: DONE in NovaService.kt: voice hooks (two: normal + cloud-listener text) -> publishTargetBegin / publishVercelRun / lovableHandoff inserted before publishRun. Uses ConnectorStore.connected/perm/token, ConnectorCatalog.VERCEL/allows (all exist in repo, verified by grep). Vercel asks first (HAAN/NAHI), needs WRITE permission; Lovable = clipboard + open + 3 honest manual steps, no passwords typed.
4. PART 4 Chrome tabs / app download / 28 more known apps: DONE. NovaService.kt: hooks parseOpen -> openWeb, parseDownload -> downloadApp (inserted before the "General project/research/creation jobs" route), functions appInstalled / openChromeTab / openAppOrSite / openWeb / downloadApp (before the contacts section), and "open_app" -> openAppOrSite (falls back to the old openApp, so old behaviour is kept). NovaAccessibilityService.kt: 134-line Play Store install path (playRoot, playInstall, playOpenResult, collectPlayHits ...) inserted in the companion object; Control.blocked is untouched, so voice tap/type in Play Store stays refused. HelpGuide.kt: topics chrome + download + PRIORITY lookup (old lookup kept as the fallback). FeatureCheck.kt: 2 OpenWebRules self-checks + "App download" feat line. Safety gate PlayInstallRules (exact title match for unknown apps) was NOT loosened.
5. PART 5 TO DO: photo editor, gallery, HUD (see list below).
6. PART 6 TO DO: ui.html, AndroidManifest, remaining HelpGuide/FEATURE_INVENTORY lines (ask first), run all checks, final patch.

## EVIDENCE SO FAR (honest)
- Cumulative change vs repo wip13: only 2 repo lines replaced (NovaService "open_app" line, HelpGuide one lookup line, both keep the old behaviour as fallback); everything else is additions. git apply --check of nova_part4_patch.txt on a clean copy of repo wip13: OK.
- Crude brace/paren balance of NovaService.kt equals the repo's own baseline; NovaAccessibilityService.kt balanced.
- Python guards: test_download_spellings_contract PASS, test_music_play_contract PASS. FAIL (expected, ui.html not ported yet): test_open_web_download_contract (ui.html MADAD list must have chrome + download, Skills screen must show both example commands, ui.html must show the Accessibility need for download), test_help_contract (Kotlin topics vs ui.html CAPS ids/examples/titles differ), test_photo_publish_contract (needs MainActivity editPhoto / editedToSite - part 5). Known old FAIL: test_vosk_model_packaging.
- Kotlin compile: NOT RUN. JUnit: NOT RUN. GitHub Gradle CI: NOT RUN. APK: NOT BUILT. Real phone: NOT TESTED (publish, Chrome tabs, app download, voice commands).

## PART 5 - WHAT IS STILL TO PORT (use find-by-name; keep ALL repo code)
NovaService.kt (my side, not yet in repo):
- mediaRoute(text) + its two hooks (website with photo words / "video banao"; one in the main route before the music/others, one for groqText), MediaIntent.Kind.VIDEO branch, SiteMediaStore.applyText(rawSaid) inside the website request, REQ_SITE_MEDIA / siteMedia* bridge, editPhoto / editedToSite / editPhone / editAi / editReport (these live in MainActivity + NovaService), userBlock / applyUser.
- stopEpoch + StopRules.wasStopped around picture / website / learn jobs (+3 places), isAliveNow() (used by ScheduledTaskReceiver), overlayNudgedAt 10-minute re-nudge, alarm_failed text (needs ScheduledTaskStore.scheduleAlarmState from my side), speak / speak_end HUD events (v7.03), driving-mode skipOrderConfirm text, VideoRecordService.isBusy() checks, image-gen failure reason text, LearnStore.addKept returning keptId, PhoneVoiceRules.K_SHOT branch.
NovaHud.kt: repo is bigger (onAnswer, onTyped, silentOn, song popup). Re-add only my v7.03 future HUD blocks (fut_*) without removing the repo HUD code. NovaService does `hud = NovaHud(this).also { ... }` in the repo: keep that, add my events.
MainActivity.kt: 171 lines of mine are inserted-only (gallery picker, photo editor, site media). Port by name. Repo MainActivity is 123214 bytes, mine 129933.
SiteGen.kt / SiteMedia.kt / SiteIntent.kt: the repo has a DIFFERENT, newer media design (lastSiteTemplate, SiteMedia.loadLast/saveLast/count, SiteMedia.parseEdit). Mine uses lastSiteRawHtml + SiteMediaStore + MediaPlace. COMPETING DESIGNS: keep the repo's, adapt SiteMediaStore / MediaPlace to the repo's API instead of replacing it. If it cannot be done add-only, show the owner both and ask ONE short question.
Small files where mine has AUTOPSY fixes the repo lacks (port only if no conflict, each with its test): ScheduledTaskStore (missed one-shot jobs, alarm state), ScheduledTaskReceiver, NovaNotificationService (onListenerDisconnected rebind, isolated helpers, CallHub.onRemoved), VideoRecordService (notifyResult), ExtCfg, Control, BootReceiver, RecordSession, ImageGen.

## SKIP (repo version stays, do NOT port mine - different/newer in repo)
SmartReplyRules (+Test) [repo has Part 7/8 API parse/command/gate; mine has allowedForAi/choose/useAi], PaymentReply wiring in NovaNotificationService autoReply (the file PaymentReplyRules.kt is present but unwired; wiring conflicts with the repo's smartAutoReply, ask first), MusicPlayRules(+Test), PhoneActionRules(+Test) (repo parseAll up to 3 actions), FreeProviders, FeatureStatus, SiteIntent, ConnectorApi/Catalog, UnderstandBudget/WebContext/GeminiSpeed users, silent_on / smart_on / autoreply_on voice commands, song-choice popup, richTurn, runFreeChain(rich, web).

## PART 6 - TO DO
- ui.html (repo 336977 bytes, mine 358065): keep everything from the repo, append my blocks by name: nova-nocover-css, nova-nocover-css2 (`#app.wrap{padding-bottom:calc(96px + env(safe-area-inset-bottom,0px))!important}`, reason: an older `#app.wrap{padding-bottom:10px!important}` hid the end of HOME under the tab bar), nova-polish-css (touch-action manipulation, press feedback, focus ring, overscroll contain), nova-photo-pro-*, nova-site-media-*, fut_* HUD blocks, plus MADAD / Skills entries for chrome + download and the Accessibility-need line (fixes test_open_web_download_contract and test_help_contract). Add JS only with document.getElementById.
- AndroidManifest.xml: repo 7612 vs mine 7765 bytes (repo 3 lines only in repo, mine 5 only in mine) - show both, add mine additively.
- Re-run: python3 tools/run_all_checks.sh, tools/ui_owner_check.py, tools/ui_scroll_audit.py (49 PASS at 390x844, 360x640, 844x390, needs Playwright), tools/test_no_feature_loss_contract.py. Removed lines vs the REPO version must be 0 (except the 2 documented fallbacks).
- If kotlinc 1.9.24 + android-34 android.jar + org.json jar can be downloaded: JSON_JAR=<jar> bash $PWD/tools/local_compile_check.sh <ABSOLUTE workdir>, fix every error. Never claim PASS without it.
- Deliver: git diff patch against repo wip13 (+ .txt copy, owner downloads on a phone), verified with git apply --check on a clean copy of repo wip13, plus full zip NOVA-Voice-Assistant.zip and an updated prompt.
- HelpGuide / FEATURE_INVENTORY lines for gallery, photo editor, Vercel / Lovable, space orb: edits old files, ask first. Extra HelpGuide topics from my side (safety, social, callanswer, phonecontrol, smartreply, calendar) were NOT ported (depend on unported features).

## WHAT THE OWNER WANTS (product)
NOVA does any task by voice or text (Hindi / Hinglish / English), opens any website, downloads the exact app he names at once, understands everything he says, polished UI, as fast as possible; finally a GitHub-ready zip and a working APK. NovaService.systemPrompt already asks the AI to work out the intent of long casual speech; real free-speech understanding needs internet + a Gemini key. Collect failing sentences from the phone, then add words / rules add-only with JUnit. Speed NOT measured: owner runs Settings > SPEED and sends the numbers.

## HOW THE OWNER PUSHES (Termux)
```
cd ~/NOVA-Voice-Assistant
git status -s
git apply --check ~/storage/downloads/<patch>.txt && echo OK
git apply ~/storage/downloads/<patch>.txt
git add -A
git commit -m "v7.07 merge"
git push        # username ramkumar2612j-cyber, password = personal access token (repo permission)
```
Then GitHub > Actions > "NOVA Android CI" > Run workflow. Green: Artifacts > nova-apk > install app-debug.apk. Red: owner sends ONLY the red lines; fix add-only. Give him ONE command or step at a time and wait for his output. Do NOT push anything until Part 6 is done and the owner says so.
