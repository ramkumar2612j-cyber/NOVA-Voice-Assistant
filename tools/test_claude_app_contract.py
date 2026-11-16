#!/usr/bin/env python3
"""v7.205 CLAUDE APP ROUTE contract: static wiring only. Kotlin compile / JUnit NOT run here (GitHub does that)."""
import sys
A = "app/src/main/java/com/nova/assistant/"
r = open(A + "ClaudeAppRules.kt", encoding="utf-8").read()
svc = open(A + "JarvisService.kt", encoding="utf-8").read()
test = open("app/src/test/java/com/nova/assistant/ClaudeAppRulesTest.kt", encoding="utf-8").read()
fails = []
def ck(c, l):
    print(("PASS " if c else "FAIL ") + l)
    if not c: fails.append(l)
ck('PACKAGE = "com.anthropic.claude"' in r, "uses the Claude app package")
ck("claudeAppRoute(text)" in svc and "private fun claudeAppRoute" in svc, "voice route wired in JarvisService")
ck("startActivity(li)" in svc[svc.index("private fun claudeAppRoute"):svc.index("private fun claudeAppRoute")+1200], "opens the app on the phone")
ck("sendKeyEvent" not in svc[svc.index("private fun claudeAppRoute"):svc.index("private fun claudeAppRoute")+1200] and "ACTION_SEND" not in svc[svc.index("private fun claudeAppRoute"):svc.index("private fun claudeAppRoute")+1200], "route never sends a message by itself")
ck("isDenied(q)" in svc[svc.index("private fun claudeAppRoute"):svc.index("private fun claudeAppRoute")+300], "payment/secret questions refused before opening")
for t in ("routeTriggers", "questionIsExtracted", "paymentQuestionsAreRefused"):
    ck("fun " + t in test, "unit test " + t)
sys.exit(1 if fails else 0)
