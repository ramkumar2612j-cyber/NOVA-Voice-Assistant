# PART STATUS v7.216 - HOME HUD L3 (REFERENCE-IMAGE HOME, LAUNCHER + APP DONO)

Kya chaha gaya tha (owner): "Jo image share ki h vaise hi launcher or app ke ander ka home page rakho, activate krte hi launcher bhi lag jaye, modern jarvis fully real working."

## Kya badla (add-only; kuch bhi remove nahi)

### 1. launcher.html — poora naya JARVIS HOME (reference image phone-1 style)
- AMOLED black + cyan #00F0FF + violet #9D00FF (launcher me koi page-filter nahi, seedha rang).
- Header: JARVIS italic shine logo + "JUST A RATHER VERY INTELLIGENT SYSTEM" + clock/date right.
- **Notifications panel** (glass): System Metrics (battery+CPU bar, OK/LOW tag), Network Status (WI-FI/MOBILE/OFFLINE bar, LIVE/DOWN), AI Module Status (JARVIS LISTENING/STANDBY, ACTIVE/OFF tag).
- Arc reactor hero (triangle core SVG + 3 rings + scan line + hex wallpaper canvas + stars).
- WELCOME, BOSS + greeting (setState.greet se).
- CURRENT WEATHER card (tap = city dialog, pehle jaisa hi) + AI MODULE ACTIVE/STANDBY badge.
- Metric tiles: BATTERY / DATA / RAM / STORAGE + AI MODULE·CPU row (purane m1..m4 + naya m5 DATA, m6 CPU).
- RECENT ACTIVITY: REMINDERS (agla scheduled task), MESSAGES (aakhri kaam), NEWS (cached headline; tap = "aaj ki news batao" JARVIS ko).
- **Bada JARVIS, ACTIVATE ring** (side me torch + camera). Tap = LB.act('orb','') — service nahi chal rahi to shuru, chal rahi ho to JARVIS card popup. Tap par turant optimistic live glow; setState.run se confirm.
- Search, dock, Aaj ka business, Quick Actions, System Status gauges, Laptop Control, apps page, nav + orb — sab pehle jaise hi (same ids + LB contract).

### 2. ui.html — naya `<style id="v7216-jh-css">` + `<script id="v7216-jh-js">` (sabse aakhri layer, `</body>` se pehle)
- **#jh = JARVIS HOME HUD** (reference image phone-1/2 style): same notifications panel, arc reactor, WELCOME, CURRENT WEATHER, AI badge, tiles (BATTERY/DATA/RAM/STORAGE + AI·CPU), RECENT ACTIVITY, **COMMAND & ANALYSIS** card (neural-network canvas, PLAY song, CPU/RAM/NETWORK, LOCATION TRACKING + MAP KHOLO, SECURE COMMAND), bada ACTIVATE ring, apna bottom nav (HOME/CHAT/MIC/NEWS/SETUP).
- Real numbers naye `Android.homeJson()` bridge se, 5 sec poll + show par pull.
- ACTIVATE ring = wahi `Android.toggle()` jo purana #btn karta hai → started par purana setOn(true) + tfAssemble; jh bhi live ho jaata hai (ACTIVE badges, violet-green glow, SYSTEM LIVE).
- PLAY song = `Android.sendText("youtube pe <song> chalao")`; MAP KHOLO = `sendText("map kholo")`; SECURE COMMAND = seedha `sendText(text)`; NEWS = `sendText("aaj ki news batao")`. 'sent' par jh hide + JARVIS CHAT khul jaata hai; busy/off par saaf message.
- Intro ke baad (body.ready) #jh apne aap HOME ban jaata hai; CHAT/SETUP jh ke nav se purane screens khulte hain; `t_home` par wapas HUD; Android back par HUD band (jarvisBack chain add-only); chip "HUD" se dobara khulta hai. `html.jhom #tabs{display:none}` — purana tab bar sirf jh ke dauran chhupta hai.
- Page-filter ke under chalta hai, isliye cyan ke liye source #fa5a3c aur violet ke liye #557300 (v7.215 wala rule). lowfx / reduced-motion par animations band.

### 3. Kotlin (naya file + 2 chhote add-only edit)
- **HomeHud.kt (naya)**: REAL phone values — battery (sticky intent), RAM (ActivityManager), storage (StatFs), **CPU %** (/proc/stat delta), network (ConnectivityManager transports, koi nayi permission nahi), location text (permission granted ho tabhi), agla reminder (ScheduledTaskStore), aakhri kaam (TaskManagerStore), mausam (launcher wali "launcher" prefs + WebSources geo/forecast, 20 min cache, background thread), news headline (WebSources Google News RSS, 30 min cache, background thread). `json()` ek JSONObject bhejta hai; kuch bhi invent nahi hota.
- **MainActivity**: Bridge me `fun homeJson(): String = HomeHud.json(...)` (add-only, `running()` ke baad).
- **LauncherActivity**: stateJson me cpu/net/rem/msg/news add (launcher.html ke naye tiles isi se bharte hain).

## ACTIVATE FLOW ("activate krte hi launcher bhi lag jaye")
- Ek hi JarvisService.running dono jagah dikhta hai: launcher tick 5s + wake 2.5s par pushState (run:true → SYSTEM LIVE, reactor violet glow, badge ACTIVE, orb violet "BOLO / TAP KARO"); app ka #jh homeJson poll par wahi. Launcher par ACTIVATE ring / app ke jh par ring — dono se service shuru hoti hai (permissions missing ho to purana startFlow wala raasta). Tap par optimistic live look turant, confirm setState/homeJson se.

## Check
- node --check: launcher.html ke sab blocks + ui.html ke 92/93 blocks PASS (1 fail = purana GLSL shader, JS nahi — backup me bhi wahi tha).
- Headless Chromium (agent-browser): launcher standby + live screenshots PASS (SYSTEM LIVE/ACTIVE badges, violet reactor, BOLO/TAP KARO); ui.html preview (Android Proxy-stub) me #jh render PASS — notifications panel, reactor, WELCOME, weather 31°C, tiles 88/41/62, activity cards real text, neural canvas, ACTIVATE flow (wrap 'on', #btn STOP, jh SYSTEM LIVE) aur PLAY→chat PASS. Console errors: 0.
- Naya `tools/test_home_hud_contract.py`: 32/32 PASS. Poora contract suite (192 tests): **PASS=176 FAIL=16** — aur wo 16 exactly wahi hain jo owner ke original v7.215 zip me bhi fail the (pre-existing/stale; is part se koi naya red nahi). Is part me 2 test bhi fix hue: test_part5_ui_screens (jh MIC button 64px tap target + clipped text) aur test_part6_contract (BRIDGE list me "homeJson" add).
- Kotlin: sandbox me kotlinc nahi hai → compile NOT RUN (HomeHud pattern launcher/pehle parts jaise hi; manual review done).

## NOT DONE / NOT RUN
- Gradle build + JUnit + real phone: NOT RUN (sandbox me Android SDK/kotlinc nahi).
- DATA tile "4G/5G" nahi dikhata: READ_PHONE_STATE permission nahi hai — isliye honest WI-FI/MOBILE label.
- Mausam/news pehli baar khali dikh sakta hai (cache thanda) — background me aa jaata hai; launcher city prefs shared hai.
