# NOVA v7.119 continuation prompt (audit done, code unchanged)

Start from this zip (files at root). Read `NEXT_ACCOUNT_PROMPT_v7_110_FINAL.md` (owner, hard rules), `NEXT_ACCOUNT_PROMPT_v7_119_AFTER_PARTB.md`, `PART_STATUS_v7_119_PART_B.md`, `AUDIT_v7_119_PART_B.md`.

## Owner (short)
Hinglish, Android phone only, SHORT messages (~7 lines), ONE step at a time, full clickable URLs, no DONE / WORKING / VERIFIED without evidence (written < statically checked < tests run < CI green < phone tested). Repo https://github.com/devboutique0-wq/NOVA (branch main). Zip + next prompt after EVERY part.

## State
- Audit only. Python checks 149 PASS (8 browser tests timed out in sandbox). Kotlin compile, real JUnit, GitHub Actions, phone: NOT RUN in this audit.
## Next
1. Owner uploads zip contents to repo root, runs build.yml; red lines first.
2. Then OWNER_PHONE_TEST_LIST_v7_119.md.
## Rules
Add-only. New voice feature needs HelpGuide + CAPS + SKILLS. Keep "never ask" defaults.
