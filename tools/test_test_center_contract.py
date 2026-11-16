#!/usr/bin/env python3
"""v7.195 TEST CENTER contract (static): one card in Settings runs selfTest -> verifyNow -> fullReport and shows one merged result."""
import re, os, sys
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ui = open(R + "/app/src/main/assets/ui.html", encoding="utf8").read()
main = open(R + "/app/src/main/java/com/nova/assistant/MainActivity.kt", encoding="utf8").read()
fails = 0
def check(c, l):
    global fails
    print(("PASS " if c else "FAIL ") + l)
    if not c: fails += 1
for i in ("tc_box", "tc_chip", "tc_stage", "tc_run", "tc_show", "tc_bad", "tc_out", "tc_copy", "tc_share"):
    check('id="%s"' % i in ui, "ui has #" + i)
js = (re.search(r'<script id="v195-test-center">(.*?)</script>', ui, re.S) or [None, ""])[1]
check(bool(js), "test center script present")
for fn in ("selfTest", "verifyNow", "fullReport", "copyText", "shareText"):
    check("Android." + fn + "(" in js, "script calls Android." + fn)
    check(re.search(r"fun %s\(" % fn, main) is not None, "bridge has " + fn)
check("onSelfTestDone" in js and "onFeatureVerify" in js and "onAutoTest" in js, "chains the 3 existing callbacks (not replaced)")
check("arm()" in js and "90000" in js, "90 s guard so a stuck stage cannot hang the card")
check("payment" not in js.lower() and "Android.call(" not in js, "no call/payment from the test card")
print("PASS test_test_center_contract" if not fails else "FAIL %d" % fails)
sys.exit(1 if fails else 0)
