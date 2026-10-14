"""v6.53 contract test: call permission, delete, agent screenshot/zoom (source level; Kotlin is NOT compiled here)."""
import re, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
M = root / "app/src/main"
J = M / "java/com/nova/assistant"
rd = lambda n: (J / n).read_text(encoding="utf-8")
man = (M / "AndroidManifest.xml").read_text(encoding="utf-8")
svc, ar, acc, md, mm, act = rd("NovaService.kt"), rd("AgentRules.kt"), rd("NovaAccessibilityService.kt"), rd("MessageDelete.kt"), rd("MessageMode.kt"), rd("MainActivity.kt")
ui = (M / "assets/ui.html").read_text(encoding="utf-8")
fails = []
def check(name, cond):
    print(("PASS " if cond else "FAIL ") + name)
    if not cond: fails.append(name)
check("manifest has CALL_PHONE and QUERY_ALL_PACKAGES (owner request v6.53)", 'android.permission.CALL_PHONE' in man and 'android.permission.QUERY_ALL_PACKAGES' in man)
check("call: ACTION_CALL only when CALL_PHONE is granted, dialer as fallback", "Intent.ACTION_CALL" in svc and "checkSelfPermission(Manifest.permission.CALL_PHONE)" in svc and "Intent.ACTION_DIAL" in svc)
check("call permission is requested through the app (REQ_CALL)", 'what == "call"' in act and "REQ_CALL" in act)
check("agent knows screenshot / zoom_in / zoom_out", all(x in ar for x in ('"screenshot"', '"zoom_in"', '"zoom_out"')) and all(x in svc for x in ('"screenshot" ->', '"zoom_in", "zoom_out" ->')))
check("screenshot / zoom stay blocked on protected screens", ar.index('if (Control.blocked(pkg, ownPkg, "tap"))') < ar.index('a.act == "screenshot"'))
check("accessibility has screenshotToGallery and pinch", "fun screenshotToGallery()" in acc and "fun pinch(" in acc and "GLOBAL_ACTION_TAKE_SCREENSHOT" in acc)
check("delete only inside a conversation and only the exact reply text", "inConversation(s.second)" in md and "findBubble(s.second, text)" in md)
check("delete never picks 'delete for me'", '"delete for me"' not in md)
check("delete result is verified on screen and written to history", '"deleted" else "unverified"' in md and "ActionLog.setStatus" in md)
check("voice command message_delete is wired", '"message_delete"' in mm and '"message_delete" -> messageDelete()' in svc)
check("history has a DELETE button wired to the bridge", "hdel" in ui and "Android.deleteSent" in ui and "fun deleteSent(" in act)
print("SUMMARY fails=%d" % len(fails))
sys.exit(1 if fails else 0)
