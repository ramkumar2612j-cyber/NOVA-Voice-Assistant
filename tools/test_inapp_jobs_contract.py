#!/usr/bin/env python3
"""v6.67 structural contract (Kotlin NOT run here): in-app jobs, ask_wait, popup behaviour, nothing old removed."""
import os, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java", "com", "nova", "assistant")
rd = lambda n: open(os.path.join(R, n), encoding="utf-8").read()
AR, NS, FS = rd("AgentRules.kt"), rd("NovaService.kt"), rd("FeatureStatus.kt")
bad = 0
def check(n, c):
    global bad
    print(("PASS " if c else "FAIL ") + n)
    if not c: bad += 1
check("ask_wait is a known action, parsed with a kind, allowed by check()", '"ask_wait"' in AR.split("private val ACTS")[1][:200] and 'act == "ask_wait" && say.isBlank()' in AR and '"ask_wait", "wait"' in AR)
check("old ask (job stops) still exists", '"ask" ->' in NS and 'act":"ask"' in AR)
check("isTask routes in-app jobs; WhatsApp send flow keeps its own route", "if (isAppJob(n)) return true" in AR and "WhatsAppCommandRules.parse(text) != null && !openChat) return false" in AR)
check("AI chat, recording, call, screen words are never an app job", all(w in AR.split("NOT_APP_RE =")[1][:140] for w in ("gpt", "record", "call", "screen")))
check("number words 1-9 and answerLine exist", "fun numberFrom(" in AR and "fun answerLine(" in AR and '"teesra"' in AR)
check("prompt has chat + YouTube playbooks and never-subscribe rule", "CHAT in Instagram" in AR and "YOUTUBE" in AR and "Never tap Subscribe" in AR and "Kaun sa? 1" in AR)
check("agent asks, listens and goes on (ask_wait branch, max 3, one retry)", '"ask_wait" -> {' in NS and "askWaits > 3" in NS and "agentAskAnswer(" in NS.split('"ask_wait" -> {')[1][:900])
check("answer is handed back before the dictation / yes-no branches", NS.index("p === agentAnsPending ->") < NS.index("isDict -> dictatedReply(text)"))
check("answer wait has its own longer TTL", "private fun pendingTtl(" in NS and NS.count("pendingTtl(p)") >= 3)
check("empty answer releases the waiting job without a 'cancelled' speech", "val agentWaiting = agentAnsPending != null" in NS)
check("card stays while a job runs and still closes by itself", "|| agentRunning" in NS.split("private val hudHide")[1][:600] and "hd.hide()" in NS.split("private val hudHide")[1][:600])
check("short app jobs skip the planning call", "AgentRules.isAppJob(task)" in NS)
check("status row for in-app jobs", '"inapp"' in FS)
check("no voice lock / lock gate came back", "lockedPhoneAllows" not in NS and not os.path.exists(os.path.join(R, "PhoneLock.kt")))
check("risky taps still blocked (HARD words, Subscribe)", '"subscribe"' in AR and "HARD" in AR)
sys.exit(1 if bad else 0)
