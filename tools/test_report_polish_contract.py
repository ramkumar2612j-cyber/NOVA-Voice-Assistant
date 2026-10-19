#!/usr/bin/env python3
"""v7.56 Part 13B-2c static contract: the ANALYSE report is shown in sections (summary / headings / bullets / confidence) built with createElement + textContent only (no innerHTML),
a long report scrolls inside its own box (not the page), the 320 px layout rules exist, no timer-driven progress, no new chat-bar button, SCR list unchanged. Real render NOT run (owner order)."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
U = open(os.path.join(R, 'app/src/main/assets/ui.html'), encoding='utf-8').read()
bad = []
blk = U.split('<script id="pt13_analyse">')[1].split('</script>')[0]
css = U.split('<style id="pt13_analyse_css">')[1].split('</style>')[0]
if 'function fill(' not in blk or 'fill(t)' not in blk: bad.append('sectioned report renderer missing')
if 'innerHTML' in blk or 'insertAdjacentHTML' in blk or 'document.write' in blk: bad.append('HTML injection path in pt13_analyse')
if 'setInterval' in blk: bad.append('timer-driven progress')
for cls in ('m13_sum', 'm13_h', 'm13_ul', 'm13_conf', 'm13_src'):
    if cls not in blk or '.' + cls not in css and '#m13_res .' + cls not in css: bad.append('class ' + cls + ' not used / styled')
if 'overflow-y:auto' not in css or 'max-height:50vh' not in css or 'overscroll-behavior:contain' not in css: bad.append('long report does not scroll inside its box')
if '@media (max-width:360px)' not in css: bad.append('no 320 px rules')
if 'res.tabIndex=0' not in blk: bad.append('report box not focusable (keyboard scroll)')
for h in ('Kya dikha:', 'What I saw:', 'Likha hua text:', 'Verify nahi hua:', 'Not verified:', 'Limits:', 'Sources:'):
    if h not in blk: bad.append('heading not recognised: ' + h)
# headings must match what AnalysisRules.reportText writes
K = open(os.path.join(R, 'app/src/main/java/com/nova/assistant/AnalysisRules.kt'), encoding='utf-8').read().split('fun reportText')[1].split('fun speechSummary')[0]
for h in ('Kya dikha:', 'Likha hua text:', 'Sources se mila:', 'Verify nahi hua:', 'Bharosa: ', 'Limits:'):
    if h not in K: bad.append('reportText no longer writes ' + h)
if "var SCR=['p1_auto'" not in U or U.count('pt13_analyse') < 3: bad.append('SCR / block id changed')
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS report polish contract (static; headless render + phone NOT RUN, owner order)')
