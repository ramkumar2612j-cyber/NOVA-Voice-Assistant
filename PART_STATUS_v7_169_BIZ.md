# v7.169 BIZ - status (simple Hindi)
## Kya bana
- Business profile (product, price, advance %, UPI, delivery time, sheher, category) - voice/chat se: "nova biz ...".
- Leads: Google Places se roz 5-10 filtered leads (asli mobile, rating, reviews, duplicate nahi) -> draft. Bhejna sirf aapke approve ke baad.
- Order confirm par advance rakam + UPI id + UPI link customer ko; delivery time yaad rakha, time aane par aapko alert.
## Test
- PASS: asli Kotlin compile + 1829 unit tests (12 naye).
- NOT TESTED: asli phone, asli Google Places key/call, WhatsApp/Instagram par message, GitHub Actions.
## Nahi hua / limit
- QR image nahi (sirf UPI id + link). Payment auto-verify nahi. UI screen nahi (abhi sirf bolke).
## Kaise chalao (phone par)
1) nova biz product ... / price ... / advance 30 / upi naam@bank / delivery 3 din / city ... / category ...
2) nova biz placeskey <Google key>  3) nova biz leads now  4) nova leads dikhao
