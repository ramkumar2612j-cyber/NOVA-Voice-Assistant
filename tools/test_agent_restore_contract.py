#!/usr/bin/env python3
"""v6.72 structural contract (Kotlin NOT run here): agent progress survives a restart (privacy-safe), long jobs keep their start, the owner is told about an interrupted job."""
import os, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java", "com", "nova", "assistant")
rd = lambda n: open(os.path.join(R, n), encoding="utf-8").read()
NS, AL, AR, AT = rd("NovaService.kt"), rd("AgentTaskLedger.kt"), rd("AgentRules.kt"), rd("AgentTaskRules.kt")
bad = 0
def check(n, c):
    global bad
    print(("PASS " if c else "FAIL ") + n)
    if not c: bad += 1
pl = AT.split("fun progressLine(")[1].split("const val PROGRESS_LINES")[0]
check("progress line never carries typed text or a screen label", "a.text" not in pl and "label" not in pl.lower() and 'act == "open_app"' in pl)
check("progress is bounded (lines + characters) and redacted", "PROGRESS_LINES = 12" in AT and "PROGRESS_CHARS = 900" in AT and "safeSummary(line)" in AT)
check("ledger stores progress, reads it back, and the resumed copy keeps it", AL.count("progress") >= 7 and ".put(\"progress\", t.progress)" in AL and "progress = old.progress" in AL)
check("old ledger records (no progress field) still load", 'o.optString("progress")' in AL)
check("each step saves its progress line", "progressLine = AgentTaskRules.progressLine(step, a.act, a.app" in NS)
check("a resumed task starts with the earlier progress", "(before the interruption)" in NS and "?.progress.orEmpty()" in NS)
check("resume is still owner-only and safe-only (rules untouched)", "fun resumeLastInterruptedIfSafe(" in AL and "isResumable(old, nowMs)" in AL and "all.any { AgentTaskRules.isActive(it.state) }" in AL)
check("the owner is told once when a restart cut a job short, no automatic replay", "if (interrupted > 0)" in NS and "ledger.resumeStatusText()" in NS and "resumeLastInterruptedIfSafe" not in NS.split("if (interrupted > 0)")[1][:400])
check("agent prompt lists EARLIER actions, not only the last 10", "fun earlierActions(" in AR and "EARLIER ACTIONS" in AR and "RECENT_ACTIONS = 10" in AR)
check("earlier list is bounded", "EARLIER_CHARS = 1200" in AR and "EARLIER_LINE = 70" in AR)
check("model switch keeps the task: every step prompt still carries task + hist", "AgentRules.userPrompt(fullTask, step, pkg, nodes, hist)" in NS)
check("verifier and safety checks untouched", "agentVerify(fullTask, pkg, nodes, a.say, hist)" in NS and NS.index("AgentRules.check(a, node, pkg, packageName)") < NS.index("val res = agentDo(a)"))
check("no speaker recognition / voice lock added", not any(w in (AT + AL).lower() for w in ("speaker", "voicelock", "enrol")))
check("no catch (_ and no !! added in the touched agent files", "catch (_:" not in AT + AL + AR and "!!" not in (AT + AL + AR).replace("!!!", ""))
sys.exit(1 if bad else 0)
