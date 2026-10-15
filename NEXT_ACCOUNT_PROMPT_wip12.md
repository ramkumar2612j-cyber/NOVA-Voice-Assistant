# NOVA wip12 → Next Account Handover

## Current state
- **Version**: wip12 (based on v6.97-wip11)
- **All 58 contract checks**: PASS
- **ui.html size**: ~291 KB (9 legacy style layers removed = ~35 KB freed)

## What wip12 changed (UI only — no Kotlin touched)
1. **New futuristic HUD theme** (`nova-final` style block at end of body)
   - Cyan (#2fd8ff) / indigo (#6f7bff) palette, deep space bg with grid overlay
   - No backdrop-filter, no full-screen animated layers, only transform/opacity animate
   - Home screen fits in one viewport (no scroll), ACTIVATE always visible
2. **Skills tab rebuilt** (`skills.js` injected inline)
   - 74 real skills across 10 categories (PHONE, COMM, APPS, LIVE, MAKE, MIND, VOICE, AI, LINK, SAFE)
   - Search bar + filter chips + skill count (74 SKILLS · 51 READY · 19 SETUP)
   - Each skill: icon, title, ON/SETUP/ALWAYS READY chip, description, RUN/SAY buttons, setup action buttons
   - All examples verified against Kotlin tests and IntentParser
3. **Bug fixes**
   - `al_on` duplicate id fixed → `auto_learn_on` with real `getAutoLearn`/`setAutoLearn` bridge
   - `#aiw` / `#helpw` overlays: `display:none` by default, show only on `aria-hidden="false"`
   - Orb 3D loop throttled to 30fps cap (`_lg` guard in `gloop`)
   - Status box (#fstat) collapsed by default, tap header to expand
4. **"SAB SKILLS" card** added to home quick-cards rail → goes to Skills tab

## What still needs building (Kotlin, NOT done here)
All from wip11 — nothing regressed:
- `Part 5` voice/face features (wip11 already had these written, not CI-green)
- `Part 2A` WhatsApp deep commands, AI fallback chain v2, HindiPack
- Single combined GitHub Actions build for all parts

## Next task for next account
1. `cd nova_part4_work && bash tools/run_all_checks.sh` — must still be 58/58
2. Screenshot home, skills, settings tabs with `python3 tools/ui_shots.py /tmp/s 390 844`
3. Continue from wip11 Kotlin work: Part 5 voice + face features first
4. Then Part 2A, then combined build

## Key files
- `app/src/main/assets/ui.html` — UI (do not remove `nova-final` style block)
- `skillpacks/starter.json` — routine examples
- `tools/run_all_checks.sh` — contract gate
- `NEXT_ACCOUNT_PROMPT_v6.97.md` — original wip11 handover (still valid for Kotlin parts)
