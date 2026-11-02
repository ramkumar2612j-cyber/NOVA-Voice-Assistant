#!/usr/bin/env python3
"""Part 8 guard (add-only): screenshot voice route exists in Logic.kt, handler exists in NovaService, new JUnit file present."""
import re, sys, pathlib
R = pathlib.Path(__file__).resolve().parent.parent
logic = (R/"app/src/main/java/com/nova/assistant/Logic.kt").read_text(encoding="utf-8")
svc = (R/"app/src/main/java/com/nova/assistant/NovaService.kt").read_text(encoding="utf-8")
t = R/"app/src/test/java/com/nova/assistant/Part8RulesTest.kt"
bad = []
if 'Cmd("global", "screenshot")' not in logic: bad.append("Logic.kt: no screenshot voice route")
if '"screenshot" ->' not in svc or "screenshotToGallery" not in svc: bad.append("NovaService: screenshot handler missing")
if not t.exists() or "screenshotVoiceTakesTheScreenshotAction" not in t.read_text(encoding="utf-8"): bad.append("Part8RulesTest.kt missing")
print("FAIL: " + "; ".join(bad) if bad else "PASS part8 contract"); sys.exit(1 if bad else 0)
