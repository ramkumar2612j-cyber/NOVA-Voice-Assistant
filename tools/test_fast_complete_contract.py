#!/usr/bin/env python3
"""v6.68 structural contract (Kotlin NOT run here): agent actions wait for the screen to settle, slow AI calls are capped, stalls are retried, early stops say how far they got."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java", "com", "nova", "assistant")
NS = open(os.path.join(R, "NovaService.kt"), encoding="utf-8").read()
bad = 0
def check(n, c):
    global bad
    print(("PASS " if c else "FAIL ") + n)
    if not c: bad += 1
do = NS.split("private fun agentDo(")[1].split("/** Worker thread.")[0]
check("agentSettle exists (waits for change, then two equal reads)", "private fun agentSettle(before: Int, minMs: Long, maxMs: Long)" in NS and "stable >= 2" in NS)
check("agentDo has no fixed 1800/1000/700 ms sleep left for open/tap/back", not re.search(r"agentSleep\((1800|1000|700)\)", do))
check("every agentDo action that changes the screen settles", do.count("agentSettle(before") >= 6)
check("screenshot and explicit wait keep their own fixed wait", "agentSleep(1500)" in do and "agentSleep(a.sec * 1000L)" in do)
check("postFallback has caps with the old values as defaults", "readCapMs: Long = 25000L, deadlineMs: Long = Logic.CLOUD_DEADLINE_MS" in NS and "minOf(readCapMs, remain)" in NS)
check("plan, step and verify calls use shorter caps", "postFallback(body, 10000L, 16000L)" in NS and "postFallback(body, 16000L, 32000L)" in NS and "postFallback(body, 12000L, 20000L)" in NS)
check("voice / screen / transcribe callers use shorter caps (v7.75), web search keeps the default", "postFallback(body, 15000L, 30000L)" in NS and "postFallback(body, 10000L, 20000L)" in NS and "postFallback(b)" in NS)
check("stall is retried with a hint before failing", "stuckHints < 4" in NS and "Do NOT repeat them" in NS)
check("repair rounds raised to 3", "repairRounds < 3" in NS and "repairRounds < 2" not in NS)
check("early stop reports progress", "private fun agentProgress(" in NS and NS.count("agentProgress(hist)") >= 3)
check("safety path untouched (check/CONFIRM/DENY still before agentDo)", NS.index("AgentRules.check(a, node, pkg, packageName)") < NS.index("val res = agentDo(a)"))
check("verifier still runs on done", "agentVerify(fullTask, pkg, nodes, a.say, hist)" in NS)
check("no catch (_ and no !! added", "catch (_:" not in NS and "!!" not in NS.replace("!!!", ""))
sys.exit(1 if bad else 0)
