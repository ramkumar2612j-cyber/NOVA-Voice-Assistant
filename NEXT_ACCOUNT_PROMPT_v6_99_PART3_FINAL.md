# NOVA v6.99 - ALL 3 PARTS DONE in source. Paste as first message + attach NOVA-Voice-Assistant.zip
Owner: not a programmer, Android phone only. Hinglish, SHORT, ONE step at a time, exact github.com links. Say PASS / FAIL / NOT TESTED. NEVER remove a feature. No speaker lock, no PIN unlock. Zip name exactly NOVA-Voice-Assistant.zip. Repo: github.com/devboutique0-wq/NOVA (branch main).
## Done in this finish (see CHANGELOG.md top four entries)
1. Stale HelpGuide "haan" lines (call, whatsapp) fixed; versionName 6.99.
2. StopRules.kt + stopEpoch: spoken stop (ruk jao, roko, bas karo ...) makes picture / website jobs drop their result ("Roka gaya").
3. Download / install spellings: DL_SPELLINGS (33) incl. Devanagari forms daunlod / instol, "down load"; HelpGuide keys.
## Evidence
Python contracts 64/64 PASS (sandbox). Kotlin compile NOT RUN. JUnit NOT RUN (new: StopRulesTest 4, OpenWebRulesTest +3). Phone NOT TESTED. GitHub build for these 3 parts NOT DONE.
## FIRST JOB
Build on GitHub Actions (upload `tools` BEFORE `app`), read the test-report artifact, fix only the exact compile / test errors. No new features until green + owner phone tests: call uthao, screenshot lo, ringer silent, rotation off, AI SMART JAWAB, SELF TEST CHALAO, "nova ek billi ki photo banao" then "ruko", "nova whatsapp download karo" (also said in Hindi).
## Known limits
Stop is not heard while NOVA thinks in one turn (mic muted) or speaks (echo); network call not aborted, result dropped; learn / publish / connector jobs ignore stop. Real Vosk/Groq spelling of "download" on the phone unknown. Smart reply holds listener thread up to 8 s. Repo owner name: check (older notes: ramkumar2612j-cyber, skfashion142).
