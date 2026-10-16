#!/usr/bin/env python3
"""v6.99 Part 5 money talk auto reply: static contract. Run from the project root."""
import os,sys
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..')
J=os.path.join(R,'app/src/main/java/com/nova/assistant/')
rd=lambda p:open(p,encoding='utf-8').read()
N,P=rd(J+'NovaNotificationService.kt'),rd(J+'PaymentReplyRules.kt')
bad=[]
def need(c,m):
    if not c: bad.append(m)
need('PaymentReplyRules.decide(m.text)' in N,'notification service does not call PaymentReplyRules')
need('Kind.SECRET' in N and 'secretNote()' in N,'code / OTP messages must be skipped with a note')
need('pay?.reply ?: soc?.reply' in N,'money talk must use its own fixed line')
need('pay == null && SmartReplyRules.useAi' in N,'money talk must never go to the AI')
need('PaymentReplyRules.note(pay.kind)' in N,'history note missing')
import re
for name in ('REPLY_ASK','REPLY_PAID','REPLY_DETAILS'):
    m=re.search(name+r' = "([^"]*)"',P); need(bool(m) and not re.search(r'\d',m.group(1)) and 'haan' not in m.group(1).lower(),name+' must be safe text')
need(os.path.exists(os.path.join(R,'app/src/test/java/com/nova/assistant/PaymentReplyRulesTest.kt')),'test file missing')
if bad:
    for b in bad: print('FAIL',b)
    sys.exit(1)
print('PASS payment reply contract: no ask, no AI, no amount, codes skipped')
