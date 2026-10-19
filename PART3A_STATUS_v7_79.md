# v7.79 - PART 3 ka hissa A (UI glitches) - status
RUN = sandbox me chalaya. NOT RUN = nahi chalaya. NOT TESTED = phone chahiye.
## Kya badla (add-only)
- Home par NOVA core orb chhota aur ring se baayen khiska hua tha (purana CSS rule uspar bhari pad raha tha). Ab har screen size par ring ke theek beech me. Sirf ui.html me ek CSS rule jodi, kuch hataya nahi.
- Sweep: HOME / SKILLS / LIMITS / CHAT / SETUP, 320 / 360 / 412 px: koi side-overflow nahi, koi JS error nahi, chat input tab bar ke upar.
## RESULT
- RUN: test_part3a_contract (static + headless centre check) PASS, 8 purane guards PASS.
- NOT RUN: Kotlin compile / JUnit (Kotlin nahi badla), GitHub Actions. NOT TESTED: phone.
## Agla: Part 3B = chhoti screen (320 px) par Home ka orb box dabna + baaki chhote glitches jo sweep me dikhen; phir Part 4.
