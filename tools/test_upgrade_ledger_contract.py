#!/usr/bin/env python3
"""v7.182 Part B(i) static contract: upgrade ledger = request list + HAAN gate. It must NOT push, build or install by itself."""
import sys
R='app/src/main/java/com/nova/assistant/'
u=open(R+'UpgradeLedger.kt',encoding='utf-8').read(); ma=open(R+'MainActivity.kt',encoding='utf-8').read()
ns=open(R+'JarvisService.kt',encoding='utf-8').read(); ui=open('app/src/main/assets/ui.html',encoding='utf-8').read()
ut=open('app/src/test/java/com/nova/assistant/UpgradeRulesTest.kt',encoding='utf-8').read()
chk={
 'no network / push / install in ledger':all(x not in u for x in ('HttpURLConnection','GitHubPublish','AppInstaller','PackageInstaller','AutopilotNet','startActivity')),
 'retry cap 5':'MAX_TRIES = 5' in u,
 'every phone/GitHub change needs HAAN':'fun needsOwnerYes' in u and 'to == APPROVED || to == PUSHED || to == INSTALLED || to == UNDONE' in u,
 'DRAFT cannot jump to PUSHED/INSTALLED':'DRAFT -> to == APPROVED || to == REJECTED' in u,
 'protected (signing/secret/build.yml) blocked':'"build.yml"' in u and 'BLOCKED' in u,
 'honest reply: nothing built yet':'Abhi kuch build ya install nahi hoga' in u,
 'bridge upgradeList + upgradeAct capped':'fun upgradeList()' in ma and 'fun upgradeAct(id: Int, act: String)' in ma and 'act.take(8)' in ma,
 'voice hook wired':'UpgradeLedger.handle(this' in ns,
 'UI names bridge + one block':'Android.upgradeList()' in ui and 'Android.upgradeAct(' in ui and ui.count('v7.182 JARVIS UPGRADES (add-only)')==1,
 'unit tests exist':'class UpgradeRulesTest' in ut and ut.count('@Test')>=7,
}
bad=[k for k,v in chk.items() if not v]
for k,v in chk.items(): print(('PASS' if v else 'FAIL')+': '+k)
sys.exit(1 if bad else 0)
