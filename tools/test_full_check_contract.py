#!/usr/bin/env python3
"""v7.181 FULL CHECK static contract: merged report is wired (Kotlin object, bridge, voice hook, UI buttons, unit test)."""
import re,sys
R='app/src/main/java/com/nova/assistant/'
fc=open(R+'FullCheck.kt',encoding='utf-8').read(); ma=open(R+'MainActivity.kt',encoding='utf-8').read()
ns=open(R+'JarvisService.kt',encoding='utf-8').read(); ui=open('app/src/main/assets/ui.html',encoding='utf-8').read()
ut=open('app/src/test/java/com/nova/assistant/FullCheckRulesTest.kt',encoding='utf-8').read()
chk={
 'merges SelfTest report':'AutoTest.lastReport' in fc,
 'merges AUTO VERIFY report':'FeatureVerify.lastReport' in fc,
 'merges FeatureStatus':'FeatureStatus.json' in fc,
 'merges recent errors':'ErrLog.dump()' in fc,
 'read-only (never starts a test itself)':'SelfTest.start' not in fc and 'Flash' not in fc,
 'bridge fullReport':'fun fullReport()' in ma and 'FullCheck.report(' in ma,
 'voice hook before AUTO VERIFY ask':0<ns.find('FullCheckRules.isAsk')<ns.find('FeatureVerify.isAsk(text)'),
 'UI names fullReport + copyText':'Android.fullReport()' in ui and 'Android.copyText(' in ui,
 'UI one block only':ui.count('v7.181 FULL CHECK (add-only)')==1,
 'honest when nothing ran':'chali nahi' in fc,
 'unit test exists':'class FullCheckRulesTest' in ut and ut.count('@Test')>=6,
 'old report buttons kept':'id="st_copy"' in ui and 'id="st_run"' in ui,
}
bad=[k for k,v in chk.items() if not v]
for k,v in chk.items(): print(('PASS' if v else 'FAIL')+': '+k)
sys.exit(1 if bad else 0)
