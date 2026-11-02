"""v6.91 EASY HELP contract: HelpGuide.kt (voice/chat answers + AI facts) and the MADAD popup in ui.html must list the same topics."""
import re, json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
K = open(os.path.join(HERE, '..', 'app/src/main/java/com/nova/assistant/HelpGuide.kt'), encoding='utf-8').read()
U = open(os.path.join(HERE, '..', 'app/src/main/assets/ui.html'), encoding='utf-8').read()
S = open(os.path.join(HERE, '..', 'app/src/main/java/com/nova/assistant/NovaService.kt'), encoding='utf-8').read()
bad = []
kt = re.findall(r'Topic\("([a-z]+)", "([^"]*)", "[^"]*", listOf\([^)]*\), "([^"]*)"', K)
m = re.search(r'var CAPS=(\[.*?\]);\nfunction J', U, re.S)
caps = json.loads(m.group(1)) if m else []
if len(kt) < 10: bad.append('too few Kotlin topics: %d' % len(kt))
if [(a, c) for a, b, c in kt] != [(c['id'], c['ex']) for c in caps]: bad.append('Kotlin topics and ui.html CAPS differ (ids/examples)')
if [(a, b) for a, b, c in kt] != [(c['id'], c['t']) for c in caps]: bad.append('Kotlin and ui.html titles differ')
for need in ['id="helpw"', 'id="c_help"', 'id="hw_l"', 'id="hw_q"', 'id="hw_x"', 'id="help-v691"']:
    if need not in U: bad.append('ui.html missing ' + need)
if 'HelpGuide.isAsk(text)' not in S: bad.append('NovaService.handle does not call HelpGuide.isAsk')
if S.count('HelpGuide.aiFacts()') < 2: bad.append('AI facts missing in Gemini or free chain prompt')
fn = re.search(r'fun aiFacts\(\): String =(.*?)\n\}', K, re.S)
if not fn or '$' in fn.group(1).replace('${', '').replace('$', '') and False: bad.append('aiFacts check')
if fn and re.search(r'\$[A-Za-z{]', fn.group(1)): bad.append('aiFacts must not contain a dollar template (it is pasted into a raw-string prompt)')
REQ = ['wabiz','autoreply','connectors','routines','lockguard','teach','powerguide','gmail','agent','screenalert','watchmic','quiet','offlineai','leads','limits']
for r in REQ:
    if r not in [c['id'] for c in caps]: bad.append('Part 2 topic missing in MADAD: ' + r)
if len(set(c['id'] for c in caps)) != len(caps): bad.append('duplicate topic ids')
for c in caps:
    if not c['d'] or not c['t']: bad.append('empty text for ' + c['id'])
if bad:
    print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS help contract: %d topics equal in Kotlin and ui.html' % len(caps))
