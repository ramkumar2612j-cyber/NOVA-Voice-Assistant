#!/usr/bin/env python3
"""v6.97 wip14: static contract for 'open websites in Chrome tabs' + 'Play Store download'. No Android, no network.
Proves the SAFETY shape only (not that it works on a phone)."""
import re, sys, pathlib

root = pathlib.Path(__file__).resolve().parent.parent
src = root / "app/src/main/java/com/nova/assistant"
ctl = (src / "Control.kt").read_text(encoding="utf-8")
acc = (src / "NovaAccessibilityService.kt").read_text(encoding="utf-8")
svc = (src / "NovaService.kt").read_text(encoding="utf-8")
rul = (src / "PlayInstallRules.kt").read_text(encoding="utf-8")
fails = []

def check(ok, msg):
    if not ok:
        fails.append(msg)

# 1. the generic voice tap stays blocked in the Play Store
m = re.search(r"BLOCKED_PACKAGES\s*=\s*setOf\((.*?)\)", ctl, re.S)
check(m is not None and "com.android.vending" in m.group(1), "Control.BLOCKED_PACKAGES must still hold com.android.vending")

# 2. the 60 s window
check("WINDOW_MS = 60_000L" in rul, "PlayInstallRules.WINDOW_MS must be 60_000L")

# 3. playInstall: only the exact Install node, only after mayTap, one press (disarm before click)
pi = re.search(r"fun playInstall\(.*?\n        }\n", acc, re.S)
check(pi is not None, "playInstall not found")
if pi:
    body = pi.group(0)
    check(body.count("clickNode(") == 1, "playInstall must click exactly once")
    check("PlayInstallRules.mayTap(" in body, "playInstall must call PlayInstallRules.mayTap before pressing")
    check("exactInstallNode(root)" in body, "playInstall must press only exactInstallNode")
    check(body.index("PlayInstallRules.disarm(key)") < body.index("clickNode("), "playInstall must disarm before the click (one press only)")
    check(not re.search(r'"(Update|Buy|Install anyway|Sign in|Purchase)"', body), "playInstall must never name a button other than Install")
ex = re.search(r"fun exactInstallNode\(.*?\n        }\n", acc, re.S)
check(ex is not None and 't.equals("Install", ignoreCase = true)' in ex.group(0), "exactInstallNode must compare the WHOLE text with Install")
rt = re.search(r"fun playRoot\(.*?\n        }\n", acc, re.S)
check(rt is not None and acc.count("PLAY_PKG") >= 3 and "== PLAY_PKG" in rt.group(0), "playRoot must only return the com.android.vending window")

# 4. nothing else in the accessibility service clicks inside the Play Store: tapLabel still goes through Control.blocked
tl = re.search(r"fun tapLabel\(.*?\n        }\n", acc, re.S)
check(tl is not None and 'Control.blocked(' in tl.group(0), "tapLabel must still call Control.blocked")

# 5. handler order in NovaService.handle(): web open -> download -> agent task
a = svc.find("OpenWebRules.parseOpen(text)")
b = svc.find("OpenWebRules.parseDownload(text)")
c = svc.find("AgentRules.isTask(text)")
check(0 < a < b < c, "handle(): parseOpen, then parseDownload, both BEFORE AgentRules.isTask")

# 6. downloadApp is honest: success word only for the 'started' code, and the Play intent is pinned to the Play Store
dl = re.search(r"private fun downloadApp\(.*?\n    }\n", svc, re.S)
check(dl is not None, "downloadApp not found")
if dl:
    d = dl.group(0)
    check(d.count("download start ho gaya") == 1 and '"started" ->' in d, "'download start ho gaya' only for code started")
    check("setPackage(PLAY_PKG)" in d, "Play intent must be pinned to com.android.vending")
    check("PlayInstallRules.arm(" in d and "PlayInstallRules.disarm(" in d, "downloadApp must arm and disarm")

# 7. Chrome tabs: no Browser.EXTRA_APPLICATION_ID (it would reuse one tab)
check("EXTRA_APPLICATION_ID" not in svc.split("fun openChromeTab")[1].split("fun openAppOrSite")[0], "openChromeTab must not set EXTRA_APPLICATION_ID")

# 8. NOVA KNOWS the features: Help topics (voice/chat answer + AI facts), MADAD popup (ui.html), Skills screen, self-test
hg = (src / "HelpGuide.kt").read_text(encoding="utf-8")
ui = (root / "app/src/main/assets/ui.html").read_text(encoding="utf-8")
fc = (src / "FeatureCheck.kt").read_text(encoding="utf-8")
check('Topic("chrome"' in hg and 'Topic("download"' in hg, "HelpGuide must have the chrome and download topics")
check('"id": "chrome"' in ui and '"id": "download"' in ui, "ui.html MADAD list (CAPS) must have chrome and download")
check("nova whatsapp download karo" in ui and "nova youtube chrome pe kholo" in ui, "ui.html Skills screen must show both example commands")
check("case 'download'" in ui and "download:['ACCESSIBILITY ON KARO'" in ui, "ui.html must show the Accessibility need for download")
check("OpenWebRules.parseOpen(" in fc and "OpenWebRules.parseDownload(" in fc, "FeatureCheck self-test must check both new sentences")

if fails:
    for f in fails:
        print("FAIL:", f)
    sys.exit(1)
print("OK: open-web / download contract")
