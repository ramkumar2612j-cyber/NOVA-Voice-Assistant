#!/usr/bin/env python3
"""v7.46 owner rule: auto-reply NEVER sends a fixed / canned text. Every reply is the AI's own answer (OTP / payment talk too); no AI answer = nothing sent, owner is told. Static."""
import pathlib, re, sys
R = pathlib.Path(__file__).resolve().parent.parent
J = R / "app/src/main/java/com/nova/assistant"
nl = (J / "NovaNotificationService.kt").read_text(encoding="utf-8")
sv = (J / "NovaService.kt").read_text(encoding="utf-8")
al = (J / "ActionLog.kt").read_text(encoding="utf-8")
ui = (R / "app/src/main/assets/ui.html").read_text(encoding="utf-8")
bad = []
def need(c, m):
    if not c: bad.append(m)
need("fun smartReplyOn(ctx: Context): Boolean = true" in al, "auto reply must always be the AI")
need("val smart = true" in nl, "autoReply must always take the AI path")
body = re.search(r"private fun smartAutoReply\(.*?\n    }\n", nl, re.S).group(0)
need("autoReplyText" not in nl, "NovaNotificationService must never send the saved fixed text")
need("PaymentReplyRules" not in nl + sv and "SocialReplyRules" not in nl + sv, "canned-line rule files must not be wired")
need("HOLD_LINE" not in sv, "no fixed hold line may be sent")
need("if (text.isEmpty())" in body and body.index("if (text.isEmpty())") < body.index("DrivingBridge.sendReply"), "empty AI answer must send nothing")
need("GmailStore.notifyPhone" in body and "SmartReplyRules.inform" in body, "owner must be told every time")
need('id="ar_text"' not in ui and 'id="ar_smart"' not in ui, "fixed-text box / old AI switch must be gone from the UI")
need('id="ar_on"' in ui and 'id="ar_save"' in ui and 'id="ar_safe"' in ui, "auto reply switch + rule card must exist")
if bad:
    [print("FAIL", b) for b in bad]; sys.exit(1)
print("PASS ai-only reply contract")
