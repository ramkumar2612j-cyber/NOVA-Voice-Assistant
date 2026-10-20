package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test
import com.nova.assistant.TradeEngine.Candle
import com.nova.assistant.TradeEngine.Dir
import com.nova.assistant.TradeEngine.Mode
import com.nova.assistant.TradeEngine.Status
import com.nova.assistant.TradeEngine.Trade

class TradeEngineTest {

    /** Seeded random walk with a drift: looks like a real market (up and down candles), same every run. */
    private fun series(n: Int, drift: Double, noise: Double, start: Double = 2000.0, seed: Long = 7L): List<Candle> {
        val r = java.util.Random(seed)
        var c = start
        return (0 until n).map { i ->
            val o = c
            c += drift + r.nextGaussian() * noise
            Candle(1_700_000_000_000L + i * 900_000L, o, max(o, c) + Math.abs(r.nextGaussian()) * noise * 0.4, min(o, c) - Math.abs(r.nextGaussian()) * noise * 0.4, c)
        }
    }
    private fun max(a: Double, b: Double) = if (a > b) a else b
    private fun min(a: Double, b: Double) = if (a < b) a else b

    @Test fun emaFollowsAndRsiRange() {
        val up = (1..40).map { it.toDouble() }
        val e = TradeEngine.ema(up, 10)
        assertEquals(up.size, e.size)
        assertTrue(e.last() < up.last() && e.last() > up[20])
        assertEquals(100.0, TradeEngine.rsi(up), 0.001)
        assertEquals(0.0, TradeEngine.rsi(up.reversed()), 0.001)
        assertEquals(50.0, TradeEngine.rsi(listOf(1.0, 2.0)), 0.001)
    }

    @Test fun atrIsPositiveAndZeroWhenShort() {
        assertTrue(TradeEngine.atr(series(60, 0.5, 1.0)) > 0.0)
        assertEquals(0.0, TradeEngine.atr(series(5, 0.5, 1.0)), 0.0)
    }

    @Test fun tooFewCandlesIsWait() {
        val s = TradeEngine.analyse("XAUUSD", series(20, 1.0, 1.0))
        assertEquals(Dir.WAIT, s.dir)
        assertTrue(s.waitWhy.isNotBlank())
    }

    @Test fun flatMarketIsWait() {
        val flat = (0 until 120).map { i -> val c = 2000.0 + Math.sin(i / 2.0) * 0.5; Candle(i * 900_000L, c, c + 0.3, c - 0.3, c) }
        assertEquals(Dir.WAIT, TradeEngine.analyse("XAUUSD", flat).dir)
    }

    @Test fun uptrendNeverSellsAndLevelsAreOrdered() {
        var buys = 0; var total = 0
        for (seed in 1L..40L) for (n in intArrayOf(100, 120, 140)) {
            total++
            val s = TradeEngine.analyse("XAUUSD", series(n, 0.8, 3.0, 2000.0, seed))
            val lastUp = series(n, 0.8, 3.0, 2000.0, seed)
            if (s.dir == Dir.BUY) {
                buys++
                assertTrue(s.sl < s.entry && s.entry < s.tp1 && s.tp1 < s.tp2)
                assertTrue(s.reasons.isNotEmpty())
                assertTrue(lastUp.last().c > 0)
            }
            assertNotEquals("clean uptrend must not be SELL (seed=$seed n=$n)", Dir.SELL, s.dir)
        }
        assertTrue("some BUY ideas expected, got $buys of $total", buys > 0)
        assertTrue("it must also say WAIT often, got $buys of $total", buys < total)
    }

    @Test fun downtrendNeverBuysAndLevelsAreOrdered() {
        var sells = 0; var total = 0
        for (seed in 1L..40L) for (n in intArrayOf(100, 120, 140)) {
            total++
            val s = TradeEngine.analyse("XAUUSD", series(n, -0.8, 3.0, 2000.0, seed))
            if (s.dir == Dir.SELL) {
                sells++
                assertTrue(s.sl > s.entry && s.entry > s.tp1 && s.tp1 > s.tp2)
            }
            assertNotEquals("clean downtrend must not be BUY (seed=$seed n=$n)", Dir.BUY, s.dir)
        }
        assertTrue("some SELL ideas expected, got $sells of $total", sells > 0)
        assertTrue(sells < total)
    }

    @Test fun riskRewardIsAtLeastOnePointFive() {
        for (seed in 1L..40L) {
            val s = TradeEngine.analyse("XAUUSD", series(120, 0.8, 3.0, 2000.0, seed))
            if (s.dir == Dir.BUY) {
                val rr = (s.tp1 - s.entry) / (s.entry - s.sl)
                assertTrue("rr=$rr", rr >= 1.45)
            }
        }
    }

    @Test fun calibrateShiftsToMt5AndRefusesHugeGap() {
        val s = TradeEngine.Signal("XAUUSD", Dir.BUY, 2000.0, 1990.0, 2015.0, 2025.0, 5, 6.0, 55.0, "UP", listOf("x"), "")
        val c = TradeEngine.calibrate(s, 2003.5)
        assertEquals(2003.5, c.entry, 0.001); assertEquals(1993.5, c.sl, 0.001); assertEquals(2018.5, c.tp1, 0.001)
        val bad = TradeEngine.calibrate(s, 2300.0)
        assertEquals(Dir.WAIT, bad.dir)
    }

    @Test fun lotsRiskOnePercent() {
        // gold: 1 lot = 100 oz, a 10 dollar stop = 1000 dollars per lot. 1% of 10000 = 100 -> 0.10 lot
        assertEquals(0.10, TradeEngine.lots("XAUUSD", 10000.0, 1.0, 2000.0, 1990.0), 0.0001)
        // tiny account never goes below the 0.01 minimum and never above the cap
        assertEquals(0.01, TradeEngine.lots("XAUUSD", 100.0, 1.0, 2000.0, 1990.0), 0.0001)
        assertEquals(1.0, TradeEngine.lots("EURUSD", 10_000_000.0, 1.0, 1.1, 1.0999), 0.0001)
        // risk percent is capped at 2
        assertEquals(TradeEngine.lots("XAUUSD", 10000.0, 2.0, 2000.0, 1990.0), TradeEngine.lots("XAUUSD", 10000.0, 50.0, 2000.0, 1990.0), 0.0001)
    }

    @Test fun symbolAliases() {
        assertEquals("XAUUSD", TradeEngine.Spec.canon("gold"))
        assertEquals("XAUUSD", TradeEngine.Spec.canon("XAU/USD"))
        assertEquals("EURUSD", TradeEngine.Spec.canon("eurusd.m"))
        assertEquals("BTCUSD", TradeEngine.Spec.canon("bitcoin"))
        assertEquals("", TradeEngine.Spec.canon("banana"))
        assertTrue(TradeEngine.yahooUrl("gold").contains("GC%3DF"))
        assertEquals("", TradeEngine.yahooUrl("banana"))
    }

    private fun trade(dir: Dir, entry: Double, sl: Double, tp: Double, ts: Long = 1000L) =
        Trade("t1", ts, "XAUUSD", dir, entry, sl, tp, 0.01, Mode.DEMO)

    @Test fun settleTargetStopAndSameCandleIsLoss() {
        val t = trade(Dir.BUY, 100.0, 95.0, 107.5)
        val win = TradeEngine.settle(t, listOf(Candle(2000, 100.0, 108.0, 99.0, 107.0)))
        assertEquals(Status.WIN, win.status); assertEquals(1.5, win.r, 0.001)
        val loss = TradeEngine.settle(t, listOf(Candle(2000, 100.0, 101.0, 94.0, 95.0)))
        assertEquals(Status.LOSS, loss.status); assertEquals(-1.0, loss.r, 0.001)
        // one candle touches both: the pessimistic order says LOSS
        val both = TradeEngine.settle(t, listOf(Candle(2000, 100.0, 110.0, 90.0, 100.0)))
        assertEquals(Status.LOSS, both.status)
        // candles older than the trade are ignored
        assertEquals(Status.OPEN, TradeEngine.settle(t, listOf(Candle(500, 100.0, 200.0, 1.0, 100.0))).status)
        // sell side
        val s = trade(Dir.SELL, 100.0, 105.0, 92.5)
        assertEquals(Status.WIN, TradeEngine.settle(s, listOf(Candle(2000, 100.0, 101.0, 92.0, 93.0))).status)
        assertEquals(Status.LOSS, TradeEngine.settle(s, listOf(Candle(2000, 100.0, 105.5, 99.0, 104.0))).status)
    }

    @Test fun staleTradeClosesEvenByAge() {
        val t = trade(Dir.BUY, 100.0, 95.0, 107.5, 1000L)
        val e = TradeEngine.settle(t, listOf(Candle(2000, 100.0, 101.0, 99.0, 102.0)), 1000L, 5000L)
        assertEquals(Status.EVEN, e.status); assertEquals(0.4, e.r, 0.001)
    }

    private fun closed(n: Int, winEvery: Int, day0: Long = 0): List<Trade> = (1..n).map { i ->
        val win = i % winEvery != 0
        Trade("x$i", i.toLong(), "XAUUSD", Dir.BUY, 100.0, 95.0, 107.5, 0.01, Mode.DEMO,
            if (win) Status.WIN else Status.LOSS, 0.0, if (win) 1.5 else -1.0, (day0 + i / 3) * 86_400_000L)
    }

    @Test fun cardMath() {
        val c = TradeEngine.card(closed(30, 3))   // 20 wins (1.5R), 10 losses (-1R)
        assertEquals(30, c.closed); assertEquals(20, c.wins); assertEquals(10, c.losses)
        assertEquals(66.67, c.winRate, 0.01)
        assertEquals(20.0 / 30.0 * 1.0, c.expectancy, 0.02)   // (30 - 10) / 30
        assertEquals(3.0, c.profitFactor, 0.01)
        assertTrue(c.maxDrawdownR >= 1.0)
    }

    @Test fun gateLockedWhenFewTradesOrBadScore() {
        val few = TradeEngine.card(closed(10, 3))
        assertFalse(TradeEngine.Gate.unlocked(few))
        assertTrue(TradeEngine.Gate.missing(few).any { it.contains("Practice trades") })
        val bad = TradeEngine.card(closed(40, 2))   // wins 1.5 / loses 1 alternating = still positive, make it losing instead
        val losing = TradeEngine.card((1..40).map { i -> Trade("l$i", i.toLong(), "XAUUSD", Dir.SELL, 1.0, 2.0, 0.0, 0.01, Mode.DEMO, Status.LOSS, 0.0, -1.0, (i / 3) * 86_400_000L) })
        assertFalse(TradeEngine.Gate.unlocked(losing))
        assertTrue(bad.closed == 40)
    }

    @Test fun gateOpensOnlyWhenEverythingPasses() {
        val good = TradeEngine.card(closed(120, 4, 100))   // v7.92: gate needs 100 trades / 30 days
        assertTrue(TradeEngine.Gate.missing(good).toString(), TradeEngine.Gate.unlocked(good))
        // unlocked is still not enough: owner confirm, daily loss, open limit
        assertFalse(TradeEngine.Gate.realAllowed(good, false, 0.0, 0).first)
        assertFalse(TradeEngine.Gate.realAllowed(good, true, 2.0, 0).first)
        assertFalse(TradeEngine.Gate.realAllowed(good, true, 0.0, 1).first)
        assertTrue(TradeEngine.Gate.realAllowed(good, true, 0.5, 0).first)
        // a locked score card can never allow a real order, whatever else is true
        assertFalse(TradeEngine.Gate.realAllowed(TradeEngine.card(emptyList()), true, 0.0, 0).first)
    }

    @Test fun cardIgnoresRealTradesWhenScoringDemo() {
        val real = closed(40, 4, 100).map { it.copy(mode = Mode.REAL) }
        assertEquals(0, TradeEngine.card(real, Mode.DEMO).closed)
        assertEquals(40, TradeEngine.card(real, Mode.REAL).closed)
    }

    @Test fun readMt5Screen() {
        val lines = listOf("Quotes", "Demo Account - MetaQuotes-Demo", "Balance: 10 000.00", "XAUUSD", "2 003.45", "2 003.78", "EURUSD", "1.08210")
        val r = TradeEngine.readMt5(lines)
        assertEquals(true, r.demo)
        assertEquals(10000.0, r.balance!!, 0.001)
        assertEquals("XAUUSD", r.symbol)
        assertEquals(2003.45, r.price!!, 0.001)
        // real account text is not demo; unknown is null (callers treat null as NOT demo)
        assertEquals(false, TradeEngine.readMt5(listOf("Real account", "Broker-Live server")).demo)
        assertNull(TradeEngine.readMt5(listOf("Settings", "Chart")).demo)
    }

    @Test fun parseYahooSkipsNullRowsAndBadJson() {
        val j = """{"chart":{"result":[{"timestamp":[1,2,3],"indicators":{"quote":[{"open":[1.0,null,3.0],"high":[2.0,null,4.0],"low":[0.5,null,2.5],"close":[1.5,null,3.5]}]}}]}}"""
        val c = TradeEngine.parseYahoo(j)
        assertEquals(2, c.size); assertEquals(3000L, c[1].t); assertEquals(3.5, c[1].c, 0.0)
        assertTrue(TradeEngine.parseYahoo("not json").isEmpty())
        assertTrue(TradeEngine.parseYahoo("{}").isEmpty())
    }

    @Test fun speakNeverPromises() {
        val s = TradeEngine.Signal("XAUUSD", Dir.BUY, 2000.0, 1990.0, 2015.0, 2025.0, 5, 6.0, 55.0, "UP", listOf("a"), "")
        val t = TradeEngine.speak(s, 0.05).lowercase()
        assertTrue(t.contains("guarantee nahi"))
        assertFalse(t.contains("sure shot") || t.contains("pakka profit"))
        assertTrue(TradeEngine.speak(TradeEngine.analyse("XAUUSD", emptyList()), 0.01).contains("WAIT"))
    }

    // ================= v7.92 FIXES =================

    @Test fun v792ClosedOnlyDropsFormingCandle() {
        val c = listOf(Candle(0L, 1.0, 2.0, 0.5, 1.5), Candle(900_000L, 1.5, 2.5, 1.0, 2.0))
        // now is inside the second candle: it is still forming
        assertEquals(1, TradeEngine.closedOnly(c, 900_000L, 1_000_000L).size)
        // now is after it closed
        assertEquals(2, TradeEngine.closedOnly(c, 900_000L, 1_800_001L).size)
        assertEquals(0, TradeEngine.closedOnly(emptyList(), 900_000L, 5L).size)
    }

    @Test fun v792SpreadCostIsTakenOffEveryResult() {
        val t = Trade("a", 1000L, "XAUUSD", Dir.BUY, 2000.0, 1994.0, 2009.0, 0.01, Mode.DEMO)   // risk 6 -> cost 0.3 / 6 = 0.05R
        assertEquals(0.05, TradeEngine.costR("XAUUSD", 2000.0, 1994.0), 0.001)
        val loss = TradeEngine.settleScaled(t, listOf(Candle(2000, 2000.0, 2001.0, 1990.0, 1991.0)))
        assertEquals(Status.LOSS, loss.status); assertEquals(-1.05, loss.r, 0.001)
    }

    @Test fun v792ScaledExitHalfAtT1ThenBreakevenOrT2() {
        val t = Trade("b", 1000L, "NOSPREAD", Dir.BUY, 100.0, 94.0, 109.0, 0.01, Mode.DEMO)     // r1 = 1.5, r2 = 2.5, tp2 = 115
        // T1 on the first candle, then price comes back to entry -> only the banked half: +0.75R
        val be = TradeEngine.settleScaled(t, listOf(Candle(2000, 100.0, 110.0, 99.0, 108.0), Candle(3000, 108.0, 109.0, 99.5, 100.0)))
        assertEquals(Status.WIN, be.status); assertEquals(0.75, be.r, 0.001)
        // T1 then T2 -> 0.75 + 1.25 = 2.0R
        val t2 = TradeEngine.settleScaled(t, listOf(Candle(2000, 100.0, 110.0, 99.0, 108.0), Candle(3000, 108.0, 116.0, 107.0, 115.0)))
        assertEquals(Status.WIN, t2.status); assertEquals(2.0, t2.r, 0.001)
        // T1 and breakeven inside the SAME later candle: breakeven first (pessimistic)
        val both = TradeEngine.settleScaled(t, listOf(Candle(2000, 100.0, 110.0, 99.0, 108.0), Candle(3000, 108.0, 120.0, 98.0, 110.0)))
        assertEquals(0.75, both.r, 0.001)
        // stop before target stays a full loss, still open when nothing happens
        assertEquals(Status.OPEN, TradeEngine.settleScaled(t, listOf(Candle(2000, 100.0, 105.0, 98.0, 101.0))).status)
        // SELL mirror
        val s = Trade("c", 1000L, "NOSPREAD", Dir.SELL, 100.0, 106.0, 91.0, 0.01, Mode.DEMO)
        val sw = TradeEngine.settleScaled(s, listOf(Candle(2000, 100.0, 101.0, 90.0, 92.0), Candle(3000, 92.0, 85.0, 84.0, 85.0)))
        assertEquals(2.0, sw.r, 0.001)
    }

    @Test fun v792SessionGuard() {
        fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long {
            val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); c.clear(); c.set(y, mo - 1, d, h, mi); return c.timeInMillis
        }
        // 2026-10-21 is a Wednesday
        assertEquals("", TradeEngine.sessionBlock("XAUUSD", utc(2026, 10, 21, 10, 0)))
        assertTrue(TradeEngine.sessionBlock("XAUUSD", utc(2026, 10, 24, 10, 0)).isNotEmpty())     // Saturday
        assertTrue(TradeEngine.sessionBlock("XAUUSD", utc(2026, 10, 23, 22, 0)).isNotEmpty())     // Friday night
        assertTrue(TradeEngine.sessionBlock("EURUSD", utc(2026, 10, 21, 22, 30)).isNotEmpty())    // rollover
        assertTrue(TradeEngine.sessionBlock("EURUSD", utc(2026, 10, 2, 13, 30)).isNotEmpty())     // first Friday = NFP window
        assertEquals("", TradeEngine.sessionBlock("EURUSD", utc(2026, 10, 9, 13, 30)))             // second Friday is normal
        assertEquals("", TradeEngine.sessionBlock("BTCUSD", utc(2026, 10, 24, 10, 0)))             // crypto trades on weekends
    }

    @Test fun v792FiltersTurnBadSignalsIntoWait() {
        val buy = TradeEngine.Signal("XAUUSD", Dir.BUY, 2000.0, 1994.0, 2009.0, 2015.0, 5, 4.0, 55.0, "UP", listOf("x"), "")
        val wed = 1_792_000_000_000L   // some weekday midday UTC is not guaranteed, so test the htf / spread rules with the session guard off
        assertEquals(Dir.WAIT, TradeEngine.applyFilters(buy, "DOWN", wed, true, false).dir)
        assertEquals(Dir.BUY, TradeEngine.applyFilters(buy, "UP", wed, true, false).dir)
        assertTrue(TradeEngine.applyFilters(buy, "UP", wed, true, false).reasons.any { it.contains("sath me") })
        assertEquals(Dir.BUY, TradeEngine.applyFilters(buy, "DOWN", wed, false, false).dir)       // filter switched off
        val sell = buy.copy(dir = Dir.SELL, sl = 2006.0, tp1 = 1991.0, tp2 = 1985.0)
        assertEquals(Dir.WAIT, TradeEngine.applyFilters(sell, "UP", wed, true, false).dir)
        // spread eats more than 20% of the stop -> WAIT
        val tight = buy.copy(sl = 1999.0, tp1 = 2001.5, tp2 = 2002.5)   // risk 1.0, spread 0.30
        assertEquals(Dir.WAIT, TradeEngine.applyFilters(tight, "UP", wed, true, false).dir)
    }

    @Test fun v792HigherTimeframeTrend() {
        val up = (0 until 120).map { i -> Candle(i * 3_600_000L, 100.0 + i, 101.0 + i, 99.0 + i, 100.5 + i) }
        val dn = (0 until 120).map { i -> Candle(i * 3_600_000L, 300.0 - i, 301.0 - i, 299.0 - i, 299.5 - i) }
        assertEquals("UP", TradeEngine.htfTrend(up))
        assertEquals("DOWN", TradeEngine.htfTrend(dn))
        assertEquals("UNKNOWN", TradeEngine.htfTrend(up.take(20)))
    }

    @Test fun v792StaleFeedOnlyWhenMarketOpen() {
        fun utc(d: Int, h: Int): Long { val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); c.clear(); c.set(2026, 9, d, h, 0); return c.timeInMillis }
        val now = utc(21, 12)   // Wednesday noon
        val old = listOf(Candle(now - 10 * 3_600_000L, 1.0, 1.0, 1.0, 1.0))
        assertTrue(TradeEngine.feedStale(old, 900_000L, now, "XAUUSD"))
        val fresh = listOf(Candle(now - 900_000L, 1.0, 1.0, 1.0, 1.0))
        assertFalse(TradeEngine.feedStale(fresh, 900_000L, now, "XAUUSD"))
        assertFalse(TradeEngine.feedStale(old, 900_000L, utc(24, 12), "XAUUSD"))   // Saturday: old candle is normal
    }

    @Test fun v792BackupFeeds() {
        assertTrue(TradeEngine.yahooUrl2("XAUUSD").startsWith("https://query2.finance.yahoo.com/"))
        assertEquals("", TradeEngine.binanceUrl("XAUUSD"))   // only crypto has the Binance backup
        assertTrue(TradeEngine.binanceUrl("BTCUSD", "60m").contains("symbol=BTCUSDT&interval=1h"))
        val j = "[[1700000000000,\"100.5\",\"101\",\"99\",\"100\",\"5\"],[1700000900000,\"100\",\"102\",\"99.5\",\"101.5\",\"6\"]]"
        val c = TradeEngine.parseBinance(j)
        assertEquals(2, c.size); assertEquals(101.5, c[1].c, 0.0001); assertEquals(1700000000000L, c[0].t)
        assertEquals(0, TradeEngine.parseBinance("not json").size)
    }

    @Test fun v792GateNeedsBigSampleAndConfidence() {
        assertEquals(100, TradeEngine.Gate.MIN_TRADES); assertEquals(30, TradeEngine.Gate.MIN_DAYS)
        // 30 perfect-looking trades are no longer enough
        assertFalse(TradeEngine.Gate.unlocked(TradeEngine.card(closed(30, 4, 100))))
        // 120 trades with a clear edge pass
        val good = TradeEngine.card(closed(120, 4, 100))
        assertTrue(TradeEngine.Gate.lowerBoundR(good) > 0.0)
        assertTrue(TradeEngine.Gate.unlocked(good))
        // a lucky-looking mix: same average but huge swings -> lower bound below zero -> still locked
        val wild = (1..100).map { i ->
            val big = i % 2 == 0
            Trade("w$i", i.toLong(), "XAUUSD", Dir.BUY, 100.0, 95.0, 107.5, 0.01, Mode.DEMO, if (big) Status.WIN else Status.LOSS, 0.0, if (big) 9.0 else -8.5, (100 + i / 3) * 86_400_000L)
        }
        val cw = TradeEngine.card(wild)
        assertTrue(cw.expectancy > 0.2)
        assertTrue(TradeEngine.Gate.lowerBoundR(cw) < 0.0)
        assertTrue(TradeEngine.Gate.missing(cw).any { it.contains("bharosa") })
    }
}
