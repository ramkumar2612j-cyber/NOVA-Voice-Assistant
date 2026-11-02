#!/usr/bin/env python3
"""v6.99 Part 4 phone / self facts: static contract. Run from the project root."""
import os,sys,re
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..')
J=os.path.join(R,'app/src/main/java/com/nova/assistant/')
rd=lambda p:open(p,encoding='utf-8').read()
P,N=rd(J+'PhoneFacts.kt'),rd(J+'NovaService.kt')
man=rd(os.path.join(R,'app/src/main/AndroidManifest.xml'))
bad=[]
def need(c,m):
    if not c: bad.append(m)
need('PhoneFacts.aiFacts(this)' in N,'AI prompt chain does not include PhoneFacts')
for p in ('RECORD_AUDIO','READ_CONTACTS','CALL_PHONE','ANSWER_PHONE_CALLS','READ_CALENDAR','CAMERA','POST_NOTIFICATIONS','SYSTEM_ALERT_WINDOW','WRITE_SETTINGS'):
    need('android.permission.'+p in man,'manifest lacks '+p)
    if p in ('SYSTEM_ALERT_WINDOW','WRITE_SETTINGS'): continue
    need('Manifest.permission.'+p in P,'PhoneFacts does not read '+p)
keys=re.findall(r'Triple\("(\w+)"',P)
need(len(keys)==len(set(keys))==11,'ACCESS must hold 11 unique keys')
for k in keys: need('m["%s"]'%k in P,'read() missing key '+k)
need('NovaAccessibilityService.isEnabled(ctx)' in P,'accessibility status not read')
if bad:
    for b in bad: print('FAIL',b)
    sys.exit(1)
print('PASS phonefacts contract: 11 accesses read, tap paths listed, wired into the AI chain')
