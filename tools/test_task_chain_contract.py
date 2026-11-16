#!/usr/bin/env python3
"""v7.199 CHAIN contract: one spoken job that needs several agents in order (website -> publish -> WhatsApp).
Static wiring checks + a Python mirror of the Kotlin step regex. Kotlin compile / JUnit are NOT run here (GitHub does that)."""
import re, sys
A = "app/src/main/java/com/nova/assistant/"
rules = open(A + "TaskManagerRules.kt", encoding="utf-8").read()
store = open(A + "TaskManagerStore.kt", encoding="utf-8").read()
svc = open(A + "JarvisService.kt", encoding="utf-8").read()
test = open("app/src/test/java/com/nova/assistant/TaskManagerRulesTest.kt", encoding="utf-8").read()
fails = []
def ck(c, l):
    print(("PASS " if c else "FAIL ") + l)
    if not c: fails.append(l)

for fn in ("fun parseSteps(", "fun stepFailed(", "fun encodeSteps(", "fun decodeSteps(", "fun isContinue(", "fun chainLine(", "fun isBackground("):
    ck(fn in rules, "rules has " + fn)
ck('"steps"' in rules and "TWO OR MORE" in rules, "AI prompt explains steps")
ck("fun savePending(" in store and "fun pending(" in store, "store keeps paused steps")
ck("private fun runChain(" in svc and "private fun runPlanText(" in svc, "service has runChain + runPlanText")
ck(svc.count("TaskManagerRules.parseSteps(") == 2, "both brain hooks read steps")
ck(svc.count("runChain(") >= 5, "runChain used at both brain hooks and both continue hooks (+ definition)")
ck(svc.count("TaskManagerRules.isContinue(") == 2 and svc.count("TaskManagerStore.pending(") == 2, "continue phrase only acts when steps are pending")
i = svc.index("private fun runChain(")
body = svc[i:i + 2500]
ck("stepFailed(" in body and "isBackground(" in body and "savePending(" in body and "isDenied(" in body, "chain stops on failure, pauses after long tool, re-checks HARD DENY")
ck(svc.index("parseSteps(uraw") < svc.index("runRewritten(up,"), "chain is tried before the single plan (site 1)")
ck(svc.index("parseSteps(jraw") < svc.index("runRewritten(plan,"), "chain is tried before the single plan (site 2)")
# HARD DENY words in a step
ck("isDenied(say)" in rules[rules.index("fun parseSteps("):], "parseSteps refuses payment/OTP/password words")
for t in ("chainIsParsedInOrderAndPrefixed", "chainNeedsTwoValidStepsAndKnownTools", "chainDeniesPaymentAnywhere", "chainTooLongIsRefused", "pendingStepsRoundTripAndKeepDenyRule", "longToolsPauseTheChainAndShortOnesDoNot", "chainLineIsHonest"):
    ck("fun " + t in test, "unit test " + t)
# Python mirror of the Kotlin regex
STEP = re.compile(r'\{\s*"agent"\s*:\s*"\s*([A-Za-z_]+)\s*"\s*,\s*"say"\s*:\s*"((?:[^"\\]|\\.)*)"\s*\}')
raw = '{"agent":"none","steps":[{"agent":"site","say":"jarvis a"},{"agent":"wa","say":"x \\"q\\" y"}]}'
m = STEP.findall(raw[raw.index('"steps"'):])
ck([x[0] for x in m] == ["site", "wa"] and m[1][1] == 'x \\"q\\" y', "step regex reads ordered steps and escaped quotes")
ids = re.findall(r'Entry\("([a-z]+)"', rules)
bg = re.search(r'BACKGROUND = setOf\(([^)]*)\)', rules).group(1)
ck(all(b in ids for b in re.findall(r'"([a-z]+)"', bg)), "every background id exists in the registry")
sys.exit(1 if fails else 0)
