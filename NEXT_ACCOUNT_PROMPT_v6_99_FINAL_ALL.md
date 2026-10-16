# NOVA v6.99 FINAL (5 steps DONE in source). Paste as first message + attach NOVA-Voice-Assistant.zip
Owner: not a programmer, Android phone only. Hinglish, SHORT, ONE step at a time, exact github.com links. Say PASS / FAIL / NOT TESTED. NEVER remove a feature. No speaker lock, no PIN unlock. Zip name exactly NOVA-Voice-Assistant.zip. Repo: github.com/devboutique0-wq/NOVA (branch main).
## Done (details: CHANGELOG.md top five entries)
1. HelpGuide call / whatsapp wording: do first, tell after (defaults already skip confirm); versionName 6.99.
2. StopRules.kt + stopEpoch: spoken stop drops picture / website results.
3. Download / install spellings (33, incl. Devanagari daunlod / instol).
4. PhoneFacts.kt: phone model, Android version, 11 accesses allowed or missing + tap paths, in the online AI prompt.
5. PaymentReplyRules.kt: money talk in chat auto reply = safe fixed line by kind, codes (OTP, PIN) = no reply, never asks, never AI.
## Evidence
Python contracts 66/66 PASS (sandbox). UI bridge audit: 188 calls all matched (static). Kotlin compile NOT RUN. JUnit NOT RUN (new: StopRulesTest 4, OpenWebRulesTest +3, PhoneFactsTest 3, PaymentReplyRulesTest 4). Phone NOT TESTED. GitHub build NOT DONE.
## FIRST JOB
Build on GitHub Actions (upload `tools` BEFORE `app`), read the test-report artifact, fix only exact compile / test errors. No new features until green + phone tests (call uthao, screenshot lo, ringer silent, rotation off, SELF TEST CHALAO, photo banao then ruko, whatsapp download karo).
## Known limits
Android permission boxes and Accessibility / Notification-access switches cannot be skipped. Payment-like taps still ask. Stop not heard while NOVA thinks or speaks. PhoneFacts only in online AI chain. Real Vosk/Groq spelling of "download" unknown. Repo owner name: check with owner (older notes: ramkumar2612j-cyber, skfashion142).
## REMAINING WORK (honest, in order)
1. BUILD: owner pushes with Termux (git push, CI build.yml runs). Read the failed step's red lines only, fix exact compile / test errors. Size unknown: nothing was ever compiled.
2. PHONE TESTS, one at a time: call uthao, screenshot lo, ringer silent, rotation off, SELF TEST CHALAO, photo banao then ruko, whatsapp download karo (also Hindi), WhatsApp paise ki baat (auto reply on) and OTP message (no reply), AI SMART JAWAB, driving mode reply.
3. KNOWN LIMITS (not bugs to fix without owner ok):
   a. Stop is not heard while NOVA thinks or speaks (mic muted). Publish / connector jobs cannot be stopped once started.
   b. DEFAULTS: skip_order_confirm and skip_risky_confirm are TRUE, so NOVA never asks yes/no (also not for payment-like taps in the screen agent). Owner wants act first, tell after. Turning confirm ON in Settings brings the questions back. Tell owner once, do not change.
   c. PhoneFacts only reaches the online AI chain.
   d. Real Vosk / Groq spelling of "download" unknown: add from history text.
   e. Repo: devboutique0-wq/NOVA per latest notes (older: ramkumar2612j-cyber, skfashion142); Termux repo ~/NOVA-Voice-Assistant, branch wip13.
