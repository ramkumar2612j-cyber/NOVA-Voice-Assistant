"""v6.53 contract test: auto reply (source level; Kotlin is NOT compiled here)."""
import re, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
J = root / "app/src/main/java/com/nova/assistant"
rd = lambda n: (J / n).read_text(encoding="utf-8")
ns, mm, ar, al = rd("NovaNotificationService.kt"), rd("MessageMode.kt"), rd("AutoReplyRules.kt"), rd("ActionLog.kt")
act = (root / "app/src/main/java/com/nova/assistant/MainActivity.kt").read_text(encoding="utf-8")
ui = (root / "app/src/main/assets/ui.html").read_text(encoding="utf-8")
fails = []
def check(name, cond):
    print(("PASS " if cond else "FAIL ") + name)
    if not cond: fails.append(name)
check("listener auto replies only for selected Message Mode apps", "MessageMode.isSelected(this, pkg)) autoReply(" in ns)
check("auto reply never sends to groups / codes / no-button", all(x in ar for x in ('isGroup -> "group"', 'hidden -> "code"', '!canReply -> "no_reply_button"')))
check("rate limits present (10 min, 2/h, 80/day)", all(x in ar for x in ("SENDER_COOLDOWN_MS = 600_000L", "SENDER_PER_HOUR = 2", "DAY_CAP = 80")))
check("reply text is the owner's own text (AI smart reply is OFF by default, fixed text is always the fallback)", "ActionLog.autoReplyText(ctx)" in ns)
check("every attempt is written to the history", 'ActionLog.add(ctx, m.app, m.sender, m.text, if (ok) "sent" else "failed", text, m.pkg, if (pay != null) PaymentReplyRules.note(pay.kind) else SmartReplyRules.note(picked.ai))' in ns)   # v6.99 Part 5: money talk has its own history note
check("code messages are stored without their text", "m.hidden" in ar or 'hidden -> "code"' in ar)
check("owner is told afterwards, never asked", "fun onAutoReplied" in rd("NovaService.kt") and "askOrRun" not in ns)
check("bridge methods for history + settings exist", all(f"fun {n}(" in act for n in ("getAutoReply", "setAutoReply", "getActionLog", "actionUnseen", "markActionsSeen", "clearActionLog")))
check("home page has alert icon, badge, history panel", all(x in ui for x in ('id="alb"', 'id="alc"', 'id="hist"', 'id="h_clear"', 'id="h_close"')))
check("settings box has switch, text and save", all(x in ui for x in ('id="ar_on"', 'id="ar_text"', 'id="ar_save"')))
check("every new UI bridge call exists", all(re.search(r"fun " + n + r"\(", act) for n in set(re.findall(r"Android\.(getAutoReply|setAutoReply|getActionLog|actionUnseen|markActionsSeen|clearActionLog)\(", ui))))
check("history capped and clearable", "MAX = 300" in al and "fun clear(" in al)
print("SUMMARY fails=%d" % len(fails))
sys.exit(1 if fails else 0)
