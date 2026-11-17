#!/usr/bin/env python3
"""v7.216 HOME HUD L3 contract: launcher.html naya HUD + ui.html #jh layer + HomeHud.kt real-data bridge.
Static check (repo convention): files me naye blocks maujood hain, sahi jagah par hain, aur purane
contract elements (LB.act kinds, setState ids) barkarar hain."""
import sys

LA = "app/src/main/assets/launcher.html"
UI = "app/src/main/assets/ui.html"
HH = "app/src/main/java/com/nova/assistant/HomeHud.kt"
MA = "app/src/main/java/com/nova/assistant/MainActivity.kt"
LS = "app/src/main/java/com/nova/assistant/LauncherActivity.kt"

la = open(LA, encoding="utf-8").read()
ui = open(UI, encoding="utf-8").read()
hh = open(HH, encoding="utf-8").read()
ma = open(MA, encoding="utf-8").read()
ls = open(LS, encoding="utf-8").read()

fails = []
def ck(c, l):
    print(("PASS " if c else "FAIL ") + l)
    if not c: fails.append(l)

# ---- launcher.html: naya HUD (purana backup add-only tree me rakha gaya hai)
ck(la.count('<canvas id="wp"') == 1 and la.count('id="bg"') == 1, "launcher: wallpaper + stars canvas")
ck('id="notif"' in la and "SYSTEM METRICS" in la and "NETWORK STATUS" in la and "AI MODULE STATUS" in la, "launcher: notifications panel rows")
ck('id="bigact"' in la and "ACTIVATE" in la, "launcher: big JARVIS ACTIVATE ring")
ck('id="m1"' in la and 'id="m2"' in la and 'id="m3"' in la and 'id="m4"' in la and 'id="m5"' in la, "launcher: metric tiles (m1..m5)")
ck('id="rc1"' in la and 'id="rc2"' in la and 'id="rc3"' in la, "launcher: recent activity cards")
ck('LB.act(\'city\',\'\')' in la and "LB.act('orb','')" in la.replace("actTap()", ""), "launcher: city + orb act kinds intact")
ck("LB.act('submit','aaj ki news batao')" in la, "launcher: news card real command")
ck("window.setState" in la and "s.run" in la and "s.cpu" in la and "s.net" in la and "s.rem" in la and "s.news" in la, "launcher: setState naye real fields padhta hai")
ck("window.goHome" in la and "window.onBack" in la, "launcher: goHome/onBack contract")
ck("#00F0FF" in la and "#9D00FF" in la, "launcher: cyan+violet theme (no page filter here)")
ck("prefers-reduced-motion:reduce" in la, "launcher: reduced-motion respected")

# ---- ui.html: #jh layer sabse aakhir me
ck('id="v7216-jh-css"' in ui and 'id="v7216-jh-js"' in ui, "ui: v7216 layer blocks present")
ck(ui.index('id="v7216-jh-css"') > ui.rfind('</style>', 0, ui.index('id="v7216-jh-css"')), "ui: css layer after earlier styles")
ck(ui.index('id="v7216-jh-js"') > ui.rfind('</script>', 0, ui.index('id="v7216-jh-js"')), "ui: js layer after earlier scripts (add-only last)")
ck(ui.count('</body>') == 1 and ui.index('id="v7216-jh-js"') < ui.index('</body>'), "ui: layer before single body close")
ck("d.id='jh'" in ui and "jh_notif" in ui and "jh_arc" in ui and "jh_act" in ui and "jh_nn" in ui, "ui: #jh home elements (notif, reactor, act ring, neural canvas)")
ck("Android.homeJson" in ui and "Android.toggle" in ui and "Android.sendText" in ui, "ui: bridge calls (homeJson/toggle/sendText)")
ck("html.jhom #tabs{display:none" in ui, "ui: old tab bar hides while jh home is on")
_jhcss = ui.split('id="v7216-jh-css"')[1].split('</style>')[0]
ck("prefers-reduced-motion:reduce" in _jhcss, "ui: reduced-motion in jh layer")
ck("add-only" in _jhcss.lower() or "add-only" in ui.split('id="v7216-jh-css"')[1][:900], "ui: layer doc'd as add-only")

# ---- HomeHud.kt: sirf real values, koi invented number nahi
def WebOK(_s):
    return "WebSources.newsUrl" in _s

ck("JarvisService.running" in hh and "/proc/stat" in hh and "TRANSPORT_WIFI" in hh and "ScheduledTaskStore.list" in hh, "HomeHud: real sources (service, cpu, net, schedule)")
ck("news.google.com" not in hh and WebOK(hh), "HomeHud: news URL sirf WebSources se (host hard-code nahi)")
ck("20 * 60_000L" in hh and "30 * 60_000L" in hh, "HomeHud: 20/30 min cache TTL")
ck("PREFS = \"launcher\"" in hh, "HomeHud: launcher ke shared prefs reuse (same weather cache)")
ck('o.put("bat"' in hh and 'o.put("cpu"' in hh and 'o.put("net"' in hh and 'o.put("rem"' in hh and 'o.put("news"' in hh, "HomeHud: json keys")

# ---- Kotlin wiring
ck("fun homeJson(): String" in ma and "HomeHud.json(this@MainActivity)" in ma, "MainActivity: homeJson bridge")
ck("HomeHud.cpu()" in ls and "HomeHud.network(this)" in ls and "HomeHud.reminder(this)" in ls and "HomeHud.news(this)" in ls, "LauncherActivity: stateJson HUD fields")

# ---- purane contract barkarar
for kid in ['id="btn"', 'id="log"', 'id="tabs"', 'id="c_in"', 'id="panel"']:
    ck(kid in ui, "ui: purana element intact " + kid)
ck("looksLikeCommand" in ls and "wakeJarvis" in ls, "LauncherActivity: purana flow intact")

sys.exit(1 if fails else 0)
