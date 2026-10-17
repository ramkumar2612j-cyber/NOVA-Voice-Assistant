#!/usr/bin/env python3
"""Part 9 contract: ringer / rotation / screenshot are wired in every place a tool must be (schema, runTool, declarations, verify level, prompt).
Text-level only: it does NOT compile Kotlin."""
import re, sys
R = "app/src/main/java/com/nova/assistant/"
rd = lambda f: open(R + f, encoding="utf-8").read()
par, svc, tv = rd("PhoneActionRules.kt"), rd("NovaService.kt"), rd("ToolVerify.kt")
bad = []
def need(c, m):
    if not c: bad.append(m)
for t in ("ringer", "rotation"):
    need('"%s" to setOf("mode")' % t in par, t + " schema missing")
    need(par.count('"%s" to setOf("mode")' % t) >= 2, t + " required-args entry missing")
    need('"%s" -> %sTool(' % (t, t) in svc, t + " not dispatched in runTool")
    need('t.put(tool("%s"' % t in svc, t + " has no cloud declaration")
    need('"%s" to Level.VERIFIED' % t in tv, t + " has no verify level")
    need("%s(mode:" % t in par, t + " missing from the AI allow-list prompt")
need('"screenshot" -> {' in svc and "screenshotToGallery()" in svc, "screenshot missing in globalAct")
need('"lock", "screenshot")' in par, "screenshot not allowed in global_action validation")
# safety: silent must check Do Not Disturb access, rotation must check WRITE_SETTINGS, both read back
need("isNotificationPolicyAccessGranted" in svc, "silent ringer must check Do Not Disturb access")
need(re.search(r"private fun rotationTool.*?canWrite", svc, re.S) is not None, "rotation must check WRITE_SETTINGS")
need("am.ringerMode == want" in svc, "ringer must read the mode back")
need("backAuto == auto" in svc, "rotation must read the setting back")
# nothing removed: confirmation stays
need("private fun askFirst" in svc, "askFirst removed")
if bad:
    print("FAIL"); [print(" -", b) for b in bad]; sys.exit(1)
print("OK part9 phone tools contract")
