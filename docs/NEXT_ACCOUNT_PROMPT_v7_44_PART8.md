# NOVA v7.44 -> PART 8 : CHAT UI (attach: NOVA-v7_44-PART7_COMPLETE.zip)

## Rules (har part me wahi)
- Hinglish, chhota. Owner non-technical: kuch manually merge/edit nahi karwana. Poora zip + download link.
- ADD-ONLY: koi purana feature, API, fallback, connector, screen, setting, permission, id, bridge hataya / badla nahi jayega. Package com.nova.assistant, structure same.
- Demo / mock / placeholder nahi. Fake success nahi: har cheez honestly PASS / FAIL / NOT TESTED.
- Har delivery: (1) naya ZIP + link, (2) simple Hindi build/test status, (3) agle part ka prompt zip ke ANDAR, (4) latest source zip sambhal ke. Root me sirf ek status + ek prompt. Purane status / prompt `docs/` me hain.
- Beech beech me batao: kitna ho gaya, kitna bacha.

## Pehle
1. Internet ho to `tools/local_compile_check.sh` + `tools/run_all_checks.sh`. Naya FAIL aaye to pehle theek karo. `tools/check_p6_home.py` aur `tools/check_p7_motion.py` PASS rahein.
2. OWNER CHECKPOINT 1 (phone): home board + dock, clip button, internet band par orb "NO INTERNET", tab slide, HAPTIC ON/OFF (Settings), limits ke numbers ginte hue, intro caption, floating chat card.

## PART 8 : CHAT UI
Futuristic bubbles, voice waveform, typing reveal `NOVA_20_PARTS_PLAN.md` ke hisab se. Phir 9 (Jarvis persona), 10 (roundabout commands, CHECKPOINT 2).

## Jaane hue bugs (aage ke parts)
- Chhota follow-up ("ha kr do") par "Samajh nahi aaya": Part 10.
- Photo + sawaal par Gemini vision jawab: Part 13.
- Website verify warning (scroll-reveal animation): Part 16.
- `photoToSite()` (Part 5 fix) compile / phone test baaki.
