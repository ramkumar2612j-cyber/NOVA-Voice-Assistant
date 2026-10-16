# NOVA v6.99 - PART 2 of 3 DONE in source. Paste as first message + attach NOVA-Voice-Assistant.zip
Owner: not a programmer, Android phone only. Hinglish, SHORT, ONE step at a time, exact github.com links. Say PASS / FAIL / NOT TESTED. NEVER remove a feature. No speaker lock, no PIN unlock. Zip name exactly NOVA-Voice-Assistant.zip. Repo: github.com/devboutique0-wq/NOVA (branch main).
## Plan
- PART 1 DONE: stale HelpGuide "haan" lines fixed; versionName 6.99.
- PART 2 DONE: StopRules.kt (more stop phrases) + stopEpoch: a spoken stop makes the picture / website job drop its result ("Roka gaya").
- PART 3 TODO: Hindi/Hinglish ASR spellings of "download" (OpenWebRules / PlayInstallRules + tests).
## Evidence
Python contracts 63/63 PASS (sandbox, incl. new tools/test_stop_contract.py). Kotlin compile NOT RUN. JUnit NOT RUN (new StopRulesTest has 4 tests). Phone NOT TESTED. GitHub build v6.99 NOT DONE (upload `tools` BEFORE `app`).
## Known limits of Part 2
Network call is not aborted, only its result dropped. Stop not heard while NOVA thinks inside one turn (mic muted by busy) or speaks (echo). learn / publish / connector jobs do not check stop. Phone test: say "nova ek billi ki photo banao", then "ruko".
