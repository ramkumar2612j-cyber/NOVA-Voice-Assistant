#!/usr/bin/env python3
"""v7.17 Part 3 static guards: listener rebind, learn duplicate honesty."""
import os,sys
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..')
J=os.path.join(R,'app/src/main/java/com/nova/assistant/')
rd=lambda f:open(J+f,encoding='utf-8').read()
N,S=rd('NovaNotificationService.kt'),rd('NovaService.kt')
bad=[]
def need(c,m):
    if not c: bad.append(m)
need('override fun onListenerDisconnected' in N and 'requestRebind(' in N,'listener must ask Android to rebind when dropped')
need('val keptId = LearnStore.addKept(this, topic, note, src)' in S and 'keptId == null' in S,'learn must check the addKept result')
need('pehle se note hai' in S,'learn must say honestly when a note already exists')
C=rd('ConnectorApi.kt')
need('n8nWorkflowId(base, h, wf)' in C and 'workflow nahi mila' in C,'n8n executions must resolve a name to an id')
if bad:
    [print('FAIL',b) for b in bad]; sys.exit(1)
print('PASS part3 guards')
