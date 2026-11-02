#!/usr/bin/env python3
"""v6.84: automatic first-open setup chain: bounded re-runs, versioned, grants nothing itself, never starts the microphone."""
import re, sys
MA = open("app/src/main/java/com/nova/assistant/MainActivity.kt", encoding="utf-8").read()
SR = open("app/src/main/java/com/nova/assistant/SetupChainRules.kt", encoding="utf-8").read()
UI = open("app/src/main/assets/ui.html", encoding="utf-8").read()
bad = 0
def check(n, c):
    global bad
    print(("PASS " if c else "FAIL ") + n)
    if not c: bad += 1
fr = MA.split("private fun firstRunPermissions()")[1].split("private fun specialOpenCounts")[0]
ms = MA.split("private fun missingSpecial()")[1].split("private fun nextSpecialPermission()")[0]
ns = MA.split("private fun nextSpecialPermission()")[1].split("/** Tells the page")[0]
check("chain is versioned and re-runs only through SetupChainRules.shouldRun", "SetupChainRules.VERSION" in fr and "SetupChainRules.shouldRun(" in fr and '"setup_runs"' in fr)
check("old one-shot flag all_asked_v662 is gone", "all_asked_v662" not in MA)
check("runs and opens are bounded", re.search(r"MAX_RUNS = \d", SR) and re.search(r"MAX_OPENS = \d", SR) and "SetupChainRules.nextStep(" in ns)
check("each page opened once per run (special_done reset at run start)", '"special_done", emptySet()' in fr and "done.add(id)" in ns)
check("chain only opens Settings pages and asks Android's own dialog: no grant / no secure-settings write", not any(x in fr + ms + ns for x in ("putInt(contentResolver", "Settings.Secure.putString", "WRITE_SECURE_SETTINGS", "grantRuntimePermission")))
check("chain never starts the microphone service", "startForegroundService" not in fr + ms + ns and "cfg.active = true" not in fr + ms + ns)
check("brand autostart page is offered once and only for known brands", "PowerGuideRules.targets(" in ms and 'id == "autostart"' in SR)
check("runs on every app open (onCreate) and continues on return (onResume)", "web.post { firstRunPermissions() }" in MA and "nextSpecialPermission()" in MA.split("override fun onResume()")[1][:1500])
check("manual re-run exists: bridge rerunSetup() + button in the status box", "fun rerunSetup()" in MA and "Android.rerunSetup()" in UI and "SETUP DOBARA CHALAO" in UI)
sys.exit(1 if bad else 0)
