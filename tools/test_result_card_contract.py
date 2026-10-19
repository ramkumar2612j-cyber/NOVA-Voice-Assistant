#!/usr/bin/env python3
"""v7.67 Part 15 static contract (CHECKPOINT 3): analysis result card + History + "poora padho" read-out. Text only; no frame / path / face. Compile + JUnit + phone: see status file."""
import os, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'); J = os.path.join(R, 'app/src/main/java/com/nova/assistant')
rd = lambda n: open(os.path.join(J, n), encoding='utf-8').read()
C = rd('ResultCardRules.kt'); P = rd('AnalysisPipeline.kt'); S = rd('NovaService.kt'); bad = []
need = lambda c, m: bad.append(m) if not c else None
for f in ('fun card(', 'fun historyLine(', 'fun isReadAll(', 'fun chunks(', 'fun speakable('): need(f in C, f + ' missing')
for w in ('java.io', 'HttpURLConnection', 'Bitmap', 'FaceDetector', 'android.'): need(w not in C, 'pure rules must not use ' + w)
need('.take(3)' in C and 'hostOf(' in C, 'card: max 3 domains')
need('lastCard = Pair(ResultCardRules.card(r, en)' in P and 'fun takeCard()' in P, 'pipeline must hand over the card')
d = S[S.index('fun deliverAnalysis'):S.index('fun analysisChatLine')]
need('AnalysisPipeline.takeCard()' in d and 'ActionLog.add(this, "NOVA ANALYSIS"' in d and 'lastFullReport = text' in d, 'deliverAnalysis: card + History + remember report')
need('ResultCardRules.isReadAll(text)' in S and 'return readFullReport()' in S, 'poora padho route missing')
r = S[S.index('private fun readFullReport'):S.index('// ---- v7.66 PART 14B-4')]
need('cfg.silentMode' in r, 'read-out must respect silent mode')
need(S.index('ResultCardRules.isReadAll(text)') < S.index('MovieScanRules.parse(text) == MovieScanRules.Ask.MOVIE'), 'read-all route must run before movie route')
if bad: print('FAIL result card:', bad); sys.exit(1)
print('PASS result card contract (static, Part 15)')
