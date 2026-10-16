"""v6.99 Part 4 + 5 contract (static): the 15 bridge settings have UI, no duplicate ids for them, new help topics equal in Kotlin / ui.html, self-test knows the new features."""
import os, re, sys, collections
H = os.path.dirname(os.path.abspath(__file__))
def rd(f, base='app/src/main/java/com/nova/assistant'): return open(os.path.join(H, '..', base, f), encoding='utf-8').read()
UI, MA, HG, FC = rd('ui.html', 'app/src/main/assets'), rd('MainActivity.kt'), rd('HelpGuide.kt'), rd('FeatureCheck.kt')
bad = []
for m in ['listChats', 'selfTest', 'selfTestFree', 'selfTestImage', 'clearErrors', 'getWaitSec', 'setWaitSec', 'getMemoryText', 'getAutoLearn', 'setAutoLearn',
          'getWebBrowsers', 'getLocalFirst', 'getHomeCity', 'testBrowser', 'requestCalendarRead', 'hasCalendarRead']:
    if 'fun %s(' % m not in MA: bad.append('bridge missing: ' + m)
    if ('Android.%s(' % m not in UI) and ("'%s'" % m not in UI): bad.append('ui.html never calls ' + m)
for i in ['st_run', 'st_clr', 'st_free', 'st_img', 'wb_wait', 'mem_copy', 'al_batt', 'ar_ai']:
    if len(re.findall(r'id="%s"' % i, UI)) != 1: bad.append('id must exist exactly once: ' + i)
if len(re.findall(r'id="al_on"', UI)) != 1: bad.append('al_on must be unique (auto-learn); battery alert is al_batt')
if 'g("al_on")' not in UI or 'Android.setAutoLearn(' not in UI or 'g("al_batt")' not in UI: bad.append('auto-learn / battery switches not wired to their own ids')
if 'window.onBrowserTest' not in UI: bad.append('browser test result handler missing')
for t in ['callanswer', 'phonecontrol', 'smartreply', 'calendar']:
    if 'Topic("%s"' % t not in HG: bad.append('help topic missing in Kotlin: ' + t)
    if '"id": "%s"' % t not in UI: bad.append('help topic missing in ui.html CAPS: ' + t)
if "setOf(\"chrome\", \"download\", \"safety\", \"social\", \"callanswer\", \"phonecontrol\", \"smartreply\")" not in HG: bad.append('new topics must be PRIORITY (screen / call / questions words would hijack them)')
for s in ['Samajh: call uthao / call kaato', 'Samajh: screenshot, ringer, rotation', 'Samajh: AI smart jawab', 'Taiyaar: Calendar meeting yaad dilana'.replace('Taiyaar: ', '')]:
    if s not in FC: bad.append('FeatureCheck missing: ' + s)
# every Android.x( the page calls must exist in the bridge
called = set(re.findall(r'Android\.([A-Za-z0-9_]+)\(', UI))
have = set(re.findall(r'fun ([A-Za-z0-9_]+)\(', MA))
miss = sorted(x for x in called if x not in have)
if miss: bad.append('ui.html calls bridge methods that do not exist: ' + ', '.join(miss))
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS part 4 + 5 contract')
