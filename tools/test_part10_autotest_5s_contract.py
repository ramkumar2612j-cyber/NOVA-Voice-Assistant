#!/usr/bin/env python3
"""Part 10 contract: feature status refreshes every 5 s, AutoTest watches every 5 s while the screen is open, SelfTest covers the newer features."""
from pathlib import Path
R = Path(__file__).resolve().parents[1]; J = R / "app/src/main/java/com/nova/assistant"
ui = (R / "app/src/main/assets/ui.html").read_text()
at = (J / "AutoTest.kt").read_text(); st = (J / "SelfTest.kt").read_text(); main = (J / "MainActivity.kt").read_text()
checks = {
    "ui feature status refresh is 5000 ms": "fsLoad()},5000);" in ui and "fsLoad()},8000);" not in ui,
    "AutoTest watcher loop runs every 5 s and stops when the screen closes": "while (true)" in at and "5_000L" in at and "!active" in at and "fun stop()" in at,
    "onPause stops the watcher (no background polling)": "AutoTest.stop()" in main,
    "signature covers write settings, overlay, DND, smart reply": all(x in at for x in ("canWrite(a)", "canDrawOverlays(a)", "isNotificationPolicyAccessGranted", "smartReplyOn(a)")),
    "re-test only when state changed (shouldRun with sig) and not more than once a minute": "sig != lastSig" in at and "MIN_GAP_MS = 60_000L" in at,
    "SelfTest checks DND, ringer, screenshot, rotation, AI SMART read-only": all(x in st for x in ("Ringer silent (Do Not Disturb access)", "Screenshot (Accessibility)", "Screen rotation (Write settings)", "AI SMART reply")),
    "SelfTest understands ringer and rotation phrases": '"vibrate par karo" to "ringer"' in st and '"auto rotate off" to "rotation"' in st,
}
for l, ok in checks.items(): print(("PASS " if ok else "FAIL ") + l)
raise SystemExit(0 if all(checks.values()) else 1)
