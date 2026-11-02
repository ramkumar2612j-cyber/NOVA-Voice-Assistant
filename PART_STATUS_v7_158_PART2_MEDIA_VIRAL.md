# PART STATUS — v7.158 AUTOPILOT, PART 2 of 4 (media prep + viral engine + website)

Owner ke liye seedha summary sabse neeche. Honest status: **compile GREEN, 1801 JUnit tests PASS,
contract suite GREEN (wahi 11 purane version-pin guards). Phone par kuch bhi TEST NAHI HUA;
media3 / Shopify / Woo / Wix endpoints public docs + asli AAR signatures se likhe gaye hain.**

## Kya bana (sab ADD-ONLY)

### Task C — MEDIA PREP (poora)
- **AutopilotMedia.kt** (nayi file):
  - Video ki asli jaankari (MediaMetadataRetriever): duration, asli display size (rotation samet), codecs.
  - **Shorts gate**: sirf ≤ 3 minute ka video YouTube Shorts banta hai (lamba ho to honest message - chup skip nahi).
  - **Reel codec check**: H.264/AAC nahi hai to **Media3 Transformer 1.4.1** se re-encode - **sirf charging + Wi-Fi par**
    (owner ka rule: phone hang na ho). Warna original jata hai + message bolta hai.
  - **9:16 blurred background**: horizontal video reel/Short ke liye 1080x1920 canvas + GaussianBlurWithFrameOverlaid
    (blur wala frame peeche, sharp video upar) - same charging+Wi-Fi condition.
  - **45 MB cap**: badi file sirf YouTube par jaati hai (site/IG skip + honest message).
  - **Cover frame**: video ke 1/3 waqt ka asli frame JPEG - gallery thumb + vision ke liye.
  - Media3 1.4.1 chosen kyunki 1.5+ compileSdk 35 maangta hai (project 34 par pinned hai). SAARE API signatures
    asli AAR download karke verify kiye (Transformer.start(EditedMediaItem,String), Listener.onCompleted/onError,
    Presentation.createForWidthAndHeight, GaussianBlurWithFrameOverlaid(Float,Float,Float)).
- Sandbox stubs (media3 transformer/common/effect) - har function throw karta hai, koi fake export nahi.

### Task D — VIRAL ENGINE (poora, imaandaar)
1. **Vision caption**: photo / video-frame ko FreeVision dikhta hai; prompt me "PHOTO ME ASLI CHEEZ"
   (jo dikhta hai wahi likha jayega - jhooth nahi). NovaService.autopilotVision + Autopilot.visionFor.
2. **Per-platform copy**: pack me pehle se tha (IG caption + 12-18 hashtags, story + poll, YT title/desc/tags,
   site blurb, Hinglish) - ab vision + never-repeat ke saath.
3. **Carousel**: ek batch ki 2-10 photos = Instagram ka EK carousel (is_carousel_item children + CAROUSEL parent);
   >10 photo = 10 ke groups. publishCarousels() pump me.
4. **Insights loop**: post ke 24 ghante baad asli numbers (IG reach/saves/shares, YT views) padhe jaate hain;
   slot averages seekhte hain; **best slots khud tune** hote hain (5 sample + 20% behtar ho to slot aage jump);
   3x average reach = viral signal -> owner ko bol ke bataya jata hai. Kabhi bhi ki number nahi banaya jata.
5. **Never repeat**: pichle post ka hook ya 80%+ wahi hashtag set ho to pack vary hota hai (naya opener +
   ghoomaya tag set + naya CTA) - AI call ke bina.
6. **Caps configurable + tested**: Part 1 me tha (6/15/5/45min), ab tests me pinned.
7. **Idempotency**: Part 1 me tha.

### Task E — WEBSITE (poora)
- **SITE TYPE setting**: github (gallery) | vercel | shopify | wooCommerce | wix - AUTOPILOT tab me dropdown.
- **Vercel/Pages deploy wait**: waitDeploy (24 koshish x 10s) - deploy hone tak intezaar, phir hi aage.
- **Shopify publisher**: Admin API product + image (token sirf X-Shopify-Access-Token header me).
- **WooCommerce publisher**: REST products (consumer key/secret sirf Basic auth header me).
- **Wix publisher**: Media Manager import (API key sirf Authorization header me).
- **nova-offer.json writer**: Part 1 me tha - website offer aur chat ka jawab hamesha match.
- Naye SecureStore slots: ap_shop, ap_woo_k, ap_woo_s, ap_wix (ALL_SLOTS ek jagah pinned; bridge allowlist
  isi se aata hai - token kabhi UI ko wapas nahi jaata, sirf SAVED chip).

### UI
AUTOPILOT tab: SITE TYPE dropdown + Shopify store / Woo URL fields + 4 publisher token boxes + token line
me live SAVED chips. (Part 4 me holographic polish aayega.)

### Tests
- AutopilotRulesTest ab **32 tests** (Part 2 ke 11 naye: rotation/shorts/reencode/wifi-charge/host-cap/carousel/
  repeat/vary/slot-index/tuned-order/viral).
- tools/test_v7158_autopilot_contract.py Part 2 ke 20+ naye checks ke saath.
- **1801 JUnit PASS, 0 fail** | contract **159 pass + 11 known-stale** | UI screens PASS | dead-UI 0 | bridge caps 111/111.

## Kya abhi NAHI hua (agla part)
- IG/YT Hinglish step-by-step guides, TEST buttons, YouTube device-flow screen, token-expiry alerts (Part 3).
- Voice commands, morning brief, first-run wizard, SAB THEEK HAI self-repair card (Part 3).
- Full futuristic UI + HelpGuide/CAPS + owner phone-test list + final docs (Part 4).
- Re-encode / carousel / insights / publishers: sab LIVE par test nahi hue (NOT TESTED - phone).

## Owner ke liye 2 line
Ab video bhi theek se jaayega - galat format ho to khud theek karta hai (charging + WiFi par), lambi video
sirf YouTube par. Ek saath bheji photos ek carousel ban jaati hai, aur jo time pe zyada log dekhte hain NOVA
khud wahi time chunta hai.
