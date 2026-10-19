# NOVA v7.43 : PART 6 HOME REDESIGN (simple Hindi, honest)

## Kya bana (sirf jodna, kuch hataya nahi)
1. ORB STATE: purani 5 states (STANDBY / ONLINE / LISTENING / THINKING / SPEAKING) waise hi. Nayi 6th: internet na ho aur NOVA ON ho to orb amber me "NO INTERNET" dikhata hai.
2. STATUS BOARD (orb ke neeche, 5 chips, 2.5 second me refresh): AI (Gemini key / offline brain / key nahi), INTERNET, PERMISSION (mic, notification, overlay, accessibility = n/4), KAAM (queue me kitne), AWAAZ (bolta hai / chup / silent). Hara = theek, amber = dhyan do, laal = dikkat. Chip dabane par settings ya chat khulta hai.
3. DOCK (4 bade glass buttons): CHAT, CHUP, SILENT, STOP. Har button sirf purana button dabata hai ya purana "stop" sentence bhejta hai, to purana code wahi chalta hai.
4. Purani row (ACTIVATE / CHUP / SILENT) page me hai, bas chhipi hui; ids wahi.
5. Naya Kotlin code: NAHI. Sirf ui.html (naye blocks `p6_home`, `p6_home_css` + orb me ek branch).
6. Power: page hidden ya cover hone par refresh ruk jata hai; lowfx / reduced-motion me blink band; koi network / storage nahi.

## Jaanch
- `tools/check_p6_home.py` (naya static check): PASS.
- ui.html ke saare 41 scripts ka JS syntax: 0 error.
- Purane 112 Python contract scripts: Part 6 se pehle aur baad ka diff, koi naya FAIL nahi (22 purane FAIL waise hi; list PART5 status archive me).
- COMPILE / JUnit / PHONE TEST: NOT TESTED (sandbox me internet aur browser nahi). Screenshot nahi liya.

## Owner ko phone par dekhna hai (CHECKPOINT 1 ab bhi baaki)
Home par orb ke neeche 5 chips + 4 dock buttons dikhein; chip aur dock touch par kaam karein; internet band karke dekho orb amber "NO INTERNET" ho; STOP dabane par NOVA ON rahe (Part 4 ka SAFE STOP).

## Abhi baaki
Floating HUD card (Kotlin overlay) aur intro canvas ka naya look, isko Part 7 me motion ke saath. Baaki jaane hue bugs `NEXT_ACCOUNT_PROMPT_v7_43_PART7.md` me.
