# v7.90 meeting schedule + feature fix - status
RUN: python port of MeetingEventRules on 12 phrases (owner phrase -> "RSS Meeting" 15:30 today).
NOT RUN: Kotlin compile, JUnit, python guards (no network in sandbox) -> first compile = GitHub Actions "Build and Test APK".
NOT TESTED on phone: 1) Setup > ALLOW CALENDAR (now also asks write) 2) "nova 3:30 ka schedule banao rss meeting me jana hai" 3) "nova band features set karo".
If Actions fails: send the red error lines; fix only MeetingEventRules.kt / FeatureFixRules.kt / NovaService.calendarAdd, featuresFix, insertCalendarEvent / their tests.
LAG: ui.html autosmooth-v790 + fsLoad change-check + slower polls (NOT TESTED on phone; cause unconfirmed). Still need from owner: Settings > TEST REPORT text, and which 2 features show WARN.
TRADING: TradeRules.kt / runTradeAnalysis / TradeRulesTest added (analysis only, no auto trade). NOT RUN: compile + JUnit. NOT TESTED on phone: 'nova gold ka analysis karo', 'mt5 kholo' must still be the normal open-app.
If Actions fails: fix only TradeRules.kt / NovaService.runTradeAnalysis / TradeRulesTest.kt.
