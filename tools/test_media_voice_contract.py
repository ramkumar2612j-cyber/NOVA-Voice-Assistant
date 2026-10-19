#!/usr/bin/env python3
"""v7.51 Part 11-D contract: voice 'kya mila' / 'video analyse' is wired in NovaService, honest, no persona kind; MADAD + SKILLS + CAPS parity for 'media'."""
import os, re, sys, json
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
S = rd('app/src/main/java/com/nova/assistant/NovaService.kt'); M = rd('app/src/main/java/com/nova/assistant/MediaIntake.kt')
H = rd('app/src/main/java/com/nova/assistant/HelpGuide.kt'); U = rd('app/src/main/assets/ui.html'); P = rd('app/src/main/java/com/nova/assistant/PersonaRules.kt')
bad = []
if 'fun voiceIntent(' not in M: bad.append('MediaIntake.voiceIntent missing')
if 'MediaIntake.voiceIntent(text)' not in S: bad.append('NovaService.handle does not call MediaIntake.voiceIntent')
if 'MediaIntakeStore.statusText(this)' not in S: bad.append('STATUS does not use MediaIntakeStore.statusText')
if 'MediaIntake.analyseNotYetReply(' not in S: bad.append('analyse reply not wired')
if S.count('lastKind = "media"') < 2: bad.append('media kind not set')
if '"media"' in P: bad.append('persona must NOT allow the media kind')
# order: the media block must come before the generic agent task catch
if S.find('MediaIntake.voiceIntent(text)') > S.find('AgentRules.isTask(text)') or S.find('MediaIntake.voiceIntent(text)') < 0: bad.append('media block must run before AgentRules.isTask')
# the reply must not claim analysis happened
m = re.search(r'fun analyseNotYetReply\(.*?\n    \}', M, re.S)
if not m or 'abhi nahi' not in m.group(0) or 'not available yet' not in m.group(0): bad.append('analyse reply not honest')
# parity
caps = json.loads(re.search(r'var CAPS=(\[.*?\]);\nfunction J', U, re.S).group(1))
c = [x for x in caps if x['id'] == 'media']
k = re.search(r'Topic\("media", "([^"]*)"', H)
if not c or not k or c[0]['t'] != k.group(1): bad.append('media topic title differs Kotlin vs CAPS')
if "t:'Media: Share karke bhejna'" not in U: bad.append('SKILLS entry missing')
if "'pt11_media'" not in U.split('var SCR=')[1].split(']')[0]: bad.append('SCR missing pt11_media')
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS media voice contract')
