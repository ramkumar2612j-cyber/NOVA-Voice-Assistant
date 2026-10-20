#!/usr/bin/env python3
"""v7.91 TRADE contract (static, no phone): the practice / real-money safety rules must stay in the code, and every page call must have a bridge method."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
def rd(p): return open(os.path.join(R, p), encoding='utf-8').read()
H = rd('app/src/main/java/com/nova/assistant/TradeHub.kt'); E = rd('app/src/main/java/com/nova/assistant/TradeEngine.kt')
M = rd('app/src/main/java/com/nova/assistant/MainActivity.kt'); U = rd('app/src/main/assets/ui.html'); S = rd('app/src/main/java/com/nova/assistant/NovaService.kt')
bad = []
def need(c, m):
    if not c: bad.append(m)
need('mt.demo != true' in H and 'Demo account nahi dikh raha' in H, 'demo order must stop unless MT5 says Demo')
need('mt.demo != false' in H, 'real order must stop unless MT5 says Real')
need('Gate.realAllowed' in H and 'ownerConfirmed' in H, 'real order must go through Gate.realAllowed')
need(H.index('Gate.realAllowed') < H.index('startActivity(li'), 'gate must be checked BEFORE MT5 is touched')
need('AlertDialog' in M and 'HAAN, REAL BHEJO' in M and 'TradeEngine.Gate.unlocked' in M, 'real order needs the native Yes/No box and an unlocked score card')
need('sendToMt5(this, tr.signal, tr.lots, false, false)' in S, 'voice may only place DEMO orders')
need('real = true' not in S and ', true, true)' not in S.split('private fun runTradeAnalysis')[1].split('private fun runProfileSearch')[0], 'voice path must never send a real order')
need('REAL_RISK_PCT' in M, 'real lots must use the smaller real risk')
need('180_000L' in H, 'orders must use a fresh (<=3 min) analysis only')
for w in ('sure shot', 'pakka profit', 'no loss guaranteed'):
    need(w not in E.lower() and w not in H.lower(), 'forbidden promise: ' + w)
need('guarantee nahi' in E, 'signal text must say guarantee nahi')
need('Mode.DEMO' in E and 'MIN_TRADES = 100' in E and 'MIN_DAYS = 30' in E and 'MIN_LOWER_BOUND_R' in E, 'gate constants present (v7.92: 100 trades / 30 days / confidence)')
need('closedOnly' in H and 'settleAllV2' in H and 'analyse2' in H and 'fetchFeed' in H, 'v7.92 hub functions present')
need('analyse2' in S and 'cardJson2' in M and 'settleAllV2' in M and 'tradeSetFilters' in M and 'tradeSetFilters' in U, 'v7.92 wiring present')
# every Android.trade* call in the page exists as a bridge method
calls = set(re.findall(r"A\('(trade\w+)'", U))
meths = set(re.findall(r'fun (trade\w+)\(', M))
need(calls <= meths, 'page calls without bridge method: %s' % sorted(calls - meths))
need('window.novaTradeShow' in U and 'novaTradeShow()' in U, 'home card must open the TRADE view')
need('onTradeAnalysis' in M and 'onTradeSent' in M and 'onTradeSettled' in M, 'Kotlin->JS callbacks present')
print('PASS test_trade_contract' if not bad else 'FAIL test_trade_contract\n - ' + '\n - '.join(bad))
sys.exit(1 if bad else 0)
