#!/usr/bin/env python3
"""AUTOPSY R2 (static): picture failure must say the real reason; a failed gallery save must not leave a pending row."""
import pathlib, sys
J = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/nova/assistant"
svc = (J / "NovaService.kt").read_text(encoding="utf-8")
ig = (J / "ImageGen.kt").read_text(encoding="utf-8")
bad = 0
def check(n, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
img = svc.split("private fun imageBegin(")[1].split("learn from the internet")[0]
check("image failure includes the provider reason", "val reason = r.msg.trim()" in img and "$reason" in img)
save = ig.split("fun saveToGallery(")[1]
check("gallery save deletes the pending row on failure", "res.delete(uri, null, null)" in save)
check("gallery save still sets IS_PENDING back to 0", "IS_PENDING, 0" in save)
print("failed:", bad); sys.exit(1 if bad else 0)
