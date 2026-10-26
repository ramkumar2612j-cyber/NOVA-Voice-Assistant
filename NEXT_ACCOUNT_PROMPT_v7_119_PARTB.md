# NOVA v7.119 continuation prompt (Part A done -> Part B)

Start from the v7.119 Part A zip. `NEXT_ACCOUNT_PROMPT_v7_110_FINAL.md` (owner, hard rules) and `NEXT_ACCOUNT_PROMPT_v7_115.md` / `_v7_116.md` (open phone-data items) still apply. What changed: `PART_STATUS_v7_119_PART_A.md`.

## Owner (short)
Hinglish, Android phone only (no PC), SHORT messages (~7 lines), ONE step at a time, numbered steps, full clickable URLs, never write DONE / WORKING / VERIFIED without evidence (written < statically checked < tests run < CI green < phone tested), wants results not excuses, sends only the red lines of a failed build step. Repo https://github.com/devboutique0-wq/NOVA (branch main only). Zip + next-account prompt inside after EVERY part.

## State
- Lite series (lag / heat): v7.117 wake gate, v7.118 chat content-visibility, v7.119 Part A = gate moved to `WakeGateRules.kt` + tests. versionName stays 7.114.
- v7.117 / v7.118 / v7.119A Kotlin was NEVER compiled (sandbox had no kotlinc / network). FIRST job in any sandbox with network: `tools/local_compile_check.sh`, then GitHub Actions build.yml. If red, fix from the red lines FIRST.

## Part B (to do)
- Mic thread allocation: `pre.add(buf.copyOf(n))` allocates every chunk. Replace by a pure `PreRollRing` (fixed 5 slots, reused arrays) + JUnit; keep `WakeGateRules.replayCount` semantics (replay all but newest).
- Add `OWNER_PHONE_TEST_LIST_v7_119.md` (wake still works, no clipped first word, phone temperature, long-chat scroll).
- Then: CHANGELOG, status file, next prompt, zip.

## Rules
Add-only. A new voice feature needs HelpGuide topic + CAPS + SKILLS entry. Do not change the "never ask" defaults. Do not mark anything PASS without evidence.
