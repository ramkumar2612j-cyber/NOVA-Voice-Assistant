# NOVA AUTOPSY - start PART 3 of 6. Paste as first message + attach NOVA-Voice-Assistant.zip
Owner: not a programmer, Android phone only. Hinglish, SHORT, ONE step at a time, full clickable links. Say PASS / FAIL / NOT TESTED, never DONE without evidence. NEVER remove a feature. Zip name exactly NOVA-Voice-Assistant.zip. Repo github.com/devboutique0-wq/NOVA (branch main). After EVERY part: give the zip + next-account prompt.
Parts: 1 router+wiring DONE (docs/AUTOPSY_PART1.md), 2 run path DONE (docs/AUTOPSY_PART2.md), 3 music / WhatsApp / YouTube / calls, 4 connectors / n8n / web browsers / learning, 5 photo / video / recordings / routines / settings, 6 self test + final zip + report.
## Part 2 result
Fixed: screen-off deafness (new switch listenLocked, default ON) and overlay nudge repeat. Updater hash verified. Kotlin compile NOT RUN, phone NOT TESTED. 67 contract scripts PASS; test_vosk_model_packaging needs .github/workflows (not in zip) - ignore.
## PART 3 job
Trace RUN path of music (Spotify/YouTube Music), YouTube, WhatsApp send/read, calls (CALL_PHONE, ANSWER_PHONE_CALLS, contacts). For each find where a permission, missing app, intent resolution or early return silently stops it. Fix only proven bugs, add a test per fix.
## Rules
Build on GitHub Actions (upload `tools` BEFORE `app`), read the test-report artifact. Phone tests one at a time; owner sends only red lines.
