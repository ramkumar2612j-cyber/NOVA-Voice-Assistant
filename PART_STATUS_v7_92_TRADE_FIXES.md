# v7.92 trade fixes - status
RUN: python port of the new maths (26 checks PASS), test_trade_contract PASS, bridge / dead-UI / feature guards PASS, UI trade script syntax (node) OK, no new failures vs v7.91 in the guards I ran (3 + 2 + 9 fail the same on v7.91).
NOT RUN: Kotlin compile, JUnit (9 new tests written), GitHub Actions, phone.
NOT DONE: NovaService split, Thread.sleep cleanup, empty-catch logging (risky without compile; do after first green build).
If Actions fails: fix only TradeEngine.kt / TradeHub.kt / TradeEngineTest.kt (v792 blocks).
Phone test: TRADE screen > ANALYSE (check 1H trend, spread, data source cells), switches in SETTINGS > SAVE.

## v7.93 addition
- 76 hidden (empty) catch blocks now log via ErrLog.addOnce ("quiet:<File>"); 106 cleanup-type ones stay silent. Thread.sleep audited: no change needed. NovaService split NOT done (needs compile). NOT RUN: compile, JUnit (ErrLogTest +1), phone.
