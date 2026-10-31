# OWNER PHONE TEST LIST — v7.154 JARVIS UPGRADE

Pehle: naya APK install karo (GitHub Actions se). Purane features ke purane test lists (v7.150/151/152/153)
bhi chal sakte hain — ye list SIRF naye features ki hai. Har item par PASS / FAIL likho.

## A. JARVIS DEEP VOICE (offline neural awaaz)

1. "nova deep voice download karo" bolo → NOVA bole "download shuru…" → internet par 20 MB aayega
   (Settings > UPDATES me progress nahi dikhega, ye seedha filesDir me jaata hai) → poora hone par NOVA
   khud bole "model install ho gaya". Kyaa bola? (Time kitna laga: ___)
2. "nova deep voice on karo" → bole "ON. Ab English aur Hinglish jawab is bhaari offline awaaz me…"
3. Koi English sawaal pucho ("what can you do") → jawab NAYI bhaari male awaaz me aana chahiye (system TTS se alag).
   Awaaz kaisi lagi (bhaari/clear/robotic): ___
4. Koi Hinglish jawab (Roman letters) bolo → wahi deep awaaz. Ab Hindi (Devanagari) jawab wale sawaal pucho
   → PURANI Hindi awaaz aani chahiye (deep voice Hindi me nahi bolta — ye rule hai).
5. Phone AIRPLANE MODE / internet OFF karke ek English sawaal pucho → deep voice abhi bhi bolna chahiye
   (ye offline hai). Bola? ___
6. Deep voice bolte waqt STOP button dabao → turant chup?
7. "nova deep voice off karo" → wapas purani awaaz. "nova deep voice status batao" → sahi haal bole?

## B. BARGE-IN (bol ke rokna)

8. "nova bol ke rokna on karo" → ON confirm ho. Lamba jawab dilwao (koi lamba sawaal) aur bolte waqt
   dhairya se "STOP" bolo → NOVA turant chup honi chahiye (mic/service ON hi rehte hain).
9. Wapas lamba jawab shuru karwa ke "chup" bolo → rukni chahiye. "ruko" bolo → rukni chahiye.
10. SELF-ECHO test: NOVA se aisa jawab dilwao jisme "stop" word aaye (jaise "maine video stop kar di" —
    Message Mode se) — NOVA khud apni baat se band NAHI honi chahiye.
11. Gaana chala ke NOVA se kuch pucho bina barge-in ke… phir barge-in ON karke bolte waqt TV/gaana chalta
    chhod do — galat-stop nahi hona chahiye (conf+loudness gate). "nova bol ke rokna off karo" se band.

## C. AUDIO FOCUS (gaana duck)

12. YouTube par gaana chalao → NOVA se kuch pucho → gaana DHEEMA hona chahiye NOVA ke bolte waqt,
    baad me wapas full volume. Koi app band nahi hona chahiye.
13. "nova audio focus off karo" → ab gaana full volume pe hi rahega NOVA ke bolte waqt.

## D. PROVIDER CHECK + GLM SAB JAGAH

14. "nova provider check karo" → har set key ka haal bole (Gemini/Groq/OpenRouter/Z.ai: OK ya reason).
    Jo key nahi hai wo skip; koi key hi nahi to wahi bole. Internet OFF karke dobara → "internet nahi" type jawab.
15. "nova glm sab jagah on karo" → confirm bole. Ek normal chhota sawaal pucho → jawab aana chahiye
    (thoda slow ho sakta hai — GLM 5.3 pehle try hota hai). "nova glm sab jagah off karo" → wapas default.

## E. Settings screen

16. Settings kholo → 4 nayi checkbox dikhein (JARVIS DEEP VOICE, BOL KE ROKNA, AUDIO FOCUS ticked,
    GLM 5.3 SAB JAGAH) + deep voice model ki status line. Tick/untick karke SAVE → dobara kholo → yaad rahe?
    Skills screen me 5 nayi cards (AWAAZ me 3, AI DIMAAG me 2)? MADAD me 5 naye topics?

## F. REGRESSION (purana kaam — 2 minute)

17. Wake word "nova" se jaagna, torch on/off, "youtube kholo", WhatsApp confirm flow, "nova wifi scan karo"
    (v7.153), STOP button bolte waqt, SILENT mode — sab pehle jaise?
18. Phone restart → NOVA notification → ACTIVATE → sab normal?

## Notes

- Deep voice OFF + barge-in OFF + afocus ON = v7.153 jaisa behaviour hi rehna chahiye (zero-regression rule).
- FAIL aaye to: kya boli, kya hona chahiye tha, aur Settings > error log COPY — teeno bhejo.
