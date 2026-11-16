#!/usr/bin/env python3
"""v7.183 static contract: JARVIS self knowledge is wired, honest (NOT TESTED), and never promises to break security."""
import sys,re
R='app/src/main/java/com/nova/assistant/'
k=open(R+'SelfKnowledge.kt',encoding='utf-8').read(); ns=open(R+'JarvisService.kt',encoding='utf-8').read()
ut=open('app/src/test/java/com/nova/assistant/SelfKnowledgeRulesTest.kt',encoding='utf-8').read()
low=k.lower()
chk={
 'voice hook wired, before HelpGuide ask':0<ns.find('SelfKnowledge.handle(this')<ns.find('HelpGuide.isAsk(text)'),
 'phone/laptop/business all say NOT TESTED':k.count('NOT TESTED')>=3,
 'limits: no security bypass, HAAN needed':'bypass nahi karta' in k and 'HAAN' in k,
 'no claim of finished self-build':'Abhi build/install shuru nahi hota' in k,
 'no network / AI call in self knowledge':all(x not in k for x in ('HttpURLConnection','CloudTaskText','ProviderChain')),
 'no fake "perfect/100%" claims':not re.search(r'perfect|100 ?%|guarantee',low),
 'unit tests exist':'class SelfKnowledgeRulesTest' in ut and ut.count('@Test')>=5,
}
bad=[x for x,v in chk.items() if not v]
for x,v in chk.items(): print(('PASS' if v else 'FAIL')+': '+x)
sys.exit(1 if bad else 0)
