#!/usr/bin/env python3
"""v7.203 AUTO FIX LOOP contract: static wiring only. Kotlin compile / JUnit NOT run here (GitHub does that)."""
import sys
A = "app/src/main/java/com/nova/assistant/"
rules = open(A + "AutoFixRules.kt", encoding="utf-8").read()
sb = open(A + "GitHubSelfBuild.kt", encoding="utf-8").read()
test = open("app/src/test/java/com/nova/assistant/AutoFixRulesTest.kt", encoding="utf-8").read()
fails = []
def ck(c, l):
    print(("PASS " if c else "FAIL ") + l)
    if not c: fails.append(l)
ck("MAX_STREAK = 3" in rules and "MAX_PER_DAY = 3" in rules, "caps: 3 tries per red streak, 3 per day")
ck("autoFixIfAllowed(ctx, run.optLong" in sb and "private fun autoFixIfAllowed" in sb, "RED branch calls auto fix")
ck("GitHubSelfPatch.start(app, wish)" in sb, "fix goes through existing self-patch (protected files, revert on red)")
ck("FIX_STREAK, 0)" in sb, "green resets the red streak")
fx = sb[sb.index("private fun autoFixIfAllowed"):sb.index("private fun autoFixIfAllowed")+1800]
ck("wishFor(AutoFixRules.failedSteps(jobs))" in fx and "token" not in fx.split("val wish")[1].split("\n")[0], "token only used for the GitHub call, never in the AI wish")
ck("ProcessBuilder" not in fx and "Runtime.getRuntime" not in fx, "fix code runs no shell commands")
ck("PackageInstaller" not in rules, "fix rules never install directly")
for t in ("onlyRedBuildsGetFixed", "capsStopTheLoop", "failedStepsAreNamed", "wishNeverEmpty"):
    ck("fun " + t in test, "unit test " + t)
sys.exit(1 if fails else 0)
