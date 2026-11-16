# NEXT ACCOUNT PROMPT (FULL) - JARVIS v7.177 + LAPTOP APP, phone-side LINK (Part 2b) written
Zip: JARVIS_v7_177_LINK2B.zip (root folder jarvis/). Pehle ye files padho: NEXT_ACCOUNT_PROMPT_v7_177.md, _DESKTOP_P1.md, _DESKTOP_P2_LINK.md, PART_STATUS_v7_177_LINK2B.md, TEST_STATUS.md.

## Owner aur rules (compulsory)
- Owner non-programmer hai, sirf Android phone se kaam karta hai (Chrome + GitHub web). Jawab Hinglish me, CHHOTE messages (~7 lines), EK step ek time, numbered steps, full clickable URLs.
- Add-only: kuch hataya nahi. build.yml mat chhuo. GitHub push owner ke kehne par.
- Kabhi DONE / WORKING / VERIFIED mat likho bina evidence ke. Har change ka level batao: written < statically checked < tests run < CI green < phone tested. PASS/FAIL TEST_STATUS me owner ke batane par hi (phone/laptop test).
- Natija chahiye, bahane nahi. Kuch na ho sake to 2 line me kya aur kyu, aur nearest alternative.
- Owner lambe log nahi padh sakta: build fail ho to sirf failed step ki red lines maange (screenshot theek). Kaam ke dauran progress batate raho.
- Zip phone se copy ho sake; har part ke baad zip + full prompt + chhota Hinglish status.

## Kya ho chuka (source me)
Laptop app (desktop/, Electron): UI phone wali hi (node sync-ui.js), commands.js parser, actions.js (safe: delete/shutdown/password refuse), link.js (AES-256-GCM, relay ntfy.sh, topics jarvis-<sha256(dir:key)[:40]>, AAD=topic, 120 s window, replay dedupe), main.js ("phone jodo" -> code JARVIS-LINK:<key>; "phone par <kaam>" -> cmd; na-samjha sawal -> ask; phone ka reply dikhta hai). 24 node tests PASS.
Phone side (is session me likha): 
- app/src/main/java/com/nova/assistant/LaptopLink.kt: LaptopLink (topic/seal/open/publish/listen, java.util.Base64) + LaptopLinkRules (parseCode, laptopCommand, Dedupe).
- MainActivity.kt: bridge saveLaptopLink (400 char cap) / hasLaptopLink / clearLaptopLink; key SecureStore slot "laptoplink"; save/clear par JarvisService.instance?.restartLaptopLink().
- JarvisService.kt: restartLaptopLink() (onCreate bg thread + bridge), onLaptopEnv (cmd -> h.post emit("me") + typed(text); ask -> runFreeChain(withFacts=false) -> publish "reply" re=id, ek ask ek time; reply -> emit ai), laptopRoute() hook in process() ("laptop par/pe/me/mein <kaam>" -> p2l cmd; key na ho to seedha batata hai; "bhej diya" = sirf relay ne liya), linkEpoch++ onDestroy.
- ui.html: Settings box #llbox (ll_key, ll_save, ll_del, ll_st), BRIDGE list, CAPS topic "laptoplink" (HelpGuide.kt me barabar), Skills entry "LAPTOP LINK (phone se laptop control)".
- Tests: tools/test_laptop_link_contract.py (static + node<->Java mirror interop), tools/laptop_link_interop/Interop.java, app/src/test/.../LaptopLinkRulesTest.kt (6 tests, NOT RUN), tools/test_part6_skills_contract.py MAP line.

## Evidence (honest)
- Chala (machine): 24 node tests, test_laptop_link_contract (interop), test_help_contract, test_part6_skills_contract, test_bridge_caps_contract, test_desktop_contract, audit_dead_ui; pehla full run 172 PASS.
- test_perf_governor_contract.py aur test_native_smooth_contract.py (RUNTIME timing) sandbox me FAIL, ORIGINAL upload par bhi FAIL: meri change se nahi. GitHub CI par dekho.
- NOT DONE: Kotlin compile, LaptopLinkRulesTest run, GitHub build, Electron exe build, asli ntfy.sh relay par phone+laptop end-to-end, phone test. Part 3 laptop voice input. versionName badla nahi (7.177).
- Known limits: purana listener naye save ke baad agli relay line (~45 s) par band hota hai; dedupe beech me bachata hai. ntfy.sh public relay hai (sirf random topic + ciphertext dikhta hai, availability unki).

## Part 4 DONE (machine): desktop/brain.js notes+reminder+yaad rakho, 33 node tests PASS. package.json files me link.js missing tha, jod diya. Details: PART_STATUS_v7_177_BRAIN4.md

## Step A DONE (machine): boot.html + hud-inject.js (JARVIS HUD), 36 node tests PASS. Owner ne 2 reference videos di (Jarvis Spark site + J.A.R.V.I.S v6.5 HUD, "one command workspace loaded", undo). Agla: Step B (workspace + undo), Step C (laptop voice, offline Vosk). Details: PART_STATUS_v7_177_HUD_A.md

## Step B DONE (machine): workspace + undo + HUD WORKSPACES, 42 node tests PASS. Details: PART_STATUS_v7_177_WS_B.md. Agla: Step C laptop voice.

## Step C DONE (machine): desktop/voice.js + HUD mic (Groq key, push-to-talk), 48 node tests PASS. Hamesha-sunne wala wake word NAHI. Details: PART_STATUS_v7_177_VOICE_C.md

## Agla kaam (is order me, ek step)
1) Owner se: "GitHub par Build and Test APK chala diya? Aur Actions > Desktop > Run workflow?" Red line aaye to sirf wahi padho.
2) Build red ho to fix: pehle shak LaptopLink.kt (Regex/Base64/JSONObject), LaptopLinkRulesTest.kt, JarvisService wiring (when-branch types, h.post return@post).
3) Green ho to owner test (ek ek): laptop exe chalao -> "phone jodo" -> code phone Settings > LAPTOP LINK me paste -> phone se "laptop par chrome kholo" -> laptop se "phone par torch jalao" -> laptop par koi free sawal (phone AI jawab). Natija owner ke batane par TEST_STATUS me.
4) Part 3: laptop voice input (wake "jarvis"; Electron me webkitSpeechRecognition nahi chalta -> offline Vosk/whisper ya cloud STT chuno). Part 4: notes/schedule/memory laptop par.
5) Purane khule kaam: NEXT_ACCOUNT_PROMPT_v7_177.md (kinetic UI phone test, JARVIS awaaz Ultron default OFF test, Instagram filler) aur v7_175 (match live, Instagram DM, Video Studio, Payment QR phone test).
