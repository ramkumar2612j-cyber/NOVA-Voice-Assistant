#!/usr/bin/env python3
"""v7.206 TERMUX ROUTE contract: static wiring only. Kotlin compile / JUnit NOT run here (GitHub does that)."""
import sys
A = "app/src/main/java/com/nova/assistant/"
r = open(A + "TermuxRules.kt", encoding="utf-8").read()
svc = open(A + "JarvisService.kt", encoding="utf-8").read()
man = open("app/src/main/AndroidManifest.xml", encoding="utf-8").read()
fails = []
def ck(c, l):
    print(("PASS " if c else "FAIL ") + l)
    if not c: fails.append(l)
ck('"rm", "sudo"' in r or '"sudo"' in r and '"rm"' in r, "deny list has rm and sudo")
ck('ALLOW = setOf("pwd", "ls", "date", "uptime", "df -h", "git status")' in r, "auto-run list is read-only only")
ck("CONFIRM_MS = 5L" in r, "owner yes expires in 5 minutes")
ck("termuxRoute(text)" in svc and "private fun termuxRoute" in svc, "voice route wired")
ck("RUN_COMMAND_BACKGROUND" in svc and "com.termux.RUN_COMMAND" in svc, "Termux RUN_COMMAND intent used")
ck("com.termux.permission.RUN_COMMAND" not in man, "manifest does NOT add a new permission (permission guard); Termux needs the owner to grant it by hand")
ck("TermuxRules.isConfirm(text)" in svc and "termuxPendingCmd()" in svc, "owner yes runs only the pending command")
ck("Verdict.Denied" in svc[svc.index("private fun termuxRoute"):svc.index("private fun termuxRoute")+500], "denied commands never run")
for t in ("readOnlyIsAllowed", "otherCommandsNeedOwnerYes", "dangerousAlwaysDenied", "routeAndConfirm"):
    ck("fun " + t in open("app/src/test/java/com/nova/assistant/TermuxRulesTest.kt", encoding="utf-8").read(), "unit test " + t)
sys.exit(1 if fails else 0)
