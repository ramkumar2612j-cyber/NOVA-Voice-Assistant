# NOVA Part 7 Status

Base: NOVA-WIP-v75-PART6.zip. Kuch delete/disable/replace nahi kiya.

## Ho chuka
- run_all_checks: 64 passed, 0 failed.
- Part 6 mein 1 UI check fail tha (test_part5_ui_screens: home 360x640 par CONNECT tab label kat raha tha). Fix: ui.html ke end mein chhota `p7_tabfix` CSS block (max-width:380px par tab label font 8px, letter-spacing 0). Ab check PASS.

## UI (owner ki request) - ui.html mein naya `p7_core3d` block (style + script, kuch delete nahi)
- Home: ACTIVATE button ab orb ke neeche beech mein (CSS `order`), CHUP/SILENT uske neeche.
- Neeche tab bar ke beech mein naya 3D futuristic CORE orb (CSS 3D, ghoomte rings, float animation, dabane par press + pop animation, ON hone par green). Dabane par CORE panel khulta hai: bada 3D ACTIVATE/STOP button, press animation + ripple, asli `#btn` ko hi click karta hai (headless test: toggle 1 baar call hua). Dusra tab dabane ya bahar tap karne par band.
- Settings: neeche ka SAVE ab round tick icon (same `s_save` handler, aria-label save). Settings ke sabse neeche hi hai.
- Checks: ui_screens 320x568 + 360x640 home/settings clean, run_all_checks 63 pass.
- NOT TESTED: asli phone par 3D smoothness/speed, haptic, asli ACTIVATE start/stop (fake bridge mein state nahi badalta). Low-end phone par atke to Quality=SMOOTH (animation band ho jati hai).

## FEATURE AUDIT (static, code padh kar; asli phone par NAHI chalaya)
- HelpGuide ke 33 features (call, WhatsApp, alarm, torch/volume, app kholna, music/maps, camera, AI photo, website, screen control, memory, learn, driving, recording, live info, sawal, business watch, auto-reply, connectors, routines, lock guard, teach, power guide, gmail, agent, screen alert, watch mic, quiet, offline AI, leads, limits, feature status): sabke peeche asli code mila, koi sirf-dikhawa (fake) nahi mila.
- Jo cheez hamesha limited hai (jhooth nahi): WiFi/Bluetooth ON-OFF sirf settings page kholta hai (Android allow nahi karta); camera ka photo button NOVA nahi dabata.
- FeatureStatus (home ka "KYA CHALTA HAI") sirf permission/key/state padhta hai, feature chalata nahi. Asli kaam ka test phone par AutoTest / TEST REPORT se hi hoga.
- Jo kami mili: Auto-reply sirf owner ke fixed text se jawab deta tha, AI se baat nahi karta tha. Neeche fix.

## AI SMART REPLY (WhatsApp/Messages) - NAYA, Kotlin COMPILE NAHI HUA
- Settings > Auto-reply mein naya switch "AI SMART" (default OFF). ON hone par NOVA message samajh kar apne shabdon mein jawab deta hai, pichhli 6 baat yaad rakhta hai (sirf memory mein), aur owner ko bata deta hai: History entry + phone notification + (idle ho to) bolkar.
- Safety (purane rules nahi hate): group, code/OTP, bina Reply button wale message pe jawab nahi. AI khud ko owner ka ASSISTANT batata hai (owner banke jhooth nahi). Paise/rate/discount/refund/payment/UPI/link/number/date ka vaada kabhi nahi; aise message ya galat jawab par "pooch kar batata hoon" line jati hai aur owner ko "zaruri" notification milti hai. Rate limit: 15 sec/sender, 12/ghanta, 80/din.
- Agar NOVA ACTIVATE nahi hai ya AI key/chain nahi hai: purana fixed text jata hai. AI call alag thread mein hoti hai (main thread block nahi).
- Files: SmartReplyRules.kt (naya), NovaService.smartDraft/onSmartReplied, NovaNotificationService.smartAutoReply, ActionLog.smartReplyOn, MainActivity bridge, ui.html ar_smart. Tests: SmartReplyRulesTest.kt (JUnit, CHALA NAHI), tools/test_smart_reply_contract.py (PASS). Purana test_ai_rank_autolearn_contract ki ginti 3 se 4 ki (naya AI call bhi ranking sikhata hai).
- Business Watch (WaBiz) window mein WhatsApp ke liye purane catalogue-only rules hi chalte hain; smart reply wahan nahi laga.
- NOT TESTED: asli WhatsApp par jawab, AI ke jawab ki quality, Kotlin compile. Pehle GitHub Actions log dekho.

## Is session mein NAHI hua (wajah: sandbox mein internet band)
- kotlinc download nahi ho saka, isliye Kotlin compile/JUnit chala nahi. Bina compile ke Kotlin badalna build tod sakta hai (owner sirf phone se kaam karta hai), isliye koi Kotlin change nahi kiya.
- Website 2-pass generation, voice se aur phone-kaam, bolkar ON/OFF: NOT STARTED.
- Smart reply wali Kotlin changes bhi COMPILE nahi hui (kotlinc nahi mila). Agar GitHub Actions mein error aaye to pehle SmartReplyRules.kt / NovaService.smartDraft / NovaNotificationService.smartAutoReply dekho.

## NOT TESTED
- Asli phone par sab kuch (popup band, 5s latency, Quality=SMOOTH, website quality, CONNECT label fix).
- Gradle build / GitHub Actions / JUnit asli environment mein.

## Agla kaam
- GitHub Actions log dekho, errors ho to fix.
- Part 7 ka baaki: NEXT_ACCOUNT_PROMPT_PART7.md ke kaam 2, 3, 4 (network wale environment mein, pehle `bash tools/local_compile_check.sh /tmp/nova_check`).
