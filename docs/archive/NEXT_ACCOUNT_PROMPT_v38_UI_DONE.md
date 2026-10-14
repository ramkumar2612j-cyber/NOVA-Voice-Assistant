# NOVA v38 handover (read first, then HANDOVER_v16.md hard rules)

## Owner
Phone only (Chrome + GitHub web). Reply in Hinglish, short steps. Never say DONE/WORKING without an evidence level
("tests ran here", "Kotlin not compiled", "not phone tested"). Zip < 25 MB, name stays NOVA_v15_assistant.zip.
Hard rules unchanged: risky actions need spoken "yes", no silent installs, keys never shown/logged, protected screens never to cloud, no account-limit bypass automation.

## State of this zip (versionName 6.18, NOTHING phone-tested, Kotlin NOT compiled, full Android build NOT run)
Evidence for everything below: headless Chromium/SwiftShader here + python contract tests + node --check + xmllint.
- 3D orb: orb.frag inlined in ui.html as <script type="x-shader/x-fragment" id="orb_fs"> (test_ui_v37_contract checks it equals tools/orb/orb.frag; edit the .frag, then re-inline). Canvas #orb3d; old 2D #gl is the fallback (no WebGL2 / shader error / context lost / still >24 ms at 0.5 scale). Governor 1 -> .75 -> .5, DPR cap 2, pauses when hidden, reduced-motion = one still frame per mood. Poster app/src/main/assets/orb_poster.png.
- Bottom tab bar #tabs: HOME / SKILLS / LIMITS / CHAT (= old #chatb) / SETUP (= old #gear), both moved into the bar by JS. #panel/#chat/#sheet/#uc get bottom:var(--tabb). window.novaPanel(open) = quick settings open/close without the doors animation; novaGo(id) (feature deep links) still uses doors.
- SKILLS (#v_skills): grouped cards, chips ON/SETUP/ALWAYS READY from getStatus/getSettings/has*Key, SAY buttons prefill the chat input (only verified phrases).
- LIMITS (#v_limits): Android.getApiUsage ring gauges, used / total / remaining, LIVE|PHONE COUNT|COUNT ONLY, ESTIMATE/CUSTOM chips, EDIT LIMIT (setApiLimit), DEFAULT (0), RESET TODAY (two-tap), merges getProviderHealth cooldown. Unknown limits show "publish nahi" / "pata nahi", never an invented number.
- SETUP: every #panel .box is an accordion (static title map by contained id, window.novaAccOpen(id) used by both go() helpers). Bare voice items wrapped into one box.
- Theme override block id="nova-v37-theme" (graphite #0b0d12, teal #35F2C2, violet #7C5CFF, amber #FFB547). Old blue rules that hardcode rgba still exist; a full recolour of the old blocks is NOT done.
- Launcher: res/drawable-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png (orb still, subject ~62 % of the canvas), old foreground xml deleted, background recoloured. Splash: drawable/splash_icon.xml is now an animated-vector over splash_icon_vec.xml + res/animator/splash_spin_{cw,ccw}.xml (xmllint only, aapt2 never ran).
- tools: orb/render_orb.py (single still), orb/render_master.py (tiled supersampled master, 8192 px took ~4.5 min here), orb/render_clip.py (webm), ui_shots.py (Playwright + fake Android bridge), test_ui_v37_contract.py. Master PNG (8192x8192, 13 MB) and a 6 s webm were delivered OUTSIDE the zip.

## NOT done / ideas
- Run GitHub "Build and Test APK" FIRST (Kotlin hooks from v36 + new resources: check res merge, adaptive icon, animated-vector splash, versionName 6.18).
- Phone test list: orb speed + fallback, tab bar vs gesture bar, keyboard over chat input, LIMITS values after real calls, EDIT LIMIT, splash animation on Android 12+.
- Glass-tile icon set for the home feature cards (step 6 of v37) not redone; skills cards use the existing glyphs.
- Orb clip is not a seamless loop (rotation speeds are not commensurate).
