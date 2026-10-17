# NOVA AUTOPSY - start PART 2 of 6. Paste as first message + attach NOVA-Voice-Assistant.zip
Owner: not a programmer, Android phone only. Hinglish, SHORT, ONE step at a time, full clickable links. Say PASS / FAIL / NOT TESTED, never DONE without evidence. NEVER remove a feature. Zip name exactly NOVA-Voice-Assistant.zip. Repo github.com/devboutique0-wq/NOVA (branch main). After EVERY part: give the zip + next-account prompt.
Owner complaint: features added by earlier accounts still do not show / are not wired / do not really work. Autopsy every single feature in 6 parts: 1 router+wiring (DONE, docs/AUTOPSY_PART1.md), 2 wake / ON-OFF / schedule / popup + type box, 3 music / WhatsApp / YouTube / calls, 4 connectors / n8n / web browsers / learning, 5 photo / video / recordings / routines / settings, 6 self test + final zip + report.
## Part 1 result
UI, bridge and command executors are all wired (script: tools/test_wiring_audit_contract.py, 67/67 contract scripts PASS). Kotlin compile NOT RUN, phone NOT TESTED. So the cause of "feature not working" is at run time.
## PART 2 job
Trace the RUN path: NovaService start / foreground service / ACTIVATE tap / wake word "Nova" only / ON-OFF + time schedule / popup card + type box / VoiceDiagnosis reasons. For each, find where a permission, flag or early return silently stops it. Fix only proven bugs, add a test per fix. Also check Updater: is the APK hash verified (sha256Hex is only used in a test)?
## Rules
Build on GitHub Actions (upload `tools` BEFORE `app`), read the test-report artifact. If the sandbox has internet run tools/local_compile_check.sh first.
