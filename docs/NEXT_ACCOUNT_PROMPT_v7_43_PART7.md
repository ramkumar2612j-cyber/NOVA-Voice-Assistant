# NOVA v7.43 -> PART 7 : MOTION LANGUAGE (attach: NOVA-v7_43-PART6_COMPLETE.zip)

## Rules (har part me wahi)
- Hinglish, chhota. Owner non-technical: kuch manually merge/edit nahi karwana. Poora zip + download link.
- ADD-ONLY: koi purana feature, API, fallback, connector, screen, setting, permission, id, bridge hataya / badla nahi jayega. Package com.nova.assistant, structure same.
- Demo / mock / placeholder nahi. Fake success nahi: har cheez honestly PASS / FAIL / NOT TESTED.
- Har delivery: (1) naya ZIP + link, (2) simple Hindi build/test status, (3) agle part ka NEXT_ACCOUNT_PROMPT zip ke ANDAR, (4) latest source zip sambhal ke. Purani files `docs/archive_old/` me hain, unhe mat chhedna; root me sirf ek status + ek prompt rakho.
- Beech beech me batao: kitna ho gaya, kitna bacha.

## Pehle (Part 7 se pehle)
1. Internet ho to `tools/local_compile_check.sh` + `tools/run_all_checks.sh` (Part 6 me ye NOT TESTED tha). Naya FAIL aaye to pehle theek karo. `tools/check_p6_home.py` PASS rahe.
2. OWNER CHECKPOINT 1 (phone): clip button, purane IMAGE/PHOTO/WEBSITE MEDIA buttons na dikhein; photo + "website banao" image-edit na bane; Home par board + dock; internet band par orb "NO INTERNET".

## PART 7 kaam: MOTION LANGUAGE
Screen transitions (tab badalna, chat khulna), tap ripples, haptics (Android vibrator, chhota, setting se band ho sake), number tickers (API limits, queue count). Part 5 ke `--ds-*` tokens; `html.lowfx` aur reduced-motion me sab band. Native floating HUD card aur intro canvas ka restyle bhi yahin (Part 6 me nahi hua).
Phir PART 8 (chat UI), 9 (Jarvis persona), 10 (roundabout commands, CHECKPOINT 2) `NOVA_20_PARTS_PLAN.md` ke hisab se.

## Jaane hue bugs (aage ke parts)
- Chhota follow-up ("ha kr do") par "Samajh nahi aaya": Part 10.
- Photo + sawaal par Gemini vision jawab: Part 13.
- Website verify warning (scroll-reveal animation): Part 16.
- `photoToSite()` (Part 5 fix) compile / phone test baaki.
