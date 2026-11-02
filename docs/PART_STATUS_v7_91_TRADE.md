# v7.91 TRADE status (honest)

| Item | Status |
|---|---|
| TradeEngine (indicators, signal, lots, journal, score card, gate) | PASS - 19 JUnit |
| Whole project compile (kotlinc 1.9.24 + android-34 jar) + 1444 JUnit | PASS |
| tools/test_trade_contract.py | PASS |
| TRADE screen render (headless Chromium, fake bridge) | PASS |
| Gradle / AGP / GitHub Actions build | NOT RUN |
| Live candle fetch on a phone | NOT TESTED (sandbox network blocked) |
| MT5 screen driver (read Demo/balance/price, New Order, fill fields, Buy/Sell) | NOT TESTED on a phone |
| Real order flow | NOT TESTED, and locked by the score card on purpose |

## First phone test (in this order)
1. Install MetaTrader 5, log in to a DEMO account. Turn NOVA Accessibility ON.
2. NOVA > home > TRADE > pick XAUUSD > ANALYSE. BUY/SELL/WAIT with numbers should appear.
3. PRACTICE ME SAVE a few ideas, later RESULT UPDATE: the score card should count them.
4. Open MT5 on the XAUUSD chart/quotes screen, then MT5 DEMO ME ORDER. Watch where it stops (the message says the stage). If MT5 texts differ, send me that message + a screenshot and I fix that stage.
5. Never use REAL until the score card unlocks it.
