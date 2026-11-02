#!/usr/bin/env python3
"""R3 leftovers: audio recording must not start while video owns the mic; a dead writer must be reported, not called a clean save."""
import re, sys
J = "app/src/main/java/com/nova/assistant/"
ns = open(J + "NovaService.kt").read(); rs = open(J + "RecordSession.kt").read(); rr = open(J + "RecordRules.kt").read()
bad = 0
def check(n, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + n)
    if not ok: bad += 1
fn = ns.split("private fun startRecording()")[1].split("private fun stopRecording")[0]
check("start refuses while video is busy", "VideoRecordService.isBusy()" in fn and fn.index("VideoRecordService.isBusy()") < fn.index("RecordSession.start(this)"))
check("Saved carries writerError (old ctor still valid)", 'val writerError: String = ""' in rs)
check("stop() notes a writer that died before stop", "diedEarly" in rs and "wr.err" in rs)
check("both Saved returns pass the error", rs.count("drop, wErr)") == 2)
check("writer_err text exists", '"writer_err"' in rr)
st = ns.split("private fun stopRecording")[1].split("private fun runRecording")[0]
check("stopRecording appends the warning", "writerError" in st and "writer_err" in st)
mf = open("app/src/main/AndroidManifest.xml").read()
check("manifest declares ANSWER_PHONE_CALLS (PhoneActions checks it)", "android.permission.ANSWER_PHONE_CALLS" in mf)
import xml.dom.minidom as m; m.parseString(mf); check("manifest still well-formed", True)
sys.exit(1 if bad else 0)
