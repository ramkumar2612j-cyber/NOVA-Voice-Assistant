# NOVA v7.48 -> PART 10 : ROUNDABOUT COMMANDS (attach: NOVA-v7_48-PART9_COMPLETE.zip)

## Rules (har part me wahi)
- Hinglish, chhota. Owner non-technical: kuch manually merge / edit nahi karwana. Poora zip + download link.
- ADD-ONLY (owner ke explicit hataane ke order ko chhod kar): koi purana feature, API, fallback, connector, screen, setting, permission, id, bridge hataya / badla nahi jayega. Package com.nova.assistant.
- OWNER RULE v7.46: auto-reply me koi fixed / canned text kabhi nahi. Sirf AI ka jawab; AI na chale to kuch nahi bhejna, owner ko batana. Persona (Part 9) auto-reply me kabhi nahi.
- Demo / mock / placeholder nahi. Fake success nahi: har cheez honestly PASS / FAIL / NOT TESTED.
- Har delivery: (1) naya ZIP + link, (2) simple Hindi status, (3) agle part ka prompt zip ke ANDAR, (4) latest source zip sambhal ke. Root me sirf ek status + ek prompt, purane docs/ me.
- Beech beech me batao: kitna ho gaya, kitna bacha.

## Pehle
1. Internet ho to `tools/local_compile_check.sh` + GitHub Actions build. Naya FAIL aaye to pehle theek karo. `tools/check_p6_home.py`, `check_p7_motion.py`, `check_p8_chat.py`, `test_persona_contract.py` PASS rahein. `tools/run_all_checks.sh` (KNOWN_STALE_GUARDS khaali).
2. Ab tak compile NAHI hua (sab Kotlin): v7.45-7.46 (NovaNotificationService.autoReply AI-only, NovaService smartReplyVoice / smartDraft) aur v7.48 (PersonaRules.kt, PersonaRulesTest.kt, NovaService.personaTouch, ExtCfg.persona, MainActivity getPersona / setPersona). Pehle wahi compile + JUnit.

## PART 10 : ROUNDABOUT COMMANDS (CHECKPOINT 2 - owner app kholke test karega)
Owner ghuma ke bole ("wo jo kal wale ko bhej do", "ha kr do", "usi ko dobara") to NOVA samjhe aur kaam kare. Known bug: chhota follow-up "ha kr do" kaam nahi karta. Safety: unclear ya risky ho to pehle poochho; contact / payment / delete me kabhi andaaze se kaam nahi. Persona sirf andaaz: Part 9 ka PersonaRules kaam / safety kabhi nahi badalta, ise na chhedo.
Phir Part 11 (media intake).
