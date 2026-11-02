# PART STATUS — v7.158 AUTOPILOT, PART 1 of 4 (core engine + intake hardening)

Owner ke liye seedha summary sabse neeche hai. Honest status: **compile GREEN, 1790 JUnit tests PASS,
contract suite GREEN (11 purane version-pin guards chhod ke, jo KNOWN_STALE_GUARDS.txt me listed hain).
Kuch bhi phone par TEST Nahi HUA. Endpoints (Instagram/YouTube/GitHub) public docs se likhe gaye hain -
live par verify nahi hue.**

## IMPORTANT: pichla kaam ZIP me tha hi nahi
v7.158 prompt ke "WHAT ALREADY EXISTS" section me AutopilotRules.kt / AutopilotStore.kt / AutopilotNet.kt /
Autopilot.kt ka zikr tha, lekin owner ke v7.157 ZIP me ye files **kahin thi hi nahi** (pichla session save
nahi hua lagta hai). Rule ke hisaab se ("if he forgot something, you add it") Part 1 me pura core naye sir se
banaya gaya - ye chhupaya nahi gaya, ye status file saaf bata rahi hai.

## Kya bana (sab ADD-ONLY, kuch purana nahi hataya)

### 1. Autopilot core (4 nayi files)
- **AutopilotRules.kt** (pure Kotlin, JUnit-able, koi Android import nahi): media kind, group-title match,
  notification "arming" (Armed + Hinglish reasons), IST best-time slots (9:00/12:30/15:30/18:30/20:30/22:00,
  nextSlot), AI copy prompt/parse/fallback (packPrompt/parsePack/fallbackPack), website offer extraction
  (extractOffer/offerFromJson/offerJson/offerCode), order state machine (step: ITEM→SIZE→QTY→NAME→PHONE→
  ADDRESS→CONFIRM→DONE), discountReply ("website se order karoge to discount milega" + live offer),
  feedWith (site feed JSON, cap 200), igHost (IG… → graph.instagram.com warna graph.facebook.com),
  sentVerdict (OFF/ASK/AUTO - AUTO andha nahi hai), ignoredKind (document/GIF/voice safe-ignore),
  90-second batching, sha256Hex, Caps (6 feed/15 story/5 YT/45 min spacing - configurable), idemKey.
- **AutopilotStore.kt**: config (private prefs, add-only migration - update par owner ka data nahi bigadta),
  job queue (site/ig_feed/ig_reel/ig_story/yt; PENDING/DONE/FAILED/SKIPPED/REVIEW), idempotency keys,
  file-hash de-dupe, IST daily counters, orders store. Tokens YAHAN kabhi nahi - sirf SecureStore slots
  (github / ap_ig / ap_yt_cid / ap_yt_csec / ap_yt_refresh).
- **AutopilotNet.kt**: GitHub Contents API upload (STREAMING - 96 KB pieces, 45 MB tak, koi giant base64
  string nahi), feed.json + gallery.html + nova-feed.js writer, waitPublic (site/raw/jsDelivr), Instagram
  Graph publish (IMAGE/REELS/STORIES, container polling, IG id auto-detect, 60-din token refresh), YouTube
  device-flow OAuth + RESUMABLE upload (8 MB chunks, 1 retry per chunk) + asli privacyStatus read-back
  (Google ne PRIVATE kiya to owner ko sach bataya jata hai, fake success kabhi nahi), quota guard
  (1600/10000 units), liveOffer (nova-offer.json → homepage regex, 30-min cache).
- **Autopilot.kt**: MediaStore scan (WhatsApp Business paths; received files ke liye group-notification
  mark zaroori; Sent files OFF/ASK/AUTO), JPEG ≤1440px prep, video ≤45 MB copy, 90s batchId, queue pump
  (idempotency → caps → publish; IG website ka public URL ka wait karta hai, 2 min re-schedule),
  concierge (discount = sirf live website offer; order machine; business-lead holding reply), factsLine,
  snapshotJson (UI/voice status), writeOffer (nova-offer.json), WorkManager worker (8s one-shot +
  content-URI trigger addContentUriTrigger (real 2.9.1 API, AAR se verify kiya) + 15-min periodic).

### 2. Hooks (purani files me chhote additions)
- NovaNotificationService: markGroupMedia (O(1) mark - listener thread kabhi block nahi hota) +
  smartAutoReply ke andar concierge pehle (rules answer FIRST - discount/order/lead; AI with FACTS baad me).
- NovaService: autopilotAsk (copywriter ke liye ek AI call, background thread), onAutopilotPosted/
  onAutopilotFailed/onAutopilotOrder (JARVIS-tone lines + order par voice + phone notification),
  smartDraft me business FACTS line (brand/website/live offer - AI price/stock/delivery-date nahi bana sakta),
  MediaStore ContentObserver (dusra trigger), service start par periodic re-arm.
- NovaAccessibilityService: chatTitle() - WhatsApp chat ka asli naam (conversation_contact_name id,
  toolbar fallback) - sirf sent-by-me check ke liye, kabhi store/log/upload nahi hota.
- MainActivity bridge: autopilotStatus/Save/Job/Scan/PostIntake/SaveToken/HasToken/ClearToken/Offer
  (111 bridge String params capped - test green).
- BootReceiver: reboot ke baad periodic worker re-arm (queue WorkManager me persist hoti hai; idempotency
  se duplicate post kabhi nahi).
- Manifest: +READ_MEDIA_IMAGES, +READ_MEDIA_VIDEO (sirf Autopilot MediaStore scan ke liye; share path
  MediaIntakeStore aaj bhi permission-free hai). Count 36 → 38.
- ui.html: naya **AUTOPILOT view** (status card + group/website/repo/brand form + targets + sent-mode +
  best-time + concierge + 4 token boxes (kabhi wapas nahi dikhti, sirf SAVED) + aaj ka offer writer +
  live queue (RETRY/SKIP/HAAN) + orders list) + **home card "AUTOPILOT"** (neeche ka tab bar 320px par
  bhar raha tha, isliye card se khulta hai) + MEDIA MILA card me **POST TO AUTOPILOT** button (Share →
  NOVA → same queue; button sirf ON hone par dikhta hai).

### 3. Task B (intake hardening) - poora
1. Sent-by-me: accessibility chat-title check ke saath AUTO (title == group → auto; warna REVIEW; ASK →
   REVIEW job "aap ne bheja hai - haan karo"; OFF → skip). Blind AUTO nahi.
2. Share → NOVA → POST TO AUTOPILOT → same queue (hash de-dupe same).
3. File-hash de-dupe (first 512 KB + size ka SHA-256) - rescan/repost kabhi nahi.
4. ContentObserver in NovaService (dusra trigger).
5. Android 14 partial photo access detect (READ_MEDIA_VISUAL_USER_SELECTED) → status line me "Allow all" bolta hai.
6. Document/GIF/voice-note group media safe-ignore.
7. 90 second ke andar group items = ek batch (ek caption pack, ek hi batchId).

### 4. Build ko GREEN karne ke asli fixes (chhupana nahi, repair)
- **local_compile_check.sh sandbox me pehli baar poora chal** (pehle hamesha WifiKit/TarBz/JarvisDeepVoice
  missing-library errors se main compile hi fail hota tha): zxing/commons-compress/sherpa ke minimal
  **honest stubs** (har function throw karta hai - koi fake result nahi) tools/local_stubs/extra me,
  TarBzTest (asli commons-compress chahiye) sirf sandbox run se exclude (GitHub CI Gradle me wo
  aaj bhi chalta hai), script me absolute-path bug ka note.
- **FreeProvidersTest** ke 2 stale pins v7.157 reality se update (owner ke 4 GLM 5.3 keys brainOnly order
  me pehle aate hain) - real fix, test chhupaya nahi.
- **6 stale guard tools ka asli repair** (17 me se): audit_dead_ui.py + test_review_security_contract.py
  ab dynamic "prefix"+n wiring pehchante hain (isse part16 + part19 bhi pass); test_autosetup_contract.py +
  test_chain_contract.py ka provider-order pins v7.157 order se update; test_command_corpus_contract.py
  v7.156 ke "run" verb se update; test_ai_only_reply_contract.py ka empty-AI check ab AI path ke (last)
  sendReply se compare karta hai; test_ai_rank_autolearn_contract.py ab 5 ranked runs (autopilot copy
  5th hai); 7 contract files ka permission-count pin 38 allow karta hai (prompt ke rule ke mutabik).
- **11 pure version-pin guards** (7.104-7.154 era) KNOWN_STALE_GUARDS.txt me listed - inme kuch functional
  nahi, sirf purane versionName enum karte hain. NAYA failure wahan add NAHI kiya gaya.

### 5. Tests
- **AutopilotRulesTest.kt**: 21 tests (13 core + intake/caps/idempotency) - sab pass.
- **tools/test_v7158_autopilot_contract.py**: naya contract (static wiring, honest wording).
- Poora suite: **1790 JUnit PASS, 0 fail** | **contract: 159 pass, 11 known-stale** | bridge caps: 111/111.

## Kya abhi NAHI hua (agla part)
- Media prep (video duration/9:16/re-encode), vision captions, carousel, insights learning (Part 2).
- IG/YT step-by-step guides + TEST buttons + device-flow screen + token expiry alerts (Part 3).
- Voice commands ("nova autopilot on"), morning brief, first-run wizard, SAB THEEK HAI card (Part 3/4).
- Full holographic UI polish (Part 4 - abhi functional glass cards hain).
- Phone testing: KUCH BHI nahi hua. Build GitHub Actions par push karke hi confirm hoga (NOT CONFIRMED).

## Owner ke liye 2 line
Group ka naam, website, repo aur 4 tokens AUTOPILOT tab me daalo, ON karo - group me photo/video bhejte
reho, baaki NOVA khud karegi. Abhi ke baad 3 aur parts aayenge (media+ viral, guides+ setup, JARVIS UI).
