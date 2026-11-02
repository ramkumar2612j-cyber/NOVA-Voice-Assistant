# NOVA v7.119 continuation prompt (Part B done)

Start from the v7.119 Part B zip. Read `NEXT_ACCOUNT_PROMPT_v7_110_FINAL.md` (owner, hard rules), `NEXT_ACCOUNT_PROMPT_v7_115.md` / `_v7_116.md` (open items), `PART_STATUS_v7_119_PART_B.md` (what changed).

## Owner (short)
Hinglish, Android phone only, SHORT messages (~7 lines), ONE step at a time, numbered steps, full clickable URLs, never write DONE / WORKING / VERIFIED without evidence (written < statically checked < tests run < CI green < phone tested). Repo https://github.com/devboutique0-wq/NOVA (branch main). Zip + next prompt after EVERY part. With every delivery: a preview and a direct check link.

## State
- Lite series done in code: v7.117 wake gate, v7.118 chat content-visibility, v7.119 A (WakeGateRules), v7.119 B (PreRollRing).
- Compile of main code: 0 errors (sandbox, kotlinc 1.9.24). Real JUnit + GitHub Actions + phone: NOT RUN.
- 33 JUnit failures in unrelated files under the stub harness (Connector / Web / Trade / Vercel / Music): find out if they also fail on the original zip before touching anything.

## Next
1. Owner pushes zip, runs build.yml; if red, fix from the red lines FIRST.
2. Owner runs OWNER_PHONE_TEST_LIST_v7_119.md and reports.
3. Only then more lag/heat work, and only from phone evidence.

## Rules
Add-only. A new voice feature needs HelpGuide topic + CAPS + SKILLS entry. Do not change the "never ask" defaults. Do not mark anything PASS without evidence.
