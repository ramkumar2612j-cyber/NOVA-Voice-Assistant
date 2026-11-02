# NOVA NEXT ACCOUNT PROMPT (after v7.158 AUTOPILOT, all 4 parts DONE) — v7.159 se shuru karna

Owner: Hinglish, not technical, Android phone only (Chrome + GitHub web, no PC). Repo:
https://github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant (branch main only). Package
com.nova.assistant. versionName 7.158; agla kaam 7.159 (versionCode = 2000 + run number).

## v7.158 me kya hai (sab 4 parts COMPLETE — kuch bhi dobara mat banana)

1. **Autopilot core + intake** (Part 1): AutopilotRules/Store/Net/Autopilot.kt; group match,
   arming, IST best-time slots, AI copy pack, website offer (nova-offer.json), order machine,
   caps (6 feed/15 story/5 YT/45 min), 90s batching, file-hash de-dupe, sent-by-me verdict
   (ASK/AUTO/OFF), WorkManager (8s + content-URI + 15-min periodic), reboot resume.
2. **Media prep + viral + website** (Part 2): AutopilotMedia.kt (MediaMetadataRetriever truth,
   <=3 min Shorts gate, H.264/AAC re-encode via Media3 Transformer 1.4.1 SIRF charging+WiFi par,
   9:16 blurred background, 45 MB host cap, cover frame); vision caption (FreeVision, "jo dikhta
   hai wahi"); carousel (2-10 photo = 1 IG carousel); insights loop (24h reach se slot self-tune,
   3x = viral signal); never-repeat pack vary; SITE TYPE github|vercel|shopify|woo|wix publishers.
3. **IG/YT UX + concierge + JARVIS voice + auto-setup** (Part 3): TEST buttons (asli call), YouTube
   device-flow screen, token expiry alerts (IG 60-din, 5 din pehle refresh), quota guard, PRIVATE
   lock warning, GUIDE cards, leads (LeadStore.addDirect + owner alert), order webhook, order-status
   sirf owner-store se, AutopilotVoiceRules (9 commands, JARVIS tone), morning brief, first-run
   wizard, SAB THEEK HAI card (12 checks + one-tap FIX), safe defaults.
4. **Holographic HUD + self-knowledge + final docs** (Part 4): HUD strip (radar ring + live
   QUEUE/IG/STORY/YT/KEYS/QUOTA chips), holo grid, corner brackets, SHARP-only animations (SMOOTH
   = static; lowfx gate; reduced-motion; no backdrop-filter); HelpGuide/MADAD "autopilot" topic
   (74), Skills entry (COMM), FeatureStatus read-only row, CHANGELOG/README/OWNER_PHONE_TEST_LIST.

**Sandbox evidence (v7.158):** compile GREEN (real kotlinc 1.9.24 + android-34 + REAL org.json +
REAL JUnit 4.13.2 + Hamcrest 1.3, GitHub source se build) → **OK (1810 tests, 0 fail)**; contract
**159 pass + 11 known-stale** (purane version-pins, naya failure zero); dead-UI 0; node --check
74/75 (script #21 = pre-existing WebGL shader); headless Chromium render PASS (0 JS errors).
**NOT RUN: GitHub Actions. NOT TESTED: phone.**

## RULES (waise hi)

Add-only, nothing removed. Honest evidence: "NOT TESTED" phone ke liye, "NOT CONFIRMED" build ke
liye. Report progress every 5%. Har part ke baad source ZIP + FULL next prompt. Token kabhi UI/log
me nahi. Groups me kabhi answer nahi. Delivery date kabhi nahi banti.

## OPEN ITEMS (v7.159 ke liye, priority order)

1. **Owner phone test ka result** (OWNER_PHONE_TEST_LIST_v7_158.md, 39 checks). Jo FAIL aaye: kya
   hua, kya hona chahiye tha, error log COPY — teeno maango, phir fix.
2. **Pehli green GitHub Actions build**: media3 1.4.1 / sherpa-onnx / commons-compress deps ka
   asli Gradle resolve + smoke job (AUTO_TEST_STATUS.md) padho. Compile error aaye to poora fix.
3. **IG/YT live endpoints**: posting pehli baar phone se chalegi tabhi pata chalega (public docs
   se likhe hain). 401/403/429/error shapes ka honest report maango.
4. **Insights loop** ko 2-3 din ke asli data ke baad hi tune karna (abhi sirf wiring hai).
5. **Holographic HUD** phone par SHARP quality me smooth chalna chahiye (swiftshader me theek
   tha); atake to Part 4 ke animation delays ghatao (0.5s/50ms).
6. Agla feature sochna ho to owner ke bole hisaab se — suggest mat karo.
