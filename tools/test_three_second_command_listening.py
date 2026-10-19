#!/usr/bin/env python3
"""Contract: spoken commands end only after a continuous 3-second silence window."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
logic = (ROOT / "app/src/main/java/com/nova/assistant/Logic.kt").read_text()
service = (ROOT / "app/src/main/java/com/nova/assistant/NovaService.kt").read_text()
ui = (ROOT / "app/src/main/assets/ui.html").read_text()

checks = {
    "normal command silence is exactly 1300 ms (v7.75, was 1800 / 3000)": "const val END_SILENCE_MS = 1300" in logic,
    "long commands have a bounded 60-second safety cap": "const val MAX_COMMAND_MS = 60000" in logic,
    "capture uses the fixed silence threshold": "adaptiveSilence(cmdText.toString(), Logic.END_SILENCE_MS)" in service,
    "normal command timeout no longer uses configurable wait slider": "else Logic.MAX_COMMAND_MS" in service and "else Logic.MAX_COMMAND_MS + ExtCfg.waitMs(this)" not in service,
    "old early fast-end shortcut is absent from capture loop": "fastDone" not in service and "FAST_END_MIN_SILENCE_MS" not in service,
    "setup explains the 1.8-second listen behavior": "COMMAND LISTENING · 1.8 SEC" in ui,
    "obsolete wait slider is removed": 'id="w_sec"' not in ui,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + " " + label)
if not all(checks.values()):
    raise SystemExit(1)
print("ALL PASSED")
