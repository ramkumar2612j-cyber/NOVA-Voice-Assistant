#!/usr/bin/env python3
"""v7.200 DAILY CHECK contract: static wiring only. Kotlin compile / JUnit are NOT run here (GitHub does that)."""
import sys
A = "app/src/main/java/com/nova/assistant/"
rules = open(A + "DailyCheckRules.kt", encoding="utf-8").read()
svc = open(A + "JarvisService.kt", encoding="utf-8").read()
log = open(A + "DailyChangeLog.kt", encoding="utf-8").read()
test = open("app/src/test/java/com/nova/assistant/DailyCheckRulesTest.kt", encoding="utf-8").read()
fails = []
def ck(c, l):
    print(("PASS " if c else "FAIL ") + l)
    if not c: fails.append(l)
ck("MAX_FIXES_PER_DAY = 5" in rules, "fix cap is 5 per day")
ck("fun fixPlan(" in rules and "fun isFixApproval(" in rules, "fix only after owner approval words")
ck("private fun dailyReportText()" in svc and "DailyCheckRules.isReportQuestion(text)" in svc, "voice report hook wired in JarvisService")
ck("fun add(ctx" in log and "fun today(ctx" in log, "change log store exists")
ck("HARD DENY" in rules or "password" not in rules.split("object")[0], "no payment/password words in report code")
ck("GitHubSelfPatch." not in rules and "GitHubSelfPatch." not in svc[svc.index("private fun dailyReportText()"):svc.index("private fun dailyReportText()")+1500], "report never patches code by itself")
for t in ("reportQuestionAndApproval", "reportShowsBrokenFeatureAndAsksBeforeFix", "noBrokenMeansNoFixAsk", "fixPlanIsCapped"):
    ck("fun " + t in test, "unit test " + t)
sys.exit(1 if fails else 0)
