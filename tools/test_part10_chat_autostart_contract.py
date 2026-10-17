#!/usr/bin/env python3
"""Part 10 contract: typing in the chat while NOVA is off starts NOVA and sends the same text (no ACTIVATE tap needed)."""
from pathlib import Path
ui = (Path(__file__).resolve().parents[1] / "app/src/main/assets/ui.html").read_text()
checks = {
    "send() falls back to autoStartSend when sendText says off": "else{autoStartSend(t)}" in ui,
    "auto start uses the normal toggle (same permission checks)": "r=Android.toggle()" in ui and "function autoStartSend(t)" in ui,
    "message is re-sent until the service is alive, bounded retries": "++tries<10" in ui and "Android.sendText(t)" in ui,
    "mic permission problem is told plainly": "Mic permission chahiye" in ui,
    "typed text is kept if start fails": ui.count("ci.value=''") >= 2,
}
for l, ok in checks.items(): print(("PASS " if ok else "FAIL ") + l)
raise SystemExit(0 if all(checks.values()) else 1)
