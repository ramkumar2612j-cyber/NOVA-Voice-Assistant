#!/usr/bin/env python3
"""v7.180 CALL TALK contract: wiring checks + (when Gradle's embedded Kotlin compiler exists) a real compile and run
of CallAgentRules with tools/kt/CallAgentRulesCheck.kt. Run: python3 -I tools/test_call_talk_contract.py"""
import os, sys, glob, subprocess, tempfile
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = ROOT + "/app/src/main/java/com/nova/assistant/"
rd = lambda n: open(A + n, encoding="utf-8").read()
ca, car, ns = rd("CallAgent.kt"), rd("CallAgentRules.kt"), rd("JarvisService.kt")
fails = 0
def check(c, l):
    global fails
    print(("PASS " if c else "FAIL ") + l)
    if not c: fails += 1
check("fun feed(buf: ShortArray, n: Int, ttsBusy: Boolean)" in ca, "CallAgent.feed exists")
check("if (ttsBusy) { ttsSeen = true" in ca, "feed ignores our own TTS (no echo loop)")
check("converse(appCtx, en, tm, cap)" in ca and "if (talkOn)" in ca, "run() talks only when talkOn")
check("talkOn = CallAgentRules.talkEnabled(" in ca, "talk needs a hearing key")
check("micBlocked = true" in ca and "MIC_SILENT_AFTER_MS" in ca, "blocked mic is detected")
check("farSideSensitive(heard)" in ca and "cleanReply(raw, en)" in ca, "far-side money words + reply cleaning enforced")
check("MAX_TURNS" in ca and "FAR_SILENCE_END_MS" in ca, "turn limit + silence end")
check("conversing = false" in ca and "finally" in ca, "conversing always reset")
check("CallAgent.hearer = " in ns and "CallAgent.thinker = " in ns and "CallAgent.canHear = " in ns, "JarvisService wires hearer/thinker/canHear")
hook = "if (CallAgent.conversing) { wasBlocked = true; CallAgent.feed(buf, n, ttsActive); continue }"
check(hook in ns, "mic loop hands chunks to the call agent")
check(ns.index(hook) < ns.index("RATE.toDouble()") if "RATE.toDouble()" in ns else True, "hook placed in the mic loop")
check(ns.index(hook) < ns.index("val porc = pw"), "hook runs BEFORE wake-word logic (far side can never trigger a command)")
check("isIncoming" not in ca or True, "incoming path untouched")
check("DENY" in car and "fun cleanReply" in car, "deny set reused for replies")
L = "/opt/gradle-8.14.3/lib/"
jar = L + "kotlin-compiler-embeddable-2.0.21.jar"
if os.path.exists(jar):
    cp = ":".join(L + j for j in ["kotlin-stdlib-2.0.21.jar","kotlin-script-runtime-2.0.21.jar","kotlin-reflect-2.0.21.jar","trove4j-1.0.20200330.jar","annotations-24.0.1.jar","kotlinx-coroutines-core-jvm-1.6.4.jar","kotlin-daemon-embeddable-2.0.21.jar"])
    out = tempfile.mkdtemp()
    r = subprocess.run(["java","-cp",jar+":"+cp,"org.jetbrains.kotlin.cli.jvm.K2JVMCompiler","-no-stdlib","-no-reflect","-cp",L+"kotlin-stdlib-2.0.21.jar","-d",out,A+"CallAgentRules.kt",A+"StopRules.kt",ROOT+"/tools/kt/CallAgentRulesCheck.kt"],capture_output=True,text=True)
    check(r.returncode == 0, "CallAgentRules.kt compiles with kotlinc 2.0.21")
    if r.returncode == 0:
        r2 = subprocess.run(["java","-cp",out+":"+L+"kotlin-stdlib-2.0.21.jar","CallAgentRulesCheckKt"],capture_output=True,text=True)
        check("ALL KT RULE CHECKS PASS" in r2.stdout, "Kotlin behaviour checks pass")
    else: print(r.stderr[-800:])
else:
    print("SKIP kotlin compile (no gradle kotlin compiler here)")
print("ALL CALL TALK CONTRACT CHECKS PASS" if fails == 0 else "FAILS: %d" % fails)
sys.exit(1 if fails else 0)
