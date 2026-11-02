#!/usr/bin/env python3
"""v6.99 Part 2 spoken STOP: static contract. Run from the project root."""
import os,sys
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..')
def rd(p): return open(os.path.join(R,p),encoding='utf-8').read()
J='app/src/main/java/com/nova/assistant/'
N,L,S=rd(J+'NovaService.kt'),rd(J+'Logic.kt'),rd(J+'StopRules.kt')
bad=[]
def need(c,m):
    if not c: bad.append(m)
need('object StopRules' in S and 'fun isStop' in S and 'fun wasStopped' in S,'StopRules api missing')
need('StopRules.isStop(core)' in L,'Logic.classify does not use StopRules')
need('stopEpoch++' in N,'runCommand stop does not bump stopEpoch')
need(N.count('StopRules.wasStopped(epoch, stopEpoch)')==3,'image, website and learn jobs must all check stopEpoch')
need(N.count('val epoch = stopEpoch')==3,'image, website and learn jobs must all capture stopEpoch')
need(os.path.exists(os.path.join(R,'app/src/test/java/com/nova/assistant/StopRulesTest.kt')),'StopRulesTest.kt missing')
if bad:
    for b in bad: print('FAIL',b)
    sys.exit(1)
print('PASS stop contract: StopRules + Logic + image/site jobs honour a spoken stop')
