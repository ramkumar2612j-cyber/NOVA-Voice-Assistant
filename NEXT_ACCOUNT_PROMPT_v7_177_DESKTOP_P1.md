# NEXT ACCOUNT PROMPT - JARVIS v7.177 + LAPTOP APP (desktop/) Part 1 done
Rules: NEXT_ACCOUNT_PROMPT_v7_177.md (add-only, fake PASS nahi, build.yml mat chhuo, Hinglish, ek step, owner sirf phone se kaam karta hai).
Owner ne "asli laptop assistant" chuna: voice, apps, files control, phone jaisi hi UI.
## desktop/ (Electron, Windows pehle)
- ui.html phone wali hi hai: `node sync-ui.js` app/src/main/assets ko desktop/ui me copy karta hai. preload.js `window.Android` Proxy deta hai (sendText real, baaki phone-only calls khaali value). Jawab `onJarvis('me'|'ai', text)` se aata hai, ai ka jawab speechSynthesis se bolta hai.
- commands.js: Hinglish command parser (app/site/folder/search/file dhundo/screenshot/lock/time/sysinfo; delete/shutdown/password refuse). actions.js: fixed app table, spawn arg-array (shell string nahi), files sirf Desktop/Documents/Downloads/Pictures me, kuch delete nahi hota.
- Workflow: .github/workflows/desktop.yml (manual run) -> artifact JARVIS-desktop-windows (.exe portable).
## Test status
- 15 node tests + tools/test_desktop_contract.py PASS. Electron kabhi chala nahi, exe kabhi bana nahi: NOT DONE.
## Agla kaam (chhote parts)
1. Owner se: desktop.yml chalaye (Actions > Desktop > Run workflow); fail aaye to error padh ke fix; exe laptop par chala ke bataye.
2. Part 2: AI jawab (Gemini/Groq key ke saath phone wali settings jaisi) taaki free sawal ka jawab de.
3. Part 3: voice input (wake word "jarvis") - Electron me webkitSpeechRecognition nahi chalta; offline Vosk/whisper ya cloud STT chunna.
4. Part 4: baaki phone features me se jo laptop par bane (notes, schedule, memory); phone-only (calls, WhatsApp notifications, accessibility) laptop par nahi.
