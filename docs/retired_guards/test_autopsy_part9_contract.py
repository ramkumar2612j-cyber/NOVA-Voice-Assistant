#!/usr/bin/env python3
"""AUTOPSY R6 (static): leftover items - WhatsApp read path, play_music honesty, dead-service scheduled job."""
import pathlib, sys
J = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/nova/assistant"
nl = (J / "NovaNotificationService.kt").read_text(encoding="utf-8")
sv = (J / "NovaService.kt").read_text(encoding="utf-8")
rc = (J / "ScheduledTaskReceiver.kt").read_text(encoding="utf-8")
bad = 0
def check(n, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
post = nl.split("override fun onNotificationPosted")[1].split("private fun handleLock")[0]
check("listener asks Android to rebind when dropped", "override fun onListenerDisconnected" in nl and "requestRebind" in nl)
check("each helper isolated (CallHub/lock/biz/gmail)", all(x in post for x in ('"CallHub: $e"', '"lock: $e"', '"biz: $e"', '"gmail: $e"')))
check("outer catch logs instead of silent", 'ErrLog.add("NotifListener", "posted: $e")' in post)
check("message reading still after the helpers", post.index('"gmail: $e"') < post.index("Driving.makeMsg"))
pm = sv.split('"play_music" -> {')[1].split('"camera" ->')[0]
check("play_music says it only opened the search", "ki search khol di" in pm and "Opened a YouTube search" in pm)
check("service exposes isAliveNow", "fun isAliveNow(): Boolean = alive" in sv)
check("receiver uses isAliveNow before running a job", "service.isAliveNow()" in rc and rc.index("isAliveNow") < rc.index("service.onScheduledTask"))
print("failed:", bad); sys.exit(1 if bad else 0)
