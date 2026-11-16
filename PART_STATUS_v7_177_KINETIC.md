# v7.177 KINETIC REEL STYLE
Owner ne reel (video) bheji: dark + bold red pop words + white script + wireframe -> live website. Sirf `app/src/main/assets/ui.html` ke end me add-only block (`v177-kinetic-css` / `v177-kinetic-js`). Kotlin / build.yml nahi chhue.
1. OPENING: bola jaane wala quote reel words me (kb = bada red, ks = white script), `window.__nvOpen()` ki QN se sync. Opening skip nahi hoti. Typed line dim (opacity .55).
2. WEBSITE BUILD SCREEN `#wbk` (z 8400): start = `.p3_site` card ya naya `.msg.me` jise `Android.detectIntent` SITE kahe (voice bhi). Phase p0 wireframe, p1 fill+scroll (5 s), p2 text (15 s), p3 finale jab `onSite(ok,html)` aaye. Fail/dismiss/back = band. lowfx / reduced-motion me nahi dikhta.
3. APP-WIDE: `.vw.on` children rise, h2 pop, `#panel .box` stagger, tab icon bounce. One-shot, lowfx me off.
Class prefix (wbb, wbt, kw, kb, ks...) isliye ki app ke purane `.b .t .ph .bt .hd .ft .s` se na takraye.
Test: headless Chromium PASS: opening words, wbk phases/fail/dismiss/back, tab switch, 0 console error. Contract scripts ~100+ chale, 0 FAIL, poora run complete nahi hua (300 s limit): `bash tools/run_all_checks.sh` background me chalao.
NOT DONE: Kotlin compile, GitHub build, asli phone test (script font Android `cursive`, yahan nahi dekha), voice-started build overlay phone par.
