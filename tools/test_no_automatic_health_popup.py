#!/usr/bin/env python3
"""Regression contract: no NOVA Health Check dialog opens automatically."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/com/nova/assistant/MainActivity.kt").read_text(encoding="utf-8")
HEALTH = (ROOT / "app/src/main/java/com/nova/assistant/HealthCheck.kt").read_text(encoding="utf-8")
SELF = (ROOT / "app/src/main/java/com/nova/assistant/SelfTest.kt").read_text(encoding="utf-8")
SERVICE = (ROOT / "app/src/main/java/com/nova/assistant/NovaService.kt").read_text(encoding="utf-8")
UI = (ROOT / "app/src/main/assets/ui.html").read_text(encoding="utf-8")
AUTO_TRIGGER = "function ucAuto(" in UI or "setTimeout(ucAuto" in UI
checks = {
    "Activity resume does not trigger automatic health popup": "HealthCheck.auto(this, web)" not in MAIN,
    "HealthCheck has no automatic resume/dialog API": "fun auto(" not in HEALTH and "AlertDialog.Builder" not in HEALTH,
    "Health report collection remains available": "fun run(ctx: Context)" in HEALTH and "fun report(ctx: Context" in HEALTH,
    "SAB TEST KARO can still collect health checks": "HealthCheck.run" in SELF,
    "floating card waits for speech instead of popping on wake-only false positives": "deferHudUntilSpeech" in SERVICE and "if (deferHudUntilSpeech) return" in SERVICE,
    "card is shown once command speech starts": "if (deferHudUntilSpeech) {\n                            deferHudUntilSpeech = false" in SERVICE,
    "setup/update center has no automatic startup hook": not AUTO_TRIGGER and "setTimeout(function(){if(ucFree())ucOpen()},350)" not in UI,
    "manual Update Center route remains available": "setTimeout(ucOpen,350)" in UI,
    "app greeting has no automatic setup/update timer": "setTimeout(ucAuto,1800)" not in UI,
    "v6.30 owner request: setup popup returns ONLY once per launch via ucStartup (never on resume, never the Kotlin AlertDialog)": "var ucBootShown=false" in UI and "function ucStartup(" in UI and "HealthCheck.auto(this, web)" not in MAIN,
}
for name, ok in checks.items():
    print(("PASS " if ok else "FAIL ") + name)
print(f"SUMMARY pass={sum(checks.values())} fail={len(checks)-sum(checks.values())}")
sys.exit(0 if all(checks.values()) else 1)
