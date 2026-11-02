# NOVA v7.44 : PART 7 MOTION (simple Hindi, honest)

## Kya bana (sirf jodna, kuch hataya nahi) - sirf ui.html (naye blocks `p7_motion_css`, `p7_motion`)
1. SCREEN SLIDE: tab badalne par nayi screen us side se aati hai jis side ka tab dabaya (HOME se LIMITS = daayen se, wapas = baayen se). Chat khulne par neeche se halka uthta hai.
2. HAPTIC: tab, dock, chip aur chhote buttons par 8-12 ms ka halka jhatka (purana `Android.vibrate`). Settings me SAVE ke bagal me naya button HAPTIC ON / OFF (default ON, `nova_hap`).
3. NUMBER TICKER: API LIMITS ka total / use hua / bacha aur Home ke KAAM chip ka number 0 se (ya purane number se) naye number tak ginta hai (0.4 sec).
4. TAP RIPPLE: pehle se v7.12 me hai (`fut_rip`), waise hi rakha.
5. Power: `html.lowfx` ya reduced-motion me slide, ticker aur haptic sab band. Naya network / storage / Kotlin: NAHI (sirf haptic setting ka ek localStorage flag).
6. Purane saaf-bekaar files zip se hataye (owner ke kehne par): `docs/ui_shots` (purane screenshots), `docs/archive`, `docs/archive_old`. App, tools, gradle, code kuch nahi chhua.

## Jaanch
- `tools/check_p7_motion.py` (naya static check): PASS. `tools/check_p6_home.py`: PASS.
- Naye p7 JS ka syntax (node --check): 0 error.
- `tools/run_all_checks.sh`: 98 PASS, 14 FAIL. Ye 14 wahi purane hain (Part 6 se pehle bhi FAIL the, 12 known stale + 2 render / screen-session); koi NAYA FAIL nahi.
- COMPILE / JUnit / PHONE TEST: NOT TESTED (sandbox me internet aur browser nahi). Screenshot nahi liya.

## Part 7 ka aakhri hissa (ab ho gaya)
- HUD chat card (`NovaHud.kt`): ek line badli, card ka fill ab pill jaisa glass gradient hai. Pill me glowing border, sweep aur spring pehle se neon-glass tha, usko nahi chhua. Kotlin braces / parens check PASS; COMPILE NOT TESTED.
- Intro screen (`ui.html`, sirf CSS): caption aur TAP hint glass chip, halka neon vignette (sirf opacity saans leti hai, lowfx / reduced-motion me ruki rehti hai). Intro ka canvas / animation code nahi chhua.

## Abhi baaki
- COMPILE / JUnit / PHONE TEST: NOT TESTED. OWNER CHECKPOINT 1 phone par dekhna baaki: slide, HAPTIC button, ticker, intro, floating chat card.
