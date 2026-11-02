import re,sys
A="app/src/main/java/com/nova/assistant/"
n=open(A+"NovaNotificationService.kt",encoding="utf-8").read()
s=open(A+"SmartReplyRules.kt",encoding="utf-8").read()
ok=True
for name,c in [("echo helper","fun isAssistantEcho" in s),
 ("listener uses echo+newest-own guard","newestIsOwn" in n and "isAssistantEcho(m.sender, m.text)" in n and "!newestIsOwn" in n),
 ("autoReply still called","autoReply(m, ex.getBoolean" in n),
 ("owner takeover kept","takeover.mark(m.sender" in n)]:
    print(("PASS " if c else "FAIL ")+name); ok&=c
sys.exit(0 if ok else 1)
