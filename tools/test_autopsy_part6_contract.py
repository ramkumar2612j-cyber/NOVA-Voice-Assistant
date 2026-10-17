#!/usr/bin/env python3
"""AUTOPSY R3 (static): video + recordings. One check group per fix."""
import pathlib, sys
J = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/nova/assistant"
A = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets"
svc = (J / "NovaService.kt").read_text(encoding="utf-8")
vid = (J / "VideoRecordService.kt").read_text(encoding="utf-8")
rs = (J / "RecordSession.kt").read_text(encoding="utf-8")
ui = (A / "ui.html").read_text(encoding="utf-8")
bad = 0
def check(n, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1

# 1. audio start must refuse while video owns the mic
sr = svc.split("private fun startRecording()")[1].split("private fun stopRecording")[0]
check("audio start refuses while video is busy", "VideoRecordService.isBusy()" in sr and sr.index("VideoRecordService.isBusy()") < sr.index("RecordSession.start(this)"))

# 2. MediaRecorder.stop() failure must never be reported as saved
check("cleanupCapture reports stop() failure", "private fun cleanupCapture(finalize: Boolean): Boolean" in vid and "stopOk = false" in vid)
sv = vid.split("private fun stopVideo(")[1].split("private fun cleanupCapture")[0]
check("export only when stop() succeeded", "if (stopOk && f != null" in sv)
check("stop failure gets its own status", '"stop_failed"' in sv)
check("UI knows stop_failed", "s==='stop_failed'" in ui)

# 3. automatic stops and failed starts are visible
check("result notification exists", "private fun notifyResult(status: String)" in vid and "notify(3," in vid)
check("stopVideo notifies", "notifyResult(lastStatus)" in sv)
st = vid.split("private fun startVideo(")[1].split("private fun notification(")[0]
check("permission/not_visible/failed starts notify", st.count("notifyResult(lastStatus)") >= 3)

# 4. a dead audio writer must not look like a clean manual save
check("Saved carries the writer error", 'val err: String = ""' in rs and "wr.err" in rs and "werr" in rs)
sp = svc.split("private fun stopRecording(")[1].split("private fun runRecording")[0]
check("manual stop warns when writer had an error", "s.err.isNotEmpty()" in sp)
print("failed:", bad); sys.exit(1 if bad else 0)
