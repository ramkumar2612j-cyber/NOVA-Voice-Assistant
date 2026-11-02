# PART STATUS — v7.158 AUTOPILOT, PART 3 of 4 (IG/YT UX + concierge + JARVIS voice + auto-setup)

Owner ke liye seedha summary sabse neeche. Honest status: **compile GREEN, 1810 JUnit tests PASS, contract
suite GREEN (wahi 11 purane version-pins). Phone par kuch bhi TEST NAHI HUA.**

## Task F — INSTAGRAM / YOUTUBE UX (poora)
- **TEST buttons (asli call)**: GITHUB TEST (repo ki asli read), INSTAGRAM TEST (token + id verify, publish
  NAHI hota), YOUTUBE TEST (login + quota bacha) - AUTOPILOT tab me, honest wording ke saath.
- **YouTube device-flow screen**: CODE MANGO -> Google ka code + google.com/device link -> CODE COPY KARO ->
  CHECK KARO (poll) -> refresh token SecureStore me. Login ka sabse aasan tarika, koi typing nahi.
- **Token expiry alerts**: Instagram 60-din token - save hone ka time store hota hai; 5 din pehle khud
  refresh try, fail ho to owner ko voice + notification ("AUTOPILOT tab me nayi token daalo").
- **Quota guard**: upload = 1600 / 10000 units, ytQuota hisaab me; test button bhi batata hai.
- **PRIVATE lock warning**: YouTube upload ke baad asli privacyStatus padha jata hai - Google ne PRIVATE
  kiya (unaudited API project) to owner ko saaf bataya jata hai + audit steps. Fake success kabhi nahi.
- **GUIDE cards**: GitHub token / Instagram token / YouTube device login / Shop+Offer - step-by-step Hinglish,
  ek-ek khulta hai (phone smooth rehta hai).

## Task G — CONCIERGE (poora)
1. IG DM + WhatsApp + WA Business - Part 1 core + ab complete:
2. **Business leads** (wholesale/bulk/collab/influencer) -> **LeadStore.addDirect** (naya direct path - DM
   leads; screen-scraping parser LeadRules public-only hi rehta hai, uska security contract waisa hi hai)
   + owner ko voice + notification ek baar (rozana max 1 per chat).
3. **Confirmed order** -> orders list + voice + phone notification (Part 1) + ab **owner ka webhook bhi fire**
   (hook_ connector = Google Sheet via Zapier/n8n/Make; sirf tab jab owner ne banaya ho) - honest wording:
   "trigger bheja, qabool hua".
4. **Order status** ("mera order kab aayega") -> SIRF owner-store ki hui baat se jawab. Owner bolta hai
   "nova order Rahul dispatched 15 tak" ya AUTOPILOT tab me orders list me status likhta hai. Kuch store
   na ho to honest line + owner ko alert. ** kabhi delivery date nahi banayi jaati.
5. Groups kabhi answer nahi + owner ka khud ka reply AI ko side me rakhta hai (purane rules untouched).

## Task H — JARVIS EXPERIENCE (poora)
- **Voice commands** (AutopilotVoiceRules, pure + 9 tests): "nova autopilot on/off/status",
  "nova aaj kya post hua", "nova orders dikhao", "nova offer 20% off code SK20",
  "nova isko abhi post karo" (turant scan+pump), "nova story lagao" (sabse naya photo story me),
  "nova autopilot setup karo" (wizard khulta hai), "nova order <naam> <status>".
  Grammar narrow hai - negated orders kabhi match nahi, normal chat kabhi capture nahi hota.
- **JARVIS-tone lines**: "Sir, reel Instagram par live ho gaya", "Sir, naya order confirm...",
  "Sir, subah ki report: kal 3 post hue, reach 4200 tak, 2 order confirm hue."
- **Proactive alerts**: post failed / token expiring / new order / new lead / viral signal (3x reach).
- **Morning brief**: rozana subah (IST 8-11, ek baar) - kal ke post + reach + orders.
- **Self-heal**: worker 15-min re-arm (service start + boot + FIX button), retry/backoff pump me,
  reboot ke baad queue resume + idempotency se no-duplicates (Part 1 se).

## Task M — AUTO SETUP (poora)
1. **First-run wizard** (full screen, Hinglish, ek step per screen, progress ring, "baad me" required
   steps ko skip nahi karta): Photos -> Notification access -> Accessibility -> Battery (+brand autostart
   hint) -> GROUP (recent WA-Biz group notifications me se TAP karo ya likho) -> Tokens (+REPO BANAO
   one-tap) -> SAB THEEK HAI check -> READY. App start par khud khulta hai (ek baar).
2. **Auto-detect**: group names (asli notifications se), brand + site type (website ke og:site_name/title
   + asli response headers: x-vercel-id / x-shopid / wix), IG id auto-detect, existing offer (liveOffer).
3. **No repo?** REPO + WEBSITE BANAO (ek tap) - existing GitHubPublish flow se naya repo + gallery site,
   config me khud set.
4. **Token help**: guided screens + clipboard paste + TEST after each; YouTube device-flow (upar).
5. **SAB THEEK HAI card**: 12 asli checks (permission/partial-photos/listener/accessibility/tokens/repo/
   group/site reachable/offer/worker/battery) + har laal item ka one-tap FIX (permission re-ask,
   listener rebind screen, accessibility screen, battery screen, worker re-arm). Tab kholte par chalta hai.
6. **Safe defaults**: wizard READY par sab safe ON (site + IG feed/reel/story + YT Shorts + best-time +
   concierge), sent-media = ASK; DM concierge ke liye auto-reply + message mode ka ek-baar ka Hinglish
   poochhna (NOVA khud HAAN mangega).
7. **Zero-config updates**: cfg add-only migration (Part 1 se) - owner ka data update par safe.
8. Sab kuch voice se: "nova autopilot setup karo".

## Design note (important)
Lead capture ke liye LeadRules ka public-source allowlist UNCHANGED rakha hai (uska apna security contract
hai: screen-scraping sirf public pages se). DM leads ke liye naya `LeadStore.addDirect` banaya - bounded
(100 max), deduped, same storage. Do design contracts aapas mein takraaye to dono ka izzat rakha gaya.

## Tests
- **AutopilotVoiceRulesTest**: 9 naye tests (grammar + negation + honest replies + order-status rules).
- tools/test_v7158_autopilot_contract.py: Part 3 ke 25+ naye checks.
- **1810 JUnit PASS, 0 fail** | contract **159 pass + 11 known-stale** | UI screens PASS | dead-UI 0 |
  wiring audit OK (285 UI calls / 310 bridge funs / 32 command kinds).

## Kya abhi NAHI hua
- Full holographic UI polish + HelpGuide/MADAD/FeatureStatus/CAPS self-knowledge (Part 4).
- OWNER_PHONE_TEST_LIST + final docs + v7.159 final ZIP (Part 4).
- Wizard / device-flow / webhook / voice commands: phone par test NAHI hue.

## Owner ke liye 2 line
Ab bas wizard kholo (pehli baar khud khulega ya bolo "nova autopilot setup karo") - ALLOW dabate jao,
group chuno, tokens paste karo, TEST dabao. Uske baad bol ke sab hota hai: "nova aaj kya post hua",
"nova orders dikhao", "nova offer 20% off code SK20".
