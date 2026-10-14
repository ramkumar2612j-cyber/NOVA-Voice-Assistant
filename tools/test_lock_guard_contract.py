#!/usr/bin/env python3
"""v6.98 lock guard wiring contract (static): every piece must be connected, no manual test buttons."""
import re, sys, pathlib
R = pathlib.Path(__file__).resolve().parent.parent / "app/src/main"
J = R / "java/com/nova/assistant"
rd = lambda p: (R / p if "/" in p else J / p).read_text(encoding="utf-8")
svc, ns, acc, ma = rd("NovaService.kt"), rd("NovaNotificationService.kt"), rd("NovaAccessibilityService.kt"), rd("MainActivity.kt")
cfg, ui, xml, st = rd("ExtCfg.kt"), rd("assets/ui.html"), rd("res/xml/accessibility_config.xml"), rd("SelfTest.kt")
la = rd("LockAlert.kt")
bad = 0
def check(n, ok):
    global bad
    print(("PASS " if ok else "FAIL ") + n)
    bad += 0 if ok else 1
check("listener calls handleLock before other handlers", ns.index("handleLock(sbn)") < ns.index("handleBiz(sbn)"))
check("listener uses LockGuard.classify + LockAlert.start", "LockGuard.classify(" in ns and "LockAlert.start(" in ns)
check("voice loop is gated while locked", re.search(r"if \(lockQuiet\(\)\) \{.*?continue", svc, re.S) is not None)
check("announcements held while locked", "fun announceIfIdle(text: String): Boolean {\n        if (lockQuiet()) return false" in svc)
check("driving mode exempt", "Driving.enabled" in svc.split("private fun lockQuiet")[1][:400])
check("volume key stops alert", "override fun onKeyEvent" in acc and "LockAlert.stop()" in acc)
check("key filter flags in accessibility xml", "flagRequestFilterKeyEvents" in xml and 'canRequestFilterKeyEvents="true"' in xml)
check("alert uses ALARM stream", la.count("STREAM_ALARM") >= 3)
check("switch exists (default ON)", "fun lockGuard" in cfg and 'getBoolean(LOCK_GUARD, true)' in cfg)
check("bridge + UI switch", "fun getLockGuard" in ma and "fun setLockGuard" in ma and 'id="wb_lg"' in ui and "Android.setLockGuard" in ui)
check("auto test section present", "6f. v6.98 LOCK GUARD" in st)
check("no manual lock test button", 'id="lg_test"' not in ui)
check("alert never reads message text", "ex.getCharSequence(Notification.EXTRA_TEXT)" in ns and "LockGuard.text(a, silent, en)" in ns)
man = (R / "AndroidManifest.xml").read_text(encoding="utf-8")
check("watch: VOICE_COMMAND activity registered", "android.intent.action.VOICE_COMMAND" in man and ".WatchCommandActivity" in man)
check("watch: bluetooth permissions", "BLUETOOTH_CONNECT" in man and "MODIFY_AUDIO_SETTINGS" in man)
check("watch: wakeFromWatch uses WatchMic and the lock gate", "fun wakeFromWatch" in svc and "WatchMic.start(" in svc and "lockQuiet()" in svc.split("fun wakeFromWatch")[1][:200])
print("failed:", bad)
sys.exit(1 if bad else 0)
