#!/usr/bin/env python3
"""v6.30 structural contract (Kotlin NOT run): home feature-status box, once-per-launch setup popup, smoothness rules."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main")
rd = lambda *p: open(os.path.join(R, *p), encoding="utf-8").read()
FS, MA, UI, EL = rd("java/com/nova/assistant/FeatureStatus.kt"), rd("java/com/nova/assistant/MainActivity.kt"), rd("assets/ui.html"), rd("java/com/nova/assistant/ErrLog.kt")
bad = 0
def check(n, c):
    global bad
    print(("PASS " if c else "FAIL ") + n)
    if not c: bad += 1
check("FeatureStatus reads real state (permissions, keys, service, last error)", all(x in FS for x in ("checkSelfPermission", "canDrawOverlays", "NovaAccessibilityService.isEnabled", "SecureStore.hasKeys", "NovaService.running", "ErrLog.latest")))
check("every off/warn row carries a reason", FS.count('row(a,') >= 14 and "why" in FS)
check("status box does not run any feature or change anything", "startActivity" not in FS and "saveSlot" not in FS and "requestPermissions" not in FS)
check("bridge method exists", "fun getFeatureStatus()" in MA and "FeatureStatus.json(" in MA)
check("ErrLog.latest exists", "fun latest(" in EL)
check("home box in the page", 'id="fstat"' in UI and "function fsLoad(" in UI and "Android.getFeatureStatus()" in UI)
check("box says it is state, not a work test", "TEST REPORT" in UI.split("function fsRender")[1].split("function fsLoad")[0])
check("setup popup returns: once per launch, only when something needed is missing", "var ucBootShown=false" in UI and "function ucStartup(" in UI and "miss&&ucFree()" in UI)
check("popup waits for the opening animation", "classList.contains('ready')" in UI.split("function ucStartup")[1].split("var ucBootTimer")[0])
check("popup does not grant anything itself (only opens the Setup Center)", "ucOpen()" in UI.split("function ucStartup")[1].split("var ucBootTimer")[0] and "Android.requestBasics" not in UI.split("function ucStartup")[1].split("var ucBootTimer")[0])
check("no live blur anywhere (smoothness)", "backdrop-filter:none!important" in UI)
check("intro canvas degrades by itself on slow frames", "SBK*" in UI and "EMA>22" in UI and "DMAX=1" in UI)
check("heavy blur keyframes replaced", "@keyframes novaSheetIn{0%{opacity:0;transform:translateY(60px) scale(.96)}" in UI)
NS = open(os.path.join(R, "java/com/nova/assistant/NovaService.kt"), encoding="utf-8").read()
check("Auto-Heal watchdog runs every 30 s from onCreate", "h.postDelayed(healRun, 25_000L)" in NS and "h.postDelayed(this, 30_000L)" in NS.split("private val healRun")[1][:300])
check("Auto-Heal restarts the speech engine and notices a stalled mic (voice model refetch removed in v6.51)", all(x in NS.split("private fun heal()")[1].split("// ------------------------------------------------------------------ TTS")[0] for x in ("initTts()", "healMicWarned")))
check("Auto-Heal never grants permissions or starts services", not any(x in NS.split("private fun heal()")[1].split("// ------------------------------------------------------------------ TTS")[0] for x in ("startForegroundService", "requestPermissions", "Settings.System.putInt", "startActivity")))
check("home box shows what Auto-Heal did", "healInfo()" in FS and "Auto-Heal" in FS)
sys.exit(1 if bad else 0)
