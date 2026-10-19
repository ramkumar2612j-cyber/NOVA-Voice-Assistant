#!/usr/bin/env python3
"""v7.45: the SURVIVING intents of the old autopsy guards (part2/6/7/8/9), rewritten against the real code of THIS tree. Static only."""
import pathlib, sys
R = pathlib.Path(__file__).resolve().parent.parent
J = R / "app/src/main/java/com/nova/assistant"
rd = lambda f: (J / f).read_text(encoding="utf-8")
svc, nl, st, rc, bt, um, rs, ov = (rd(x) for x in ("NovaService.kt", "NovaNotificationService.kt", "ScheduledTaskStore.kt", "ScheduledTaskReceiver.kt", "BootReceiver.kt", "UpdateManager.kt", "RecordSession.kt", "OverlayNudgeRules.kt"))
bad = 0
def check(n, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
# overlay nudge repeats every 10 min
check("overlay nudge repeats after 10 min", "10 * 60_000L" in ov and "OverlayNudgeRules.due(overlayNudgedAt" in svc)
# downloads verified
check("APK/model download hash verified", "item.sha256" in um and "part.delete()" in um)
# listener isolation
post = nl.split("override fun onNotificationPosted")[1].split("private fun handleLock")[0]
check("listener asks Android to rebind", "override fun onListenerDisconnected" in nl and "requestRebind" in nl)
check("each helper isolated", all(x in post for x in ('"lock: $e"', '"biz: $e"', '"gmail: $e"')))
check("outer catch logs", 'ErrLog.add("NotifListener", "posted: $e")' in post)
check("message reading after helpers", post.index('"gmail: $e"') < post.index("Driving.makeMsg"))
# scheduled tasks
check("alarm tri-state (exact / inexact / failed)", all(x in st for x in ('"exact"', '"inexact"', '"failed"', "fun scheduleAlarmState")))
check("never says saved when Android refused every alarm", '"alarm_failed"' in st and '"alarm_failed" ->' in svc)
check("receiver uses isAliveNow before running a job", "fun isAliveNow(): Boolean = alive" in svc and rc.index("isAliveNow") < rc.index("onScheduledTask"))
check("receiver has notifyMissed", "fun notifyMissed" in rc)
man = (R / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
check("BootReceiver re-arms after update / time change", "ScheduleRestoreRules.bootPlan" in bt and all(x in man for x in ("MY_PACKAGE_REPLACED", "TIME_SET", "TIMEZONE_CHANGED")))
# recording writer error reaches the owner
check("dead audio writer is reported", "writerError" in rs and "s.writerError.isNotBlank()" in svc)
# v7.45 wiring
print("failed:", bad); sys.exit(1 if bad else 0)
