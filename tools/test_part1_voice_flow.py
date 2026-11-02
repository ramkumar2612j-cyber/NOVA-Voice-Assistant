"""Part 1 contract test (source level; Kotlin is NOT compiled here). Also simulates the capture rule in Python."""
import re, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
svc = (root / "app/src/main/java/com/nova/assistant/NovaService.kt").read_text(encoding="utf-8")
rules = (root / "app/src/main/java/com/nova/assistant/VoiceFlowRules.kt").read_text(encoding="utf-8")
logic = (root / "app/src/main/java/com/nova/assistant/Logic.kt").read_text(encoding="utf-8")
fails = []
def check(name, cond):
    print(("PASS " if cond else "FAIL ") + name)
    if not cond: fails.append(name)

check("END_SILENCE_MS == 1300 (v7.75)", re.search(r"END_SILENCE_MS\s*=\s*1300", logic) is not None)
check("MAX_COMMAND_MS == 60000", re.search(r"MAX_COMMAND_MS\s*=\s*60000", logic) is not None)
check("command capture ends through CaptureRules.shouldEnd", "CaptureRules.shouldEnd(" in svc)
cap = svc[svc.index("val maxMs ="):svc.index("val maxMs =") + 900]
check("normal command uses END_SILENCE_MS", "adaptiveSilence(cmdText.toString(), Logic.END_SILENCE_MS)" in cap)
seg = svc[svc.index("val speaking = rms > maxOf(450.0"):svc.index("if (end) {")]
check("no Thread.sleep in the capture / silence-timer section", "Thread.sleep" not in seg and "delay(" not in seg)
check("no voice-enroll code left (v6.51)", "startVoiceEnroll" not in svc and "enrollGuard" not in svc)
check("battery warning goes through AlertGate", "AlertGate.maySpeak(" in svc)
check("battery flush callbacks removed on destroy", "h.removeCallbacks(batteryFlush)" in svc)
check("battery warning not spoken directly in receiver", svc.count("Sir, battery kam hai") == 1)
check("single batteryLastAlertAt declaration", len(re.findall(r"var batteryLastAlertAt\b", svc)) == 1)
check("no stray lastAlertAt", re.search(r"(?<!battery)\blastAlertAt\b", svc) is None)
for key in ['"lead_clear" -> askFirst(', '"queue_clear" -> askFirst(', '"schedule_cancel_all" -> askFirst(']:
    i = svc.index(key); seg = svc[i:i + 400]
    check("destructive stays confirmed: " + key.split('"')[1], "), true) {" in seg)
check("forget-all stays confirmed", "forgetAllPrompt(en), true)" in svc)
check("publish stays confirmed", "true   // publishing is never skipped" in svc)
check("no listener registered twice for battery", len(re.findall(r"registerSysReceiver\(batteryLowRx", svc)) == 1)

# Python mirror of CaptureRules
def run(p, chunk=100, silence=1300, mx=60000, nospeech=8000):
    heard = False; s = 0; t = 0
    for c in p:
        t += chunk; sp = c == "s"
        if sp: heard = True
        s = 0 if sp else (s + chunk if heard else s)
        if (heard and s >= silence) or t >= mx or (not heard and t >= nospeech): return t
    return -1
check("sim: continuous speech never ends", run("s" * 300) == -1)
check("sim: 1.2 s pause stays one command", run("s" * 10 + "." * 12 + "s" * 10) == -1)
check("sim: resume resets timer", run("s" * 10 + "." * 11 + "s" + "." * 13) == (10 + 11 + 1 + 13) * 100)
check("sim: exactly 1.3 s ends", run("s" * 10 + "." * 13) == 2300)
check("sim: 60 s cap", run("s" * 700) == 60000)
print("\nfailed:", len(fails))
sys.exit(1 if fails else 0)
