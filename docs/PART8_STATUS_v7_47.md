# NOVA v7.47 : PART 8 CHAT UI - status (simple Hindi, honest)

## Kya juda (add-only, sirf ui.html: pt8_chat_css + pt8_chat)
1. Naye bubbles: neon-glass. NOVA ke jawab cyan (bayen), aapke message violet (dayen). Har bubble par chhota label "NOVA 12:31" / "12:31 TUM".
2. Typing reveal: pehle se (v7.32 p3_fx) maujood hai, Part 8 ne dusra nahi jodha (do ek saath takrate). Part 8 sirf bubble style + label deta hai.
3. Typing dots: "NOVA soch raha hai" wale teen dot neon bounce.
4. Voice waveform: chat khuli ho aur NOVA SUN RAHA / BOL RAHA / SOCH RAHA ho to input ke upar bars wali patti. Ye app ki asli state (html[data-fut]) se chalti hai. NOTE: bars ki oonchai awaaz ke volume se nahi, sirf animation hai (asli mic level Kotlin se nahi aata).

## Kuch hataya nahi
Koi purana id, bridge, setting, Kotlin file nahi badla. Check: tools/check_p8_chat.py (PASS).

## Test status
- RUN (headless Chromium, nakli Android bridge): bubbles, label, reveal (beech me aadha text, aakhir me poora), markdown / bulk instant, waveform listen / speak / off, dots, JS error 0. Contract scripts PASS.
- NOT TESTED: asli phone, GitHub Actions compile / JUnit (Part 8 me Kotlin badla nahi, par v7.45-7.46 ka Kotlin badlav abhi compile nahi hua).
- OWNER CHECKPOINT: chat kholo, kuch likho: bubble + label + reveal dikhe; mic dabao: waveform patti dikhe.

## Baaki
Part 9 (Jarvis persona) se 20. Plan: NOVA_20_PARTS_PLAN.md. Prompt: NEXT_ACCOUNT_PROMPT_v7_47_PART9.md.
Purane known bugs: chhota follow-up "ha kr do" (Part 10), photo + sawaal vision (Part 13), website verify warning (Part 16), photoToSite compile / phone test, wake word locked phone (owner decision).
