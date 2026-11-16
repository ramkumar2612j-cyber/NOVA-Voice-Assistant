# PART STATUS v7.186 - JARVIS BRAIN (owner: "main bolu, jarvis samjh ke turant kaam kare, koi bhi kaam")

## Kya bana (add-only)
- `JarvisBrain.kt` (naya, pure Kotlin): jo sentence kisi local rule ne nahi uthaya (4+ shabd), uspe ek chhota AI call pehle
  MATLAB samajhta hai aur raasta chunta hai: ANSWER (jawab), TOOLS (ek simple phone kaam), AGENT (kai-step ka asli kaam).
- `NovaService.handle()` mein ek hook (naye code ka ek block, "Answer sources in order" se theek pehle): AGENT chuna to
  "Samjha: ..." line dikhti hai aur purana task-agent (plan -> act -> verify -> retry) shuru hota hai.
- ANSWER / TOOLS / koi bhi fail (AI nahi mila, JSON galat) = purana raasta bilkul waisa hi chalta hai.
- `JarvisBrainTest.kt` (naya): 5 chhote pure-text tests.

## Honest
- Kotlin compile / JUnit / GitHub Actions / phone test: NOT RUN (sandbox me Kotlin compiler nahi).
- Jo sentence pehle se kisi local rule ne pakad liya (torch, volume, call, reel, music...) wo ab bhi pehle wahi rule leta hai;
  ye brain sirf bachi hui sentences ke liye hai. Agar phone par koi sentence phir bhi robot jaisa jawab de, to uska exact
  text bhejo - wo kaun sa rule le raha hai, wahi ThIk karenge.
- Agent ko screen control (Accessibility ON) chahiye; lock screen / OTP / bank jaise Android ke band darwaaze abhi bhi band hain.
