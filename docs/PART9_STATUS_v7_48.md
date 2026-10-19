# NOVA v7.48 : PART 9 JARVIS PERSONA - status (simple Hindi, honest)

## Kya juda (add-only)
1. NAYI file `PersonaRules.kt` (pure Kotlin, na internet, na AI): kabhi owner ko naam se bulata hai ("Ji Sonu."), kabhi ek chhoti witty line ("Aur hukum?"), kabhi time ke hisaab se feeling line (raat ko "Itni raat ko jaag rahe ho?"). Naam wahi jo Settings me auto-reply wale "owner naam" me likha hai; naam khaali ho to sirf witty line.
2. Persona SIRF bolne ka andaaz badalta hai. Ye kabhi nahi chhuta: haan/na ka sawaal, confirmation, error / gadbad wala jawab, call, message, WhatsApp, recording, screen agent, publish, site, Gmail, connector, image, payment. Sirf chhote (180 akshar tak) aam jawab / harmless acknowledgement par lagta hai, aur do touch ke beech kam se kam 45 second.
3. Auto-reply (doosron ko jawab) me persona BILKUL nahi: owner rule v7.46 (fixed / canned text nahi) jaisa tha waisa hi hai. `NovaNotificationService` ko chhua tak nahi.
4. Switch: Settings me SAVE ke bagal me naya button "PERSONA ON / OFF" (default ON). Asli switch screen Part 17 me banegi.
5. Jodne ki jagah: `NovaService.process()` me sirf ek line (`personaTouch`), `ExtCfg.persona`, bridge `getPersona / setPersona`, `ui.html` me ek chhota block `pt9_persona`.

## Kuch hataya nahi
Koi purana feature, id, bridge, setting, permission nahi badla. `finishTurn`, `say`, `withNudge`, auto-reply wahi hain.
Is zip me ek aur cheez: v7.47 ki 5 launcher icon PNG + `orb_poster.png` wapas rakhi (wo galti se hat gayi thi, build tootta).

## Test status (honest)
- RUN: `tools/test_persona_contract.py` PASS (43 checks), `tools/run_all_checks.sh` = 103 contract scripts PASS, 0 FAIL. `ui.html` ki saari JS scripts ka syntax check (node) OK.
- NOT RUN: Kotlin compile (yahan compiler / internet nahi), JUnit `PersonaRulesTest.kt` (10 test, expected values haath se nikaale), asli phone, GitHub Actions. v7.45-7.46 ka Kotlin bhi ab tak compile nahi hua.
- OWNER CHECKPOINT (Part 10 ke baad hi): Settings me PERSONA ON dikhe; kuch chhote sawaal / "torch jala do" bolo: kabhi kabhi naam ya chhoti line aaye (har baar nahi). PERSONA OFF karo: bilkul pehle jaisa. Koi call / message / confirmation ke saath mazaak nahi aana chahiye.

## Baaki
Part 10 (roundabout commands, CHECKPOINT 2) se 20. Plan: NOVA_20_PARTS_PLAN.md. Prompt: NEXT_ACCOUNT_PROMPT_v7_48_PART10.md.
Purane known bugs: chhota follow-up "ha kr do" (Part 10), photo + sawaal vision (Part 13), website verify warning (Part 16), photoToSite compile / phone test, wake word locked phone (owner decision).
