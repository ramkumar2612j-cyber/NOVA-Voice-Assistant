#!/usr/bin/env python3
"""v7.202 AUTO RELEASE contract: static wiring only. Kotlin compile / JUnit NOT run here (GitHub does that)."""
import sys
A = "app/src/main/java/com/nova/assistant/"
rules = open(A + "AutoReleaseRules.kt", encoding="utf-8").read()
sb = open(A + "GitHubSelfBuild.kt", encoding="utf-8").read()
test = open("app/src/test/java/com/nova/assistant/AutoReleaseRulesTest.kt", encoding="utf-8").read()
fails = []
def ck(c, l):
    print(("PASS " if c else "FAIL ") + l)
    if not c: fails.append(l)
ck("MAX_PER_DAY = 3" in rules and "MIN_GAP_MS = 6L" in rules, "cap: 3 per day, 6 hours gap")
ck("phase != SelfBuildRules.GREEN" in rules and "locked" in rules and "inCall" in rules, "only green, never on lock / call / busy")
ck("autoInstallIfAllowed(ctx)" in sb and "private fun autoInstallIfAllowed" in sb, "hook: green poll -> auto install")
ck("PackageInstaller.STATUS" not in sb and "installSilently" not in sb and "setInstallReason" not in sb, "no silent install code added here")
ck("AUTO_ON" in sb and "getBoolean(AUTO_ON, true)" in sb, "owner can switch auto install off (default on)")
for t in ("greenBuildInstalls", "notGreenNeverInstalls", "lockedCallBusyOrDisabledBlock", "gapAndDailyCapHold", "countTodayCountsOnlyLast24h"):
    ck("fun " + t in test, "unit test " + t)
sys.exit(1 if fails else 0)
