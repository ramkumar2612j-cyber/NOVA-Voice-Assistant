#!/usr/bin/env python3
"""Part 6 contract (v6.76). NOT a Kotlin run. Checks, on the source text:
 1. every tool name dispatched by NovaService.runLocalTool / runTool has a verification level in ToolVerify.kt;
 2. the hooks (one bounded retry, finish()) are present, and call / WhatsApp keep their mandatory spoken yes;
 3. ToolVerifyTest.classify expectations agree with a Python port of ToolVerify.classify (catches a wrong expectation before CI does);
 4. the AI-app send rule uses isAiSend. Plain script, exits non-zero on failure."""
import os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "app", "src")
def read(*p): return open(os.path.join(SRC, *p), encoding="utf8").read()
M = ("main", "java", "com", "nova", "assistant"); T = ("test", "java", "com", "nova", "assistant")
NS = read(*M, "NovaService.kt"); TV = read(*M, "ToolVerify.kt"); AR = read(*M, "AgentRules.kt")
TVT = read(*T, "ToolVerifyTest.kt"); ART = read(*T, "AgentRulesPart6Test.kt")
fails = []
def check(ok, msg):
    if not ok: fails.append(msg)

def block(src, start_pat):
    i = src.index(start_pat); j = src.index("\n    }\n", i); return src[i:j]

levels = set(re.findall(r'"([a-z_]+)" to Level\.', TV))
local = block(NS, "private fun runLocalTool(")
cloud = block(NS, "private fun runTool(")
local_names = set(re.findall(r'^\s{16}"([a-z_]+)" ->', local, re.M)) - {"else"}
cloud_names = set(re.findall(r'^\s{16}"([a-z_]+)" ->', cloud, re.M)) - {"else"}
check(len(local_names) >= 15, "runLocalTool: parsed only %d tool names" % len(local_names))
check(len(cloud_names) >= 15, "runTool: parsed only %d tool names" % len(cloud_names))
for n in sorted(local_names | cloud_names):
    check(n in levels or n == "media", "tool %r has no level in ToolVerify.LEVELS" % n)
check("media" in (local_names | cloud_names), "media tool not found (parser broke?)")

check("fun exec(): R" in local and "ToolVerify.Step.RETRY_ONCE" in local and "r = exec()" in local, "runLocalTool: bounded retry hook missing")
check(local.count("= exec()") == 2, "runLocalTool: exec() must run at most twice (1 try + 1 retry)")
check("ToolVerify.finish(c.kind" in local, "runLocalTool: finish() hook missing")
check("ToolVerify.finish(name" in NS, "runTool: finish() hook missing")
check(len(re.findall(r"askFirst\([^\n]*must = true", NS)) >= 2, "call / WhatsApp must keep askFirst(..., must = true)")
check("must = true" in block(NS, "private fun whatsappTool("), "whatsappTool must still go through askFirst")
EXT = read(*M, "ExtCfg.kt")
check('getBoolean("skip_risky_confirm", true)' in EXT and 'getBoolean("skip_order_confirm", true)' in EXT, "default must stay: no yes/no (skip_* = true)")
check("if (ExtCfg.skipRiskyConfirm(this)) return run()" in NS, "askFirst must run at once when skip_risky_confirm is ON")
LAUNCH = block(NS, "private fun launch(")
check("startFromService" in LAUNCH and "sendViaPendingIntent" in LAUNCH and "notifyOpen" not in LAUNCH, "launch(): 3 silent ways and no notification expected")
check("MAX_STEPS = 60" in AR and "MAX_MS = 600000L" in AR, "agent caps changed unexpectedly")
check("isAiSend(label(node))" in AR, "AgentRules.check must use isAiSend for the AI-app send")
check("pkg in AI_APPS && risky.all { it in SEND_OK }" in AR and "&& isAiSend" in AR, "AI-app send rule changed unexpectedly")

# 3. Python port of ToolVerify.classify, compared with the expectations written in ToolVerifyTest
def classify(msg):
    m = msg.lower(); has = lambda *s: any(x in m for x in s)
    if has("accessibility"): return "NO_ACCESSIBILITY"
    if has("do not control this screen", "control nahi karta", "background se app kholne", "opening apps from the background"): return "BLOCKED"
    if has("no app can", "no app could", "koi app ye kaam", "wala app nahi mila"): return "NO_HANDLER"
    if has("permission"): return "NO_PERMISSION"
    if has("confirm nahi", "not confirmed", "could not confirm", "did not change", "nahi badla", "badli nahi"): return "NOT_CONFIRMED"
    if has("nahi mila", "could not find", "not found"): return "NOT_FOUND"
    if has("galat", "not valid", "samajh nahi", "did not understand", "khali", "is empty", "between", "beech"): return "BAD_INPUT"
    return "OTHER"
src_order = re.search(r"fun classify\(msg: String\): Fail \{(.*?)\n    \}\n", TV, re.S).group(1)
kt_checks = re.findall(r'm\.contains\("([^"]+)"\)', src_order)
py_checks = ["accessibility","do not control this screen","control nahi karta","background se app kholne","opening apps from the background","no app can","no app could","koi app ye kaam","wala app nahi mila","permission","confirm nahi","not confirmed","could not confirm","did not change","nahi badla","badli nahi","nahi mila","could not find","not found","galat","not valid","samajh nahi","did not understand","khali","is empty","between","beech"]
check(kt_checks == py_checks, "ToolVerify.classify word list or order differs from the Python port: %r" % (set(kt_checks) ^ set(py_checks) or "order"))
exp = re.findall(r'assertEquals\(ToolVerify\.Fail\.([A-Z_]+), ToolVerify\.classify\("((?:[^"\\]|\\.)*)"\)\)', TVT)
check(len(exp) >= 12, "ToolVerifyTest: only %d classify expectations parsed" % len(exp))
for kind, text in exp:
    got = classify(text.replace('\\"', '"'))
    check(got == kind, "classify(%r): test expects %s, port gives %s" % (text, kind, got))

check(len(re.findall(r"@Test", TVT)) >= 7, "ToolVerifyTest needs >= 7 tests")
check(len(re.findall(r"@Test", ART)) >= 6, "AgentRulesPart6Test needs >= 6 tests")

if fails:
    print("FAIL tool_verify contract"); [print(" -", f) for f in fails]; sys.exit(1)
print("OK: %d tools have a level; classify port agrees with %d test expectations" % (len(local_names | cloud_names), len(exp)))
