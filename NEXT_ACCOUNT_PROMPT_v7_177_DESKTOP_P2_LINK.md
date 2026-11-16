# NEXT ACCOUNT PROMPT - JARVIS laptop app Part 2 (LINK) done on laptop side
Rules: NEXT_ACCOUNT_PROMPT_v7_177.md + NEXT_ACCOUNT_PROMPT_v7_177_DESKTOP_P1.md. Owner: "kahin se bhi" (WiFi ya internet), keys sirf mobile app par.
## Design (done in desktop/link.js + main.js)
- Laptop "phone jodo" -> secret key banta hai, code `JARVIS-LINK:<key>` chat me dikhta hai; owner phone app me paste karega. Relay: ntfy.sh (free, account nahi), sirf random topic + ciphertext dikhta hai. AES-256-GCM, AAD = topic, 120 s time window, replay dedupe.
- Laptop se: "phone par <kaam>" -> type cmd phone ko; koi bhi sawal jo laptop parser na samjhe -> type ask, PHONE apni key se jawab bhejta hai (type reply). Phone se aaya cmd laptop par parse+exec, reply wapas.
- API keys laptop par kabhi nahi (test me check: main.js me key/GEMINI/GROQ nahi).
## NOT DONE
- Phone side: docs/pending_LINK/LaptopLink.kt.txt (uncompiled draft, wire format desktop/link.js se match). Isko LaptopLink.kt banao, Settings > LAPTOP LINK paste field, JarvisService me listen thread, "laptop par <cmd>" phone se, MADAD/HelpGuide/aiFacts, GitHub build. Kotlin compile NOT DONE.
- Asli relay + phone + laptop end-to-end test NOT DONE. Electron exe build NOT DONE. Part 3 voice input laptop par.
## Pehla sawal owner ko: "Laptop wala desktop.yml build chala diya? Phir phone side (Part 2b) shuru karun?"
