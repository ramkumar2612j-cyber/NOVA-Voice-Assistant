# NOVA AUTOPSY - start PART 4 of 6. Paste as first message + attach NOVA-Voice-Assistant.zip
Owner: not a programmer, Android phone only. Hinglish, SHORT, ONE step at a time, full clickable links. Say PASS / FAIL / NOT TESTED, never DONE without evidence. NEVER remove a feature. Zip name exactly NOVA-Voice-Assistant.zip. Repo github.com/devboutique0-wq/NOVA (branch main). After EVERY part: give the zip + next-account prompt.
Parts: 1 router+wiring DONE (docs/AUTOPSY_PART1.md), 2 run path DONE (docs/AUTOPSY_PART2.md), 3 music / WhatsApp / YouTube / calls DONE (docs/AUTOPSY_PART3.md), 4 connectors / n8n / web browsers / learning, 5 photo / video / recordings / routines / settings, 6 self test + final zip + report.
## Part 3 result
Fixed: music and real-call false success (raw startActivity from background is blocked silently; now launch() verifies, honest reason, dialer fallback for calls). Kotlin compile NOT RUN, phone NOT TESTED. 68 contract scripts PASS; test_vosk_model_packaging needs .github/workflows (not in zip) - ignore.
Open from Part 3: WhatsApp READ (notification path) not traced; YouTube play_music only opens search results; first build must be read on GitHub Actions (compile unproven for the 2 edits in NovaService.kt: launch/lastLaunchBlocked, musicPlay, callTool).
## PART 4 job
Trace RUN path of connectors (ConnectorApi/ConnectorStore/n8n), web browsers (WebBrowsers, OpenWebRules, Chrome tabs), learning (LearnRules, TeachStore, AutoLearn). For each find where a missing key, permission, network call, early return or swallowed exception silently stops it. Fix only proven bugs, add a test per fix.
## Rules
Build on GitHub Actions (upload `tools` BEFORE `app`), read the test-report artifact. Phone tests one at a time; owner sends only red lines.
