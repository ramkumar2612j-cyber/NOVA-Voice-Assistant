# NOVA v7.115 continuation prompt

Start from the v7.115 zip. Read `NEXT_ACCOUNT_PROMPT_v7_110_FINAL.md` for owner, hard rules and project (they all still apply) and `PART_STATUS_v7_115_WAKE_HEAR_SITE.md` for what changed.

## State
- Parts 1-8 (v7.104-110), v7.113 (SMOOTH + horn guard), v7.114 (reels, family numbers, movie name), v7.115 (WakeGuardRules, HearFailRules, SiteOffline) are written.
- v7.115 Kotlin was NEVER compiled (no kotlinc in that sandbox). FIRST job: owner runs build.yml; if it fails, fix from the log before anything else. New files: WakeGuardRules.kt, HearFailRules.kt, SiteOffline.kt (+3 test files). Edited: NovaService.kt (wake loop branch, junk branch, 2 fields), SiteGen.kt (offlineOut + stub fallback).
- versionName stays 7.114 on purpose (old contract tests pin it).

## Open (need owner phone data, change nothing without it)
- TV false wake: tune WakeGuardRules (MAX_WORDS 7, STEP_CONF 0.05, MAX_CONF 0.92, WEAK_START_CONF 0.85).
- "samajh nahi aaya": HearFailRules.SHORT_AUDIO_S 1.2, HELP_AFTER 2.
- UI lag, battery, horn thresholds (v7.113), Instagram button names (v7.114).
- Website quality with a real AI key is still unmeasured.
- Optional: delete NovaAssist.kt only after a green build (never referenced).
