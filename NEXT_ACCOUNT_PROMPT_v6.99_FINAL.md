# NOVA v6.99 FINAL (5-part plan DONE in source) - paste as first message and attach NOVA-Voice-Assistant.zip

Owner: not a programmer, Android phone only (Chrome + GitHub website). Hinglish, short, ONE step at a time, tap-by-tap with exact github.com links. Say clearly PASS / FAIL / NOT TESTED. NEVER remove an existing feature. No speaker recognition / voice lock. No PIN / password phone unlock. Zip name exactly NOVA-Voice-Assistant.zip.

## WHAT IS IN THIS ZIP (all written in source, NONE compiled, NONE phone tested)
1. Call uthana / kaatna by voice (PhoneVoiceRules, CallHub).
2. Voice screenshot, ringer silent / vibrate / normal, rotation on / off (+ Skills cards).
3. AI smart reply (ExtCfg.smartReply default OFF, NovaService.smartReply 8 s timeout, SmartReplyRules.useAi / pick, history shows AI or fixed).
4. UI buttons for 15 settings (self test, image tests, clear errors, wait seconds, memory text copy, auto-learn, browser TEST, calendar allow, chat list fallback). Bug fixed: duplicate id al_on.
5. Help / knowledge: 24 HelpGuide topics = ui.html CAPS, FeatureCheck Samajh + readiness rows for the new features.
Details: CHANGELOG.md top three entries.

## STATUS TABLE (honest)
- Python contracts 62/62: PASS (sandbox)
- ui.html JS syntax (node --check): PASS for all new code (one old vendored block fails the same way in the original zip)
- Kotlin compile: NOT RUN (sandbox had no kotlinc / internet)
- JUnit (new: SmartReplyRulesTest 13, HelpGuideTest +2, PhoneVoiceRulesTest, OpenWebRules*, PlayInstallRules*): NOT RUN. Old baseline: 24 org.json-stub failures only in a sandbox without the real jar; GitHub Actions uses real org.json
- Phone: NOT TESTED for everything above
- Known limits: smart reply asks the AI on the notification-listener thread (up to 8 s hold) and needs NovaService running + an AI key, else fixed text; sender name goes to the AI with the text; HelpGuide whatsapp / call topics still say NOVA asks "haan" (stale, owner not asked).

## FIRST JOB
Build on GitHub Actions (upload `tools` BEFORE `app`, otherwise stale Python contracts fail), read the test-report artifact, fix the exact compile / test errors only. Do NOT add features until the build is green and the owner has tested on the phone: call uthao, screenshot lo, ringer silent, rotation off, AI SMART JAWAB switch, SELF TEST CHALAO, YAAD KA TEXT COPY, browser TEST buttons, CALENDAR ALLOW.
If a sandbox with kotlinc exists: `bash tools/local_compile_check.sh <workdir>` (about 2 min), failures must stay at the same 24 org.json classes (ConnectorParseTest, WebBrowsersTest, WebSourcesTest, ConnectorRulesTest).

## OLDER PENDING (not started, one item at a time, ask owner first)
Spoken STOP cannot interrupt a running turn; Hindi/Hinglish ASR spelling of "download" not tested; stale HelpGuide "haan" lines; repo is github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant (check with owner: older notes mention skfashion142).
