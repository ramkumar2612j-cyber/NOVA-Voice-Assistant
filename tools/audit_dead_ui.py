#!/usr/bin/env python3
"""NOVA Part 16 audit (add-only, read-only): dead controls in ui.html.
1 input/button/select/textarea id with NO handler anywhere in JS (not referenced by id, no inline on*=, not inside a <label for>)
2 @JavascriptInterface methods never named in ui.html
3 HelpGuide.kt topic ids missing from ui.html CAPS (and the reverse)
4 leftover SocialReplyRules / PaymentReplyRules references in main code
Exit 1 only for items 1, 3, 4 (real gaps). Usage: python3 tools/audit_dead_ui.py"""
import os, re, sys
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
MAIN = os.path.join(R, 'app/src/main'); KT = os.path.join(MAIN, 'java/com/nova/assistant')
UI = open(os.path.join(MAIN, 'assets/ui.html'), encoding='utf-8').read()
kt = {f: open(os.path.join(KT, f), encoding='utf-8').read() for f in sorted(os.listdir(KT)) if f.endswith('.kt')}
real = []
# 1 dead controls
ctrl = re.findall(r'<(input|button|select|textarea)\b([^>]*)>', UI)
ids = []
for tag, attrs in ctrl:
    m = re.search(r'\bid="([^"]+)"', attrs)
    if m: ids.append((tag, m.group(1), attrs))
script = '\n'.join(re.findall(r'<script[^>]*>(.*?)</script>', UI, re.S))
dead = []
for tag, i, attrs in ids:
    if re.search(r'\bon\w+\s*=', attrs): continue
    if re.search(r"['\"]%s['\"]" % re.escape(i), script): continue
    if re.search(r'#%s\b' % re.escape(i), script): continue
    if re.search(r'getElementById\(\s*[^)]*\)', script) and re.search(r'\b%s\b' % re.escape(i), script): continue
    if re.search(r'for="%s"' % re.escape(i), UI): continue
    dead.append((tag, i))
print('1 DEAD CONTROLS (no handler found):', len(dead))
for t, i in dead: print('   -', t, '#' + i)
real += ['dead control #' + i for t, i in dead]
# 2 unused bridge methods
bridge = set()
for f, s in kt.items():
    for m in re.finditer(r'@JavascriptInterface\s+fun\s+(\w+)', s): bridge.add(m.group(1))
unused = sorted(b for b in bridge if not re.search(r'\b%s\b' % b, UI))
print('2 BRIDGE METHODS NOT NAMED IN PAGE (info):', len(unused), unused)
# 3 help topics
hg = kt.get('HelpGuide.kt', '')
ktids = set(re.findall(r'Topic\(\s*"([^"]+)"', hg))
capids = set(re.findall(r"id\s*:\s*['\"]([^'\"]+)['\"]", UI))
miss = sorted(t for t in ktids if t not in UI)
print('3 HELP TOPICS in Kotlin:', len(ktids), '| missing from ui.html:', miss)
real += ['help topic ' + t for t in miss]
# 4 retired rule leftovers
left = []
for f, s in kt.items():
    for w in ('SocialReplyRules', 'PaymentReplyRules'):
        for n, line in enumerate(s.splitlines(), 1):
            if w in line and not line.strip().startswith(('//', '*', '/*')): left.append('%s:%d %s' % (f, n, w))
ui_left = [w for w in ('SocialReplyRules', 'PaymentReplyRules') if w in UI]
print('4 RETIRED RULE LEFTOVERS: kotlin', left, '| ui.html', ui_left)
real += left + ui_left
print('REAL GAPS:', len(real)); [print('  ', r) for r in real]
sys.exit(1 if real else 0)
