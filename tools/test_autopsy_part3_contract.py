#!/usr/bin/env python3
"""AUTOPSY PART 3 (static): music play and the real phone call must never claim success after a raw background startActivity."""
import pathlib, sys
J = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/nova/assistant"
svc = (J / "NovaService.kt").read_text(encoding="utf-8")
bad = 0
def check(n, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
music = svc.split("private fun musicPlay(")[1].split("v6.92: works with ANY installed app")[0]
call = svc.split("private fun callTool(")[1].split("private fun matchSpokenContactChoice")[0]
check("musicPlay has no raw startActivity", "startActivity(" not in music)
check("musicPlay verifies through launch()", music.count("launch(") >= 2)
check("musicPlay stops fallbacks when background start is blocked", music.count("lastLaunchBlocked") >= 2)
check("launch() records the blocked result", "lastLaunchBlocked = true" in svc and "lastLaunchBlocked = false" in svc)
check("callTool ACTION_CALL goes through launch()", "launch(Intent(Intent.ACTION_CALL" in call and "startActivity(" not in call)
check("callTool still falls back to the dialer", "Intent.ACTION_DIAL" in call)
print("failed:", bad); sys.exit(1 if bad else 0)
