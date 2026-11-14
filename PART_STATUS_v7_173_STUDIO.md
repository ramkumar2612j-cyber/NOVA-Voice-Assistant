# v7.173 VIDEO STUDIO - status (simple Hindi)
## Kya bana
- Home ka VIDEO STUDIO card ya bolo "nova video edit karo": screen khulti hai. VIDEO CHUNO (1 se 8), likho kya chahiye (ya khali), EDIT KARO.
- NOVA har video me action napta hai (frame ka badlaav), sabse achha hissa rakhta hai, sabse zordaar pal ko slow-motion (0.5x, us hisse me awaaz band), kaat par zoom (punch), pehle/shant hisse par dheema push-in, halka contrast, sab jodta hai, Movies/NOVA me save, bolkar + screen par batata hai. DEKHO / SHARE buttons.
- Likh sakte ho: reels ya youtube, "30 second", "slow motion nahi", "zoom nahi", "best clip pehle".
## Test
- PASS: screen headless Chromium me (chuno, shuru, progress, taiyaar, back), 0 JS error; edit-plan ka logic Python me (zordaar pal par slow-mo, steady/calm clip par nahi).
- NOT TESTED: Kotlin compile, JUnit, Media3 export, muxer join, asli phone, awaaz.
## Limit
- Music, titles, transitions, "5 second par ye karo" abhi nahi. Slow-mo normal fps se hoti hai (thodi jhatke wali), awaaz us hisse me band.
- Phone par dheemi: charging par rakho, app khula.
