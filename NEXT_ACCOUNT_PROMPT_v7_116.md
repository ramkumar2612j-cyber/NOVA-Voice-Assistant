# NOVA v7.116 continuation prompt

Start from the v7.116 zip. `NEXT_ACCOUNT_PROMPT_v7_110_FINAL.md` (owner, hard rules) and `NEXT_ACCOUNT_PROMPT_v7_115.md` (open phone-data items) still apply. What changed: `PART_STATUS_v7_116_UI_COMPLETION.md`.

## State
- v7.115 + v7.116 Kotlin now COMPILES in the sandbox (kotlinc 1.9.24 + android-34 jar via tools/local_compile_check.sh; raw.githubusercontent.com is blocked there, so clone https://github.com/Reginer/aosp-android-jar sparse for android-34/android.jar) and JUnit passes. GitHub Actions + phone are still NOT run: owner runs build.yml first.
- SKILLS has 116 cards, MADAD 61 topics (HelpGuide.kt and CAPS must stay equal: tools/test_help_contract.py).
- New bridge: getAskPrefs / setAskPref (Settings card POOCHNA AUR SUNNA).

## Rules for the next change
- A new voice feature needs: HelpGuide topic (+ CAPS + test_part6_skills_contract MAP entry) AND a SKILLS entry with a real status chip. Run tools/audit_dead_ui.py and tools/test_v7116_ui_completion.py.
- Bridge String params must be capped (`.take(n)`), see tools/test_bridge_caps_contract.py.
- Do not change the "never ask" defaults of skipRiskyConfirm / skipOrderConfirm / skipConnectorConfirm without the owner.
