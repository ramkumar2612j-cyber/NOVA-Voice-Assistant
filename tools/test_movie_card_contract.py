#!/usr/bin/env python3
"""v7.66 Part 14B-5 static contract: movie result card + History + chat. Text only, no frame / path / face. Compile / JUnit / phone NOT RUN."""
import os, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'); J = os.path.join(R, 'app/src/main/java/com/nova/assistant')
rd = lambda n: open(os.path.join(J, n), encoding='utf-8').read()
G = rd('MovieGuessRules.kt'); S = rd('NovaService.kt'); bad = []
need = lambda c, m: bad.append(m) if not c else None
need('fun card(' in G and 'fun historyLine(' in G, 'card / historyLine missing')
need('.take(3)' in G and 'host(it.url)' in G, 'card must show only up to 3 domains')
d = S[S.index('private fun deliverMovie'):S.index('private fun startMovieScan')]
need('ActionLog.add(this, "NOVA MOVIE"' in d and 'emit("ai"' in d and 'hud?.setReply' in d, 'deliverMovie must write chat + History + card')
need('frame' not in G[G.index('fun historyLine'):G.index('fun historyLine') + 200].lower(), 'history must not mention frames')
need('MovieGuessRules.card(d.outcome, en)' in S and 'MovieGuessRules.historyLine(d.outcome)' in S, 'service must pass card + history')
need('h.post { laser?.setStage("RESULT 100") }' in S, 'RESULT 100 flash missing')
if bad: print('FAIL movie card:', bad); sys.exit(1)
print('PASS movie card contract (static). Compile / JUnit MovieCardTest / phone: NOT RUN')
