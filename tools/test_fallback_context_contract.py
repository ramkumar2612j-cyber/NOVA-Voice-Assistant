#!/usr/bin/env python3
"""v6.70 structural contract (Kotlin NOT run here): a backup AI gets the running conversation + the current task line; one clear missing-config report."""
import os, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java", "com", "nova", "assistant")
rd = lambda n: open(os.path.join(R, n), encoding="utf-8").read()
NS, FP, LC, LB, FC, CR = rd("NovaService.kt"), rd("FreeProviders.kt"), rd("LocalChat.kt"), rd("LocalBrains.kt"), rd("FallbackContext.kt"), rd("ConfigReport.kt")
bad = 0
def check(n, c):
    global bad
    print(("PASS " if c else "FAIL ") + n)
    if not c: bad += 1
chain = NS.split("private fun runFreeChain(")[1].split("/** JSON action pass")[0]
check("free chain passes the conversation turns", "turns = fallbackTurns()" in chain)
check("free chain system prompt carries the current task line", "fallbackTaskLine(en)" in chain and "FallbackContext.systemWith(" in chain)
check("free phone-action pass gets the turns too", "budgetMs = 9000L, turns = fallbackTurns()" in NS)
check("buildBody + ProviderChain.run accept turns with an empty default (old callers unchanged)", "turns: List<Pair<String, String>> = emptyList()" in FP and FP.count("turns = turns") >= 1)
check("only user/assistant roles go into the provider body", 'r != "user" && r != "assistant"' in FP)
check("backup answers are remembered (thread continues, Gemini sees them later)", "remember(said, guarded)" in NS and "remember(said, a)" in NS)
check("offline model gets the last turns", "fallbackEarlier()" in NS and "earlier: List<String>" in LC and "earlier: List<String>" in LB)
check("v6.71 backup AI + Gemini get the FULL current chat (stored chat, budgeted), not 6 messages", "private fun chatWindow(maxChars: Int)" in NS and "FULL_GEMINI_CHARS" in NS and "FULL_FREE_CHARS" in NS and "fun fullWindow(" in FC)
check("the question being answered is not sent twice", 'msgs.last().role == "me"' in NS and "msgs.dropLast(1)" in NS)
check("window keeps newest, drops oldest, tells the model when it cut", "oldest messages are left out" in FC and "Window(part, part.size < joined.size)" in FC and "fallbackCutNote(en)" in NS)
check("window ends with the assistant and starts with the user", 'joined.last().role == "user"' in FC and 'part.first().role != "user"' in FC)
check("Gemini path uses the window and falls back to the short memory only when there is no stored chat", "chatWindow(FallbackContext.FULL_GEMINI_CHARS).turns" in NS and "no stored chat yet" in NS)
check("turns are bounded and alternate", "MAX_MSGS" in FC and "MAX_MSG_CHARS" in FC and "keep the newer of two in a row" in FC)
check("task line is redacted again and only for a fresh task", "AgentTaskRules.safeSummary(summary)" in FC and "TASK_FRESH_MS" in FC)
check("task line never calls an unverified task finished", '"REPORTED_COMPLETE" -> if (en) "finished but NOT verified"' in FC)
check("setup report command is answered locally", "ConfigReport.isQuery(text)" in NS and "ConfigReport.report(configState()" in NS)
check("config state is read from real keys + permissions", all(x in NS for x in ("SecureStore.hasKeys(this)", "NovaAccessibilityService.isEnabled(this)", "Settings.canDrawOverlays(this)", "RECORD_AUDIO")))
check("report says configured, never 'working'", "out of quota" in CR and "is working" not in CR.lower() and "all working" not in CR.lower() and "everything works" not in CR.lower())
check("agent says clearly when no AI key is configured", "ConfigReport.noAiForAgent(" in NS and "ConfigReport.agentReady(configState())" in NS)
check("no secrets in the new files", "getKeys" not in FC + CR and "apiKey" not in FC + CR)
check("no speaker recognition / voice lock added", not any(w in (FC + CR).lower() for w in ("speaker", "voicelock", "enrol")))
check("no catch (_ and no !! in new files", "catch (_:" not in FC + CR and "!!" not in FC + CR)
sys.exit(1 if bad else 0)
