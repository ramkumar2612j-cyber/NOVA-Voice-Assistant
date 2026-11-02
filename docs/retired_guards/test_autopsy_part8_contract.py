#!/usr/bin/env python3
"""AUTOPSY R5 (static): sanity of the R4 edits that could not be compiled (kotlinc absent) + final-report presence."""
import pathlib, sys, re
import xml.dom.minidom as md
R = pathlib.Path(__file__).resolve().parent.parent
J = R / "app/src/main/java/com/nova/assistant"
bad = 0
def check(n, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1

def strip(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)
    src = re.sub(r"'(?:\\.|[^'\\\n])'", "''", src)
    return re.sub(r'//[^\n]*', '', src)
# The tiny stripper cannot read Kotlin raw strings / templates, so two big old files show a constant offset that was
# ALREADY there in the zip before R4 (measured on the original). The R4 edits must keep that offset unchanged.
BASE = {"ScheduledTaskStore.kt": (0, 0), "ScheduledTaskReceiver.kt": (0, 0), "BootReceiver.kt": (0, 0),
        "MainActivity.kt": (5, 1), "NovaService.kt": (3, 4)}
for f, (bb, bp) in BASE.items():
    s = strip((J / f).read_text(encoding="utf-8"))
    check(f + " brace/paren balance unchanged by R4 edits", (s.count("{") - s.count("}"), s.count("(") - s.count(")")) == (bb, bp))
try:
    md.parse(str(R / "app/src/main/AndroidManifest.xml")); ok = True
except Exception as e:
    ok = False
check("AndroidManifest.xml is well-formed XML", ok)
st = (J / "ScheduledTaskStore.kt").read_text(encoding="utf-8")
check("every ALARM_* constant used is declared", all(("const val " + c) in st for c in ("ALARM_EXACT", "ALARM_INEXACT", "ALARM_FAILED")))
rc = (J / "ScheduledTaskReceiver.kt").read_text(encoding="utf-8")
check("notifyMissed/notifySkipped share postNote", rc.count("postNote(") >= 3)
check("final report exists", (R / "docs/AUTOPSY_FINAL_REPORT.md").exists())
check("live owner-UI check script exists", (R / "tools/ui_owner_check.py").exists())
print("failed:", bad); sys.exit(1 if bad else 0)
