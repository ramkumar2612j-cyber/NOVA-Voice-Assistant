# v7.170 PAYMENT QR - status (simple Hindi)
## Kya bana
- Aap kisi bhi UPI id se QR image bana sakte ho: bolkar ("nova qr banao sharma@okhdfc 500") ya Home ke PAYMENT QR card se screen kholkar (UPI id, naam, rakam, note bharo -> QR BANAO).
- QR screen par dikhta hai; GALLERY ME SAVE (Android 10+) aur LINK COPY button.
- Payment check: UPI app (GPay/PhonePe/Paytm/BHIM) ya SMS ka "paisa aaya" alert aate hi NOVA bolta hai aur screen par dikhata hai: "Payment aa gayi Rs 500 (Rahul se)". Rakam aapke QR se match na ho to bhi batata hai. 30 minute tak na aaye to bolta hai "nahi aayi".
- Poochh sakte ho: "nova payment aaya kya". Auto check on/off: "nova payment check on/off karo" ya screen ka switch.
## Test
- PASS: screen headless Chromium me chali (QR banana, galat UPI par error, WAITING -> aa gayi, back button), 0 JS error; alert parser ke 12 namune Python me sahi nikle.
- NOT TESTED: Kotlin compile, JUnit (PayRulesTest likha hai, chala nahi), GitHub Actions, asli phone, asli GPay/PhonePe/SMS alert, asli awaaz.
## Limit (sach)
- Bank se seedha check nahi hota. Sirf phone par aaye alert se pata chalta hai (Notification access ON chahiye).
- SMS nakli ho sakta hai: bada paisa ho to bank app dekho. Jis bank/UPI app ka alert text alag ho wo shayad na pakde.
