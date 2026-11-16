#!/usr/bin/env python3
"""Part 9 guard (add-only): scheduled-message fix present + test file present."""
import sys, pathlib
R = pathlib.Path(__file__).resolve().parent.parent
r = (R/"app/src/main/java/com/nova/assistant/ScheduledTaskRules.kt").read_text(encoding="utf-8")
t = R/"app/src/test/java/com/nova/assistant/Part9ScheduleTest.kt"
bad = []
if 'it == "pe"' not in r: bad.append("ScheduledTaskRules: 'pe' not accepted as 'par'")
if "ko >= afterTime" not in r: bad.append("ScheduledTaskRules: recipient still leaks into message")
if not t.exists(): bad.append("Part9ScheduleTest.kt missing")
print("FAIL: " + "; ".join(bad) if bad else "PASS part9 contract"); sys.exit(1 if bad else 0)
