# NOVA v7.47 -> PART 9 : JARVIS PERSONA (attach: NOVA-v7_47-PART8_COMPLETE.zip)

## Rules (har part me wahi)
- Hinglish, chhota. Owner non-technical: kuch manually merge / edit nahi karwana. Poora zip + download link.
- ADD-ONLY (owner ke explicit hataane ke order ko chhod kar, jaise v7.46 fixed message hatana): koi purana feature, API, fallback, connector, screen, setting, permission, id, bridge hataya / badla nahi jayega. Package com.nova.assistant.
- OWNER RULE v7.46: auto-reply me koi fixed / canned text kabhi nahi. Sirf AI ka jawab; AI na chale to kuch nahi bhejna, owner ko batana.
- Demo / mock / placeholder nahi. Fake success nahi: har cheez honestly PASS / FAIL / NOT TESTED.
- Har delivery: (1) naya ZIP + link, (2) simple Hindi status, (3) agle part ka prompt zip ke ANDAR, (4) latest source zip sambhal ke. Root me sirf ek status + ek prompt, purane docs/ me.
- Beech beech me batao: kitna ho gaya, kitna bacha.

## Pehle
1. Internet ho to `tools/local_compile_check.sh` + GitHub Actions build. Naya FAIL aaye to pehle theek karo. `tools/check_p6_home.py`, `check_p7_motion.py`, `check_p8_chat.py` PASS rahein. `tools/run_all_checks.sh` (KNOWN_STALE_GUARDS ab khaali).
2. v7.45-7.46 ka Kotlin (NovaNotificationService.autoReply AI-only, NovaService smartReplyVoice / smartDraft) abhi compile nahi hua: pehle wahi.

## PART 9 : JARVIS PERSONA
Persona engine: halka humour, feelings, owner ko naam se bulana (ActionLog.ownerName / Settings naam), chhoti witty lines. Persona sirf bolne ke andaaz me, kaam / safety rules kabhi nahi badalta. Switch Part 17 me (default ON, band karne ka option). Phir Part 10 (roundabout commands, CHECKPOINT 2).
