#!/usr/bin/env python3
"""v6.18c structural + sentence check for FeatureCheck.kt (does NOT run Kotlin). Site sentences are run through the Python mirror of SiteIntent."""
import os, re, importlib.util
here = os.path.dirname(os.path.abspath(__file__))
J = os.path.join(here, '..', 'app/src/main/java/com/nova/assistant')
rd = lambda p: open(os.path.join(J, p), encoding='utf-8').read()
fc, st, ns, ui = rd('FeatureCheck.kt'), rd('SelfTest.kt'), rd('NovaService.kt'), open(os.path.join(here, '..', 'app/src/main/assets/ui.html'), encoding='utf-8').read()
src = open(os.path.join(here, 'test_site_contract.py'), encoding='utf-8').read().split('PUB = [')[0]
ns_ = {'__file__': os.path.join(here, 'test_site_contract.py')}; exec(src, ns_)
parse = ns_['parse']; is_publish = ns_['is_publish']
r1 = parse('nova ek restaurant ki website banao'); r2 = parse('ek website banao jahan nova app download ho sake')
chk = [
    ('site sentence 1 -> restaurant', r1 and r1[0] == 'restaurant'),
    ('site sentence 2 understood', r2 is not None),
    ('publish sentence', is_publish('nova website publish karo')),
    ('SelfTest calls FeatureCheck.understanding', 'FeatureCheck.understanding(rec)' in st),
    ('SelfTest calls FeatureCheck.readiness', 'FeatureCheck.readiness(ctx, rec)' in st),
    ('SelfTest calls testPlan', 'svc.testPlan()' in st),
    ('NovaService has testPlan', 'fun testPlan(): String' in ns and 'private fun agentPlanCall' in ns),
    ('report has KAAM KI BAAT line', 'KAAM KI BAAT' in st),
    ('SelfTest expects protected Settings text to remain blocked', 'Protected screen blocked (Android Settings)' in st and 'sample=' not in st),
    ('no key value is printed (only counts / presence)', not re.search(r'getKey\(|getGroq\(|getSlot\(', fc)),
    ('covers site, publish, image, learn, agent', all(x in fc for x in ('SiteIntent.parse', 'SiteIntent.isPublish', 'ImageIntent.parse', 'LearnRules.parse', 'AgentRules.isTask'))),
    ('checks ChatGPT app installed', 'appInstalled(ctx, "ChatGPT")' in fc),
    ('UI text mentions TAIYAAR/BAND', 'TAIYAAR' in ui and 'BAND' in ui),
]
bad = [n for n, ok in chk if not ok]
print('FAIL: ' + ', '.join(bad) if bad else 'ALL PASSED (%d checks, structural only; Kotlin not run)' % len(chk))
raise SystemExit(1 if bad else 0)
