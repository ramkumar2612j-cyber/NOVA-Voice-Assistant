#!/usr/bin/env python3
"""AUTOPSY PART 2 (static): wake word must work with screen off; overlay nudge must repeat; APK hash must be verified."""
import pathlib, sys
R = pathlib.Path(__file__).resolve().parent.parent / "app/src/main"
J = R / "java/com/nova/assistant"
rd = lambda p: (R / p if "/" in p else J / p).read_text(encoding="utf-8")
svc, cfg, ma, ui, um = rd("NovaService.kt"), rd("ExtCfg.kt"), rd("MainActivity.kt"), rd("assets/ui.html"), rd("UpdateManager.kt")
bad = 0
def check(n, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
check("listenLocked default ON", "fun listenLocked" in cfg and "getBoolean(LISTEN_LOCKED, true)" in cfg)
check("lockQuiet uses listenLocked", "ExtCfg.lockGuard(this) && !ExtCfg.listenLocked(this)" in svc)
check("bridge get/set listenLocked", "fun getListenLocked" in ma and "fun setListenLocked" in ma)
check("UI switch wb_ll wired", 'id="wb_ll"' in ui and "Android.setListenLocked" in ui and "Android.getListenLocked" in ui)
check("overlay nudge repeats after 10 min", "overlayNudgedAt" in svc and "600_000L" in svc.split("private fun overlayNudge")[1][:400])
check("APK/model download hash verified", 'Updater.hex(md.digest()) != item.sha256' in um and "part.delete()" in um)
print("failed:", bad); sys.exit(1 if bad else 0)
