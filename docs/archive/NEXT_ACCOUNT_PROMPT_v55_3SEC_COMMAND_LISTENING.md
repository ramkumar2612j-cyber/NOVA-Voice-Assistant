# NOVA NEXT ACCOUNT PROMPT v55 — FIXED 3-SECOND COMMAND LISTENING

Continue from `NOVA_v15_assistant.zip`, the exact archive this prompt is included in. The owner is not a programmer, uses an Android phone only, and writes Hinglish. Give short, direct Hinglish instructions, one step at a time, exact GitHub links, and end every reply with exactly one `Aapko karna hai: ...` line. Keep the ZIP name `NOVA_v15_assistant.zip`; preserve every existing archive entry, including v54, v53, and v52 prompts; no `__pycache__` or build directories. Never claim CI/compile/phone tests passed unless they ran.

## What changed in v55
1. **Command recording:** after the wake word, NOVA keeps recording while speech continues. It ends a normal spoken command only after **3 continuous seconds of detected silence**, then enters THINKING and processes it. Removed the old early “fast end” shortcut so simple/local commands cannot start thinking before the 3-second window.
2. **Longer commands:** increased the normal-command safety cap from 12 seconds to 60 seconds so long Hinglish commands are not cut off by the old cap. The 60-second limit is a stability/resource safety cap; do not claim truly unlimited recording.
3. **Settings UI:** replaced the configurable 2–10 second WAIT TIME slider with a clear `COMMAND LISTENING · 3 SEC` note so the UI matches the fixed behavior. Dictation and yes/no-answer capture retain their own separate timing rules.
4. **Tests:** added `tools/test_three_second_command_listening.py`; updated pure Kotlin tests for the fixed 3-second threshold and longer command cap.

## Evidence / do not overclaim
- Run all `tools/test_*.py`: the expected local contract count is now **31**; the Hindi contract test must run without `-I`, all others with `python3 -I` as established by v54.
- Kotlin/Gradle compile and JUnit can only be claimed after running a real compiler/CI. If no Android SDK/Gradle wrapper is available locally, mark them `Check nahi hua` and tell the owner to upload this ZIP, then open Actions: https://github.com/skfashion142/NOVA-Voice-Assistant/actions.
- Physical phone behavior is unverified until an APK built from green CI is installed and tested. Test a long command with pauses, then stop speaking; NOVA should continue listening during the speech and start thinking only after three seconds of silence. Also test a normal short command and a WhatsApp send: risky `Send` confirmation remains intentionally required.

## Required next actions
1. Inspect CI build/test output from the owner. Fix only the cause evidenced by the log; rerun static tests and rebuild the ZIP.
2. Preserve all original entries and all safety rules from v54: no silent permission grants, installs, publishing, payments/account changes; never expose keys; do not automate secure screens; do not rewrite NOVA's own code from a task; do not bypass voice/face checks.
3. Keep the ZIP named `NOVA_v15_assistant.zip`. GitHub upload: https://github.com/skfashion142/NOVA-Voice-Assistant/upload/main.
