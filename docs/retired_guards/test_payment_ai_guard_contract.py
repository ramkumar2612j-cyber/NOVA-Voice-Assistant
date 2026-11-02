#!/usr/bin/env python3
"""v7.15 Part 2: money talk / OTP must never reach the AI (static). Run from project root."""
import os,sys,re
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..')
N=open(os.path.join(R,'app/src/main/java/com/nova/assistant/NovaNotificationService.kt'),encoding='utf-8').read()
m=re.search(r'private fun smartAutoReply\(.*?\n    }\n',N,re.S)
body=m.group(0) if m else ''
bad=[]
def need(c,t):
    if not c: bad.append(t)
need(bool(body),'smartAutoReply not found')
i,j=body.find('PaymentReplyRules.decide(m.text)'),body.find('smartDraft(')
need(i>=0,'smartAutoReply does not call PaymentReplyRules.decide')
need(i>=0 and j>=0 and i<j,'payment check must come BEFORE the AI call (smartDraft)')
need('Kind.SECRET' in body and 'secretNote()' in body,'codes / OTP must be skipped with a note')
need('PaymentReplyRules.note(pay.kind)' in body,'owner note missing')
if bad:
    [print('FAIL',b) for b in bad]; sys.exit(1)
print('PASS payment AI guard: money talk and codes never reach smartDraft')
