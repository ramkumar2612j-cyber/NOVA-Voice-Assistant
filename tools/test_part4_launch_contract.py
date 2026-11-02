#!/usr/bin/env python3
"""v7.17 Part 4 static guard: a real CALL is never re-sent by launch(); blocked starts are reported honestly."""
import os,sys
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..')
S=open(os.path.join(R,'app/src/main/java/com/nova/assistant/NovaService.kt'),encoding='utf-8').read()
bad=[]
def need(c,m):
    if not c: bad.append(m)
need('resend: Boolean = true' in S and 'resend && NovaAccessibilityService.startFromService' in S and 'resend && sendViaPendingIntent' in S,'launch() needs a resend switch')
need(S.count('ACTION_CALL, Uri.fromParts("tel", num, null)), null, tr("Call", "Call"), false)')==1,'callTool must not resend the call')
need(S.count('ACTION_CALL, Uri.fromParts("tel", number, null)), null, tr("Call", "Call"), false)')==1,'chosen-contact call must not resend the call')
need('val reason = r.msg.trim()' in S,'image failure reason')
if bad:
    [print('FAIL',b) for b in bad]; sys.exit(1)
print('PASS part4 launch guards')
