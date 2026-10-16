# NOVA v6.99 - PART 1 of 3 DONE in source. Paste as first message + attach NOVA-Voice-Assistant.zip
Owner: not a programmer, Android phone only. Hinglish, SHORT, ONE step at a time, exact github.com links. Say PASS / FAIL / NOT TESTED. NEVER remove a feature. No speaker lock, no PIN unlock. Zip name exactly NOVA-Voice-Assistant.zip. Repo: github.com/devboutique0-wq/NOVA (branch main).
## Plan (owner asked: remaining work in 3 parts, zip + prompt after each)
- PART 1 DONE: stale HelpGuide "haan" lines (call, whatsapp) fixed in HelpGuide.kt + ui.html CAPS; versionName 6.99.
- PART 2 TODO: spoken STOP must interrupt a running turn (NovaService).
- PART 3 TODO: Hindi/Hinglish ASR spellings of "download" (OpenWebRules / PlayInstallRules + tests).
## Evidence
Python contracts: run in sandbox (help contract PASS, runtime contract PASS). Kotlin compile: NOT RUN. JUnit: NOT RUN. Phone: NOT TESTED. GitHub build for v6.99: NOT DONE yet (upload `tools` BEFORE `app`).
## Known limits (unchanged from v6.99 FINAL prompt)
Smart reply holds the listener thread up to 8 s; needs NovaService + AI key.
