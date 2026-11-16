package com.nova.assistant

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * v7.91 TRADE ENGINE (pure Kotlin, no Android classes, so JUnit can test every rule).
 *
 * What it does: reads real candles, computes EMA / RSI / MACD / ATR / swing levels, and turns them into ONE of BUY / SELL / WAIT
 * with entry, stop loss, two targets and a lot size that risks only a small percent of the balance.
 * It keeps a journal of PRACTICE (demo) trades, closes them against real candles, and builds a score card.
 * REAL money stays locked until the practice score card passes [Gate]. Even then every real trade needs the owner's own tap.
 *
 * Honest limits: no indicator predicts the market. A signal is an idea with a fixed risk, never a promise of profit.
 */
object TradeEngine {

    enum class Dir { BUY, SELL, WAIT }

    data class Candle(val t: Long, val o: Double, val h: Double, val l: Double, val c: Double)

    data class Signal(
        val symbol: String,
        val dir: Dir,
        val entry: Double,
        val sl: Double,
        val tp1: Double,
        val tp2: Double,
        val score: Int,
        val atr: Double,
        val rsi: Double,
        val trend: String,
        val reasons: List<String>,
        val waitWhy: String
    )

    // ------------------------------------------------------------------ indicators

    fun ema(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty() || period < 1) return emptyList()
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double>(values.size)
        var prev = values[0]
        out.add(prev)
        for (i in 1 until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out.add(prev)
        }
        return out
    }

    /** Wilder RSI. Needs period + 1 closes, otherwise 50 (neutral). */
    fun rsi(closes: List<Double>, period: Int = 14): Double {
        if (closes.size <= period) return 50.0
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d >= 0) gain += d else loss -= d
        }
        var ag = gain / period
        var al = loss / period
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            ag = (ag * (period - 1) + max(d, 0.0)) / period
            al = (al * (period - 1) + max(-d, 0.0)) / period
        }
        if (al == 0.0) return if (ag == 0.0) 50.0 else 100.0
        val rs = ag / al
        return 100.0 - 100.0 / (1.0 + rs)
    }

    /** Wilder ATR (average true range). 0 when there are too few candles. */
    fun atr(c: List<Candle>, period: Int = 14): Double {
        if (c.size <= period) return 0.0
        val tr = ArrayList<Double>()
        for (i in 1 until c.size) {
            val a = c[i].h - c[i].l
            val b = abs(c[i].h - c[i - 1].c)
            val d = abs(c[i].l - c[i - 1].c)
            tr.add(max(a, max(b, d)))
        }
        var v = tr.take(period).average()
        for (i in period until tr.size) v = (v * (period - 1) + tr[i]) / period
        return v
    }

    /** Returns (macd line, signal line, histogram) of the last candle plus the histogram of the one before. */
    data class Macd(val line: Double, val signal: Double, val hist: Double, val prevHist: Double)

    fun macd(closes: List<Double>): Macd {
        if (closes.size < 35) return Macd(0.0, 0.0, 0.0, 0.0)
        val e12 = ema(closes, 12)
        val e26 = ema(closes, 26)
        val line = closes.indices.map { e12[it] - e26[it] }
        val sig = ema(line, 9)
        val n = closes.size
        return Macd(line[n - 1], sig[n - 1], line[n - 1] - sig[n - 1], line[n - 2] - sig[n - 2])
    }

    /** Nearest swing low below [price] and swing high above it, from the last [look] candles (3-candle pivots). */
    fun levels(c: List<Candle>, price: Double, look: Int = 120): Pair<Double?, Double?> {
        val s = if (c.size > look) c.subList(c.size - look, c.size) else c
        var sup: Double? = null
        var res: Double? = null
        for (i in 2 until s.size - 2) {
            val lo = s[i].l
            val hi = s[i].h
            if (lo < s[i - 1].l && lo < s[i - 2].l && lo < s[i + 1].l && lo < s[i + 2].l && lo < price) {
                if (sup == null || lo > sup) sup = lo
            }
            if (hi > s[i - 1].h && hi > s[i - 2].h && hi > s[i + 1].h && hi > s[i + 2].h && hi > price) {
                if (res == null || hi < res) res = hi
            }
        }
        return Pair(sup, res)
    }

    // ------------------------------------------------------------------ signal

    /** Minimum candles needed: EMA 50 + MACD warm-up. */
    const val MIN_CANDLES = 60

    /** Price rounding the way MT5 shows it. */
    fun round(symbol: String, v: Double): Double {
        val d = Spec.of(symbol).digits
        var m = 1.0
        repeat(d) { m *= 10.0 }
        return Math.round(v * m) / m
    }

    fun analyse(symbol: String, c: List<Candle>, rr1: Double = 1.5, rr2: Double = 2.5): Signal {
        val sym = Spec.canon(symbol)
        if (c.size < MIN_CANDLES) return wait(sym, c.lastOrNull()?.c ?: 0.0, "Itna data nahi mila (" + c.size + " candle).")
        val closes = c.map { it.c }
        val last = closes.last()
        val e20 = ema(closes, 20)
        val e50 = ema(closes, 50)
        val a = atr(c)
        if (a <= 0.0) return wait(sym, last, "Market chal nahi raha (ATR 0).")
        val r = rsi(closes)
        val m = macd(closes)
        val n = closes.size
        // a real trend needs the two averages clearly apart (0.3 ATR), otherwise it is just noise around a flat price
        val gap = e20[n - 1] - e50[n - 1]
        val strong = abs(gap) >= 0.3 * a
        val up = strong && gap > 0 && last > e50[n - 1]
        val down = strong && gap < 0 && last < e50[n - 1]
        val slopeUp = e20[n - 1] > e20[n - 6]
        val slopeDn = e20[n - 1] < e20[n - 6]
        val trend = if (up && slopeUp) "UP" else if (down && slopeDn) "DOWN" else "SIDEWAYS"

        var buy = 0
        var sell = 0
        val rb = ArrayList<String>()
        val rs = ArrayList<String>()
        if (up && slopeUp) { buy += 2; rb.add("EMA20 > EMA50, trend upar") }
        if (down && slopeDn) { sell += 2; rs.add("EMA20 < EMA50, trend neeche") }
        if (m.hist > 0 && m.hist > m.prevHist) { buy += 1; rb.add("MACD momentum upar") }
        if (m.hist < 0 && m.hist < m.prevHist) { sell += 1; rs.add("MACD momentum neeche") }
        if (r in 45.0..65.0) { buy += 1; rb.add("RSI " + fmt1(r) + " healthy") }
        if (r in 35.0..55.0) { sell += 1; rs.add("RSI " + fmt1(r) + " healthy") }
        // Pullback entry is better than chasing: price close to EMA20 (within 1 ATR)
        val nearE20 = abs(last - e20[n - 1]) <= a
        if (nearE20 && up) { buy += 1; rb.add("price EMA20 ke paas (pullback)") }
        if (nearE20 && down) { sell += 1; rs.add("price EMA20 ke paas (pullback)") }

        val (sup, res) = levels(c, last)
        // do not buy right under a wall / sell right above a floor
        val wallUp = res != null && res - last < 1.0 * a
        val wallDn = sup != null && last - sup < 1.0 * a
        if (wallUp) { buy -= 2; rb.add("upar resistance bahut paas (-2)") }
        if (wallDn) { sell -= 2; rs.add("neeche support bahut paas (-2)") }
        if (r > 72) { buy -= 2; rb.add("RSI overbought (-2)") }
        if (r < 28) { sell -= 2; rs.add("RSI oversold (-2)") }

        val need = 4
        val dir = when {
            buy >= need && buy > sell + 1 -> Dir.BUY
            sell >= need && sell > buy + 1 -> Dir.SELL
            else -> Dir.WAIT
        }
        if (dir == Dir.WAIT) {
            val why = if (trend == "SIDEWAYS") "Market sideways hai, saaf trend nahi" else "Signal kamzor hai (buy " + buy + " / sell " + sell + ", chahiye " + need + ")"
            return Signal(sym, Dir.WAIT, round(sym, last), 0.0, 0.0, 0.0, max(buy, sell), a, r, trend, emptyList(), why)
        }
        val risk = 1.5 * a
        val entry = last
        val sl: Double
        val t1: Double
        val t2: Double
        if (dir == Dir.BUY) {
            var s = entry - risk
            // put the stop just under a real swing low if that is within 2.5 ATR (not tighter than 1 ATR)
            if (sup != null && entry - sup in a..(2.5 * a)) s = min(s, sup - 0.2 * a)
            sl = s
            val rk = entry - sl
            t1 = entry + rr1 * rk
            t2 = entry + rr2 * rk
        } else {
            var s = entry + risk
            if (res != null && res - entry in a..(2.5 * a)) s = max(s, res + 0.2 * a)
            sl = s
            val rk = sl - entry
            t1 = entry - rr1 * rk
            t2 = entry - rr2 * rk
        }
        val reasons = if (dir == Dir.BUY) rb else rs
        return Signal(sym, dir, round(sym, entry), round(sym, sl), round(sym, t1), round(sym, t2), if (dir == Dir.BUY) buy else sell, a, r, trend, reasons, "")
    }

    private fun wait(sym: String, last: Double, why: String) = Signal(sym, Dir.WAIT, last, 0.0, 0.0, 0.0, 0, 0.0, 50.0, "SIDEWAYS", emptyList(), why)

    private fun fmt1(v: Double) = (Math.round(v * 10.0) / 10.0).toString()

    /** MT5 (broker) price and the free data feed differ by a few points. Shift every level by the difference so the numbers match the MT5 screen. */
    fun calibrate(s: Signal, mt5Price: Double): Signal {
        if (s.dir == Dir.WAIT || mt5Price <= 0.0) return s
        val off = mt5Price - s.entry
        // a huge gap means the wrong symbol / stale feed: do not shift, flag by returning WAIT
        if (abs(off) > 8.0 * max(s.atr, 1e-9)) return s.copy(dir = Dir.WAIT, sl = 0.0, tp1 = 0.0, tp2 = 0.0, reasons = emptyList(), waitWhy = "MT5 ka bhav aur data ka bhav bahut alag hai, symbol check karo")
        return s.copy(entry = round(s.symbol, s.entry + off), sl = round(s.symbol, s.sl + off), tp1 = round(s.symbol, s.tp1 + off), tp2 = round(s.symbol, s.tp2 + off))
    }

    // ------------------------------------------------------------------ symbols, lots

    /** contract = money per 1.0 price unit per 1.0 lot (USD account). digits = price decimals. These are common MT5 defaults; brokers can differ. */
    data class Spec(val yahoo: String, val contract: Double, val digits: Int) {
        companion object {
            private val T: Map<String, Spec> = mapOf(
                "XAUUSD" to Spec("GC=F", 100.0, 2),
                "XAGUSD" to Spec("SI=F", 5000.0, 3),
                "EURUSD" to Spec("EURUSD=X", 100000.0, 5),
                "GBPUSD" to Spec("GBPUSD=X", 100000.0, 5),
                "AUDUSD" to Spec("AUDUSD=X", 100000.0, 5),
                "USDJPY" to Spec("USDJPY=X", 100000.0 / 150.0, 3),
                "USDCAD" to Spec("USDCAD=X", 100000.0 / 1.37, 5),
                "USDCHF" to Spec("USDCHF=X", 100000.0 / 0.88, 5),
                "BTCUSD" to Spec("BTC-USD", 1.0, 2),
                "ETHUSD" to Spec("ETH-USD", 1.0, 2),
                "USOIL" to Spec("CL=F", 1000.0, 2),
                "US30" to Spec("^DJI", 1.0, 1),
                "NAS100" to Spec("^NDX", 1.0, 1),
                "US500" to Spec("^GSPC", 1.0, 1)
            )
            private val ALIAS: Map<String, String> = mapOf(
                "GOLD" to "XAUUSD", "XAU" to "XAUUSD", "SONA" to "XAUUSD", "SILVER" to "XAGUSD", "XAG" to "XAGUSD", "CHANDI" to "XAGUSD",
                "BTC" to "BTCUSD", "BITCOIN" to "BTCUSD", "ETH" to "ETHUSD", "ETHEREUM" to "ETHUSD",
                "OIL" to "USOIL", "CRUDE" to "USOIL", "WTI" to "USOIL", "XTIUSD" to "USOIL",
                "DOW" to "US30", "DJI" to "US30", "NASDAQ" to "NAS100", "US100" to "NAS100", "SP500" to "US500", "SPX" to "US500",
                "GOLDUSD" to "XAUUSD", "XAUUSDM" to "XAUUSD", "EURUSDM" to "EURUSD", "GBPUSDM" to "GBPUSD"
            )
            val all: List<String> get() = T.keys.toList()
            /** "gold", "XAU/USD", "eurusd.m" -> a table key, or "" when unknown. */
            fun canon(s: String): String {
                val u = s.uppercase().replace(Regex("[^A-Z0-9]"), "")
                if (u.isEmpty()) return ""
                if (T.containsKey(u)) return u
                ALIAS[u]?.let { return it }
                // broker suffixes like EURUSDM, EURUSDPRO, XAUUSDMICRO
                for (k in T.keys) if (u.startsWith(k) && u.length <= k.length + 5) return k
                return ""
            }
            fun of(s: String): Spec = T[canon(s)] ?: Spec("", 1.0, 5)
        }
    }

    /** Lot size so that a stop-loss hit costs about [riskPct] percent of [balance]. Rounded DOWN to 0.01, at least 0.01, at most [maxLot]. */
    fun lots(symbol: String, balance: Double, riskPct: Double, entry: Double, sl: Double, maxLot: Double = 1.0): Double {
        val dist = abs(entry - sl)
        val contract = Spec.of(symbol).contract
        if (balance <= 0.0 || dist <= 0.0 || contract <= 0.0) return 0.01
        val riskMoney = balance * (riskPct.coerceIn(0.1, 2.0) / 100.0)
        val raw = riskMoney / (dist * contract)
        val l = floor(raw * 100.0) / 100.0
        return max(0.01, min(maxLot, l))
    }

    // ------------------------------------------------------------------ journal + score card

    enum class Mode { DEMO, REAL }
    enum class Status { OPEN, WIN, LOSS, EVEN }

    data class Trade(
        val id: String,
        val ts: Long,
        val symbol: String,
        val dir: Dir,
        val entry: Double,
        val sl: Double,
        val tp1: Double,
        val lots: Double,
        val mode: Mode,
        val status: Status = Status.OPEN,
        val exit: Double = 0.0,
        val r: Double = 0.0,          // result in R: +1.5 = reached target 1, -1 = stop loss
        val closedTs: Long = 0L
    )

    /**
     * Close an OPEN trade against the candles that came AFTER it was opened. Stop loss is checked first inside the same candle
     * (the pessimistic order), so the practice result is never better than reality. Candles must be newer than [Trade.ts].
     * Returns the same trade when it is still open.
     */
    fun settle(t: Trade, after: List<Candle>, maxAgeMs: Long = 3L * 24 * 3600 * 1000, now: Long = 0L): Trade {
        if (t.status != Status.OPEN) return t
        val risk = abs(t.entry - t.sl)
        if (risk <= 0.0) return t
        for (k in after) {
            if (k.t <= t.ts) continue
            val hitSl = if (t.dir == Dir.BUY) k.l <= t.sl else k.h >= t.sl
            val hitTp = if (t.dir == Dir.BUY) k.h >= t.tp1 else k.l <= t.tp1
            if (hitSl) return t.copy(status = Status.LOSS, exit = t.sl, r = -1.0, closedTs = k.t)
            if (hitTp) return t.copy(status = Status.WIN, exit = t.tp1, r = abs(t.tp1 - t.entry) / risk, closedTs = k.t)
        }
        if (now > 0L && now - t.ts > maxAgeMs && after.isNotEmpty()) {
            val px = after.last().c
            val rr = (if (t.dir == Dir.BUY) px - t.entry else t.entry - px) / risk
            return t.copy(status = Status.EVEN, exit = px, r = Math.round(rr * 100.0) / 100.0, closedTs = after.last().t)
        }
        return t
    }

    data class Card(
        val closed: Int,
        val wins: Int,
        val losses: Int,
        val winRate: Double,
        val expectancy: Double,
        val profitFactor: Double,
        val maxDrawdownR: Double,
        val last10R: Double,
        val totalR: Double,
        val activeDays: Int,
        val open: Int,
        val sdR: Double = 0.0          // v7.92: spread of the per-trade R results (for the confidence check)
    )

    fun card(trades: List<Trade>, mode: Mode = Mode.DEMO): Card {
        val all = trades.filter { it.mode == mode }
        val closed = all.filter { it.status != Status.OPEN }.sortedBy { it.closedTs }
        val wins = closed.count { it.r > 0.0 }
        val losses = closed.count { it.r < 0.0 }
        val sumPos = closed.filter { it.r > 0 }.sumOf { it.r }
        val sumNeg = closed.filter { it.r < 0 }.sumOf { -it.r }
        val pf = if (sumNeg == 0.0) (if (sumPos > 0.0) 99.0 else 0.0) else sumPos / sumNeg
        var eq = 0.0
        var peak = 0.0
        var dd = 0.0
        for (t in closed) {
            eq += t.r
            peak = max(peak, eq)
            dd = max(dd, peak - eq)
        }
        val tot = closed.sumOf { it.r }
        val l10 = closed.takeLast(10).sumOf { it.r }
        val days = closed.map { it.closedTs / 86_400_000L }.toSet().size
        val wr = if (closed.isEmpty()) 0.0 else wins * 100.0 / closed.size
        val ex = if (closed.isEmpty()) 0.0 else tot / closed.size
        val sdv = if (closed.size < 2) 0.0 else sqrt(closed.sumOf { (it.r - ex) * (it.r - ex) } / (closed.size - 1))
        return Card(closed.size, wins, losses, round2(wr), round2(ex), round2(pf), round2(dd), round2(l10), round2(tot), days, all.count { it.status == Status.OPEN }, round2(sdv))
    }

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0

    // ------------------------------------------------------------------ real-money gate

    /** The owner said: real trades only when practice is "perfect". These are the numbers that count as perfect. Honest: past demo results do not guarantee the future. */
    object Gate {
        const val MIN_TRADES = 100           // v7.92: was 30 (too small, luck can look perfect)
        const val MIN_DAYS = 30              // v7.92: was 10
        const val MIN_LOWER_BOUND_R = 0.0    // v7.92: average R minus one standard error must still be above this
        const val MIN_EXPECTANCY = 0.20      // average R per trade
        const val MIN_PROFIT_FACTOR = 1.30
        const val MAX_DRAWDOWN_R = 8.0
        const val MIN_LAST10_R = 0.0         // the most recent 10 must not be losing
        const val REAL_RISK_PCT = 0.5        // real trades risk half of what the demo risks, at most
        const val MAX_REAL_LOSS_R_PER_DAY = 2.0
        const val MAX_REAL_OPEN = 1

        /** Empty list = unlocked. Otherwise one Hinglish line per missing condition. */
        fun missing(c: Card): List<String> {
            val m = ArrayList<String>()
            if (c.closed < MIN_TRADES) m.add("Practice trades " + c.closed + "/" + MIN_TRADES)
            if (c.activeDays < MIN_DAYS) m.add("Alag din " + c.activeDays + "/" + MIN_DAYS)
            if (c.expectancy < MIN_EXPECTANCY) m.add("Average " + c.expectancy + "R, chahiye " + MIN_EXPECTANCY + "R")
            if (c.profitFactor < MIN_PROFIT_FACTOR) m.add("Profit factor " + c.profitFactor + ", chahiye " + MIN_PROFIT_FACTOR)
            if (c.maxDrawdownR > MAX_DRAWDOWN_R) m.add("Sabse bada girna " + c.maxDrawdownR + "R, limit " + MAX_DRAWDOWN_R + "R")
            if (c.last10R < MIN_LAST10_R) m.add("Pichhle 10 trade negative (" + c.last10R + "R)")
            if (c.closed >= 2 && lowerBoundR(c) < MIN_LOWER_BOUND_R) m.add("Average ka bharosa kam (" + lowerBoundR(c) + "R), result kismat bhi ho sakta hai")
            return m
        }

        fun unlocked(c: Card) = missing(c).isEmpty()

        /** v7.92: average R minus one standard error. Positive = the practice edge is not just a lucky streak (still no guarantee). */
        fun lowerBoundR(c: Card): Double = if (c.closed < 2) 0.0 else Math.round((c.expectancy - c.sdR / sqrt(c.closed.toDouble())) * 100.0) / 100.0

        /** Can a REAL order be sent right now? Needs unlock + the owner's own confirm + daily loss / open limits. */
        fun realAllowed(c: Card, ownerConfirmed: Boolean, todayRealLossR: Double, realOpen: Int): Pair<Boolean, String> {
            if (!unlocked(c)) return Pair(false, "Real trade abhi locked hai: " + missing(c).joinToString("; "))
            if (!ownerConfirmed) return Pair(false, "Real trade ke liye aapki haan (confirm) chahiye.")
            if (todayRealLossR >= MAX_REAL_LOSS_R_PER_DAY) return Pair(false, "Aaj ka loss limit (" + MAX_REAL_LOSS_R_PER_DAY + "R) poora ho gaya, kal dekhenge.")
            if (realOpen >= MAX_REAL_OPEN) return Pair(false, "Ek real trade pehle se khula hai.")
            return Pair(true, "")
        }
    }

    // ------------------------------------------------------------------ MT5 screen reading (text lines from Accessibility)

    data class Mt5Read(val demo: Boolean?, val balance: Double?, val symbol: String, val price: Double?, val lines: Int)

    private val NUM = Regex("-?\\d[\\d,\\s]*\\.?\\d*")

    fun parseNumber(s: String): Double? {
        val m = NUM.find(s.replace(" ", " ")) ?: return null
        val t = m.value.replace(",", "").replace(" ", "")
        return t.toDoubleOrNull()
    }

    /**
     * Best-effort read of the MT5 Android screen text. demo = true when the account line says Demo, false when it says Real/Live, null when unknown
     * (unknown is treated as NOT demo by every caller). Prices are the first plausible number after a symbol on a quote row.
     */
    fun readMt5(lines: List<String>): Mt5Read {
        var demo: Boolean? = null
        var bal: Double? = null
        var sym = ""
        var px: Double? = null
        for (i in lines.indices) {
            val l = lines[i]
            val lo = l.lowercase()
            if (demo == null) {
                if (lo.contains("demo")) demo = true
                else if (Regex("\\b(real|live)\\b").containsMatchIn(lo) && (lo.contains("account") || lo.contains("server") || lo.contains("-real") || lo.contains("-live"))) demo = false
            }
            if (bal == null && (lo.startsWith("balance") || lo.startsWith("equity"))) {
                bal = parseNumber(l.substringAfter(':', l.substring(min(l.length, 7))))
                if (bal == null && i + 1 < lines.size) bal = parseNumber(lines[i + 1])
            }
            if (sym.isEmpty()) {
                val tok = l.trim().split(Regex("[\\s,]+")).firstOrNull() ?: ""
                val c = Spec.canon(tok)
                if (c.isNotEmpty() && tok.length in 5..10) {
                    sym = c
                    // quote rows show bid and ask in the next lines
                    for (j in i + 1 until min(lines.size, i + 6)) {
                        val v = parseNumber(lines[j])
                        if (v != null && v > 0.0 && lines[j].contains('.')) { px = v; break }
                    }
                }
            }
        }
        return Mt5Read(demo, bal, sym, px, lines.size)
    }

    // ------------------------------------------------------------------ candle parsing (Yahoo chart JSON), kept here so it can be tested

    /** Parses the Yahoo Finance chart JSON text into candles, skipping null rows. Returns empty on any problem. */
    fun parseYahoo(json: String): List<Candle> {
        return try {
            val root = org.json.JSONObject(json)
            val res = root.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
            val ts = res.getJSONArray("timestamp")
            val q = res.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
            val o = q.getJSONArray("open")
            val h = q.getJSONArray("high")
            val l = q.getJSONArray("low")
            val c = q.getJSONArray("close")
            val out = ArrayList<Candle>()
            for (i in 0 until ts.length()) {
                if (o.isNull(i) || h.isNull(i) || l.isNull(i) || c.isNull(i)) continue
                out.add(Candle(ts.getLong(i) * 1000L, o.getDouble(i), h.getDouble(i), l.getDouble(i), c.getDouble(i)))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun yahooUrl(symbol: String, interval: String = "15m", range: String = "5d"): String {
        val y = Spec.of(symbol).yahoo
        if (y.isEmpty()) return ""
        val iv = if (interval in setOf("5m", "15m", "30m", "60m", "1d")) interval else "15m"
        val rg = if (range in setOf("1d", "5d", "1mo", "3mo")) range else "5d"
        return "https://query1.finance.yahoo.com/v8/finance/chart/" + java.net.URLEncoder.encode(y, "UTF-8") + "?interval=" + iv + "&range=" + rg
    }

    /** Spoken / shown text for one signal. No guarantee words. */
    fun speak(s: Signal, lot: Double, en: Boolean = false): String {
        if (s.dir == Dir.WAIT) return s.symbol + ": WAIT. " + s.waitWhy + "."
        val d = if (s.dir == Dir.BUY) "BUY" else "SELL"
        return s.symbol + " " + d + ". Entry " + s.entry + ", Stop Loss " + s.sl + ", Target1 " + s.tp1 + ", Target2 " + s.tp2 + ". Lot " + lot +
            ". Wajah: " + s.reasons.joinToString(", ") + ". Ye analysis hai, guarantee nahi."
    }


    // ================================================================== v7.92 FIXES (add-only: nothing above was removed)

    const val RR1 = 1.5
    const val RR2 = 2.5

    fun intervalMs(iv: String): Long = when (iv) { "5m" -> 300_000L; "15m" -> 900_000L; "30m" -> 1_800_000L; "60m" -> 3_600_000L; "1d" -> 86_400_000L; else -> 900_000L }

    /** FIX 1: the newest candle from the feed is still FORMING (its high / low / close keep moving), so a signal on it can flip a minute later.
     *  Drop it when it has not finished yet; indicators then run only on finished candles. */
    fun closedOnly(c: List<Candle>, intervalMs: Long, now: Long): List<Candle> {
        if (c.isEmpty() || now <= 0L || intervalMs <= 0L) return c
        return if (c.last().t + intervalMs > now) ArrayList(c.subList(0, c.size - 1)) else c
    }

    /** FIX 2: typical MT5 retail spread in PRICE units (brokers differ; this is a fair default so practice is not too rosy). */
    fun spread(symbol: String): Double = when (Spec.canon(symbol)) {
        "XAUUSD" -> 0.30; "XAGUSD" -> 0.03; "EURUSD" -> 0.00012; "GBPUSD" -> 0.00015; "AUDUSD" -> 0.00015
        "USDJPY" -> 0.015; "USDCAD" -> 0.00018; "USDCHF" -> 0.00018; "BTCUSD" -> 25.0; "ETHUSD" -> 2.0
        "USOIL" -> 0.04; "US30" -> 3.0; "NAS100" -> 1.5; "US500" -> 0.6; else -> 0.0
    }

    /** Cost of the spread in R (1R = the stop distance). Subtracted from every closed practice trade. */
    fun costR(symbol: String, entry: Double, sl: Double): Double {
        val risk = abs(entry - sl)
        return if (risk <= 0.0) 0.0 else Math.round(spread(symbol) / risk * 1000.0) / 1000.0
    }

    /**
     * FIX 3: honest exit model = half at Target 1, stop moves to entry (breakeven), the other half runs to Target 2.
     * Pessimistic inside one candle (stop / breakeven is checked before a target) and the spread cost is taken off.
     * Target 2 is derived from Target 1 with the same ratio the signal uses (2.5R / 1.5R), so old stored trades work.
     */
    fun settleScaled(t: Trade, after: List<Candle>, maxAgeMs: Long = 3L * 24 * 3600 * 1000, now: Long = 0L): Trade {
        if (t.status != Status.OPEN) return t
        val risk = abs(t.entry - t.sl)
        if (risk <= 0.0) return t
        val buy = t.dir == Dir.BUY
        val r1 = abs(t.tp1 - t.entry) / risk
        val r2 = r1 * (RR2 / RR1)
        val tp2 = if (buy) t.entry + r2 * risk else t.entry - r2 * risk
        val cost = costR(t.symbol, t.entry, t.sl)
        var phase = 1
        fun done(k: Candle, exit: Double, rr: Double): Trade {
            val v = Math.round(rr * 100.0) / 100.0
            return t.copy(status = if (v > 0.0) Status.WIN else if (v < 0.0) Status.LOSS else Status.EVEN, exit = exit, r = v, closedTs = k.t)
        }
        for (k in after) {
            if (k.t <= t.ts) continue
            if (phase == 1) {
                val hitSl = if (buy) k.l <= t.sl else k.h >= t.sl
                val hitTp = if (buy) k.h >= t.tp1 else k.l <= t.tp1
                if (hitSl) return done(k, t.sl, -1.0 - cost)
                if (hitTp) { phase = 2; continue }          // half is banked; this candle is not used again (pessimistic)
            } else {
                val hitBe = if (buy) k.l <= t.entry else k.h >= t.entry
                val hitT2 = if (buy) k.h >= tp2 else k.l <= tp2
                if (hitBe) return done(k, t.entry, 0.5 * r1 - cost)
                if (hitT2) return done(k, tp2, 0.5 * r1 + 0.5 * r2 - cost)
            }
        }
        if (now > 0L && now - t.ts > maxAgeMs && after.isNotEmpty()) {
            val k = after.last()
            val rr = (if (buy) k.c - t.entry else t.entry - k.c) / risk
            return done(k, k.c, (if (phase == 2) 0.5 * r1 + 0.5 * rr else rr) - cost)
        }
        return t
    }

    /** FIX 4a: trend of a BIGGER timeframe (1 hour candles): UP / DOWN / SIDEWAYS, or UNKNOWN when there is too little data. */
    fun htfTrend(c: List<Candle>): String {
        if (c.size < 55) return "UNKNOWN"
        val cl = c.map { it.c }
        val a = atr(c)
        if (a <= 0.0) return "UNKNOWN"
        val e20 = ema(cl, 20)
        val e50 = ema(cl, 50)
        val n = cl.size
        val gap = e20[n - 1] - e50[n - 1]
        return if (abs(gap) < 0.3 * a) "SIDEWAYS" else if (gap > 0 && cl[n - 1] > e50[n - 1]) "UP" else if (gap < 0 && cl[n - 1] < e50[n - 1]) "DOWN" else "SIDEWAYS"
    }

    fun isCrypto(symbol: String): Boolean = Spec.canon(symbol).let { it == "BTCUSD" || it == "ETHUSD" }

    /**
     * FIX 4b: session / news guard (UTC clock). Returns "" when trading is fine, otherwise the Hinglish reason for WAIT.
     * Weekend close, the daily broker rollover (wide spreads) and the monthly Non-Farm Payroll window (first Friday).
     * Honest limit: there is NO live news calendar here, so FOMC / CPI / surprise news are NOT covered. Crypto is not blocked.
     */
    fun sessionBlock(symbol: String, nowMs: Long): String {
        if (nowMs <= 0L || isCrypto(symbol)) return ""
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = nowMs
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)           // Sunday = 1 ... Saturday = 7
        val mins = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val dom = cal.get(java.util.Calendar.DAY_OF_MONTH)
        if (dow == 7 || (dow == 6 && mins >= 21 * 60) || (dow == 1 && mins < 22 * 60)) return "Market band hai (weekend)"
        if (mins in (21 * 60 + 55)..(23 * 60 + 5)) return "Din ka rollover time hai, spread bahut bada rehta hai"
        if (dow == 6 && dom <= 7 && mins in (12 * 60)..(15 * 60)) return "Aaj Non-Farm Payroll jaisi badi news ka time hai, abhi trade mat lo"
        return ""
    }

    /** FIX 5: stale feed = the newest finished candle is much older than it should be while the market is open. */
    fun feedStale(c: List<Candle>, intervalMs: Long, now: Long, symbol: String): Boolean {
        if (c.isEmpty() || now <= 0L || intervalMs >= 86_400_000L) return false
        if (sessionBlock(symbol, now).isNotEmpty()) return false     // closed / rollover: an old candle is normal
        return now - (c.last().t + intervalMs) > 3 * intervalMs
    }

    fun toWait(s: Signal, why: String) = s.copy(dir = Dir.WAIT, sl = 0.0, tp1 = 0.0, tp2 = 0.0, reasons = emptyList(), waitWhy = why)

    /** FIX 4 + 2: turn a BUY / SELL into WAIT when the session is closed, the spread eats too much of the stop, or the 1 hour trend is against it. */
    fun applyFilters(s: Signal, htf: String, now: Long, useHtf: Boolean = true, useSession: Boolean = true): Signal {
        if (s.dir == Dir.WAIT) return s
        if (useSession) {
            val b = sessionBlock(s.symbol, now)
            if (b.isNotEmpty()) return toWait(s, b)
        }
        val risk = abs(s.entry - s.sl)
        val sp = spread(s.symbol)
        if (risk > 0.0 && sp > 0.0 && sp / risk > 0.20) return toWait(s, "Spread stop loss ke muqable bahut bada hai, trade ka kharcha zyada padega")
        if (useHtf) {
            if (s.dir == Dir.BUY && htf == "DOWN") return toWait(s, "1 ghante ka bada trend neeche hai, BUY us ke ulta hai")
            if (s.dir == Dir.SELL && htf == "UP") return toWait(s, "1 ghante ka bada trend upar hai, SELL us ke ulta hai")
            if (htf == "UP" || htf == "DOWN") return s.copy(reasons = s.reasons + ("1 ghante ka trend " + htf + " (sath me)"))
        }
        return s
    }

    // ---- backup data feeds (FIX 6): Yahoo mirror host, and Binance public candles for crypto

    fun yahooUrl2(symbol: String, interval: String = "15m", range: String = "5d"): String = yahooUrl(symbol, interval, range).replace("query1.", "query2.")

    fun binanceUrl(symbol: String, interval: String = "15m", limit: Int = 300): String {
        val b = when (Spec.canon(symbol)) { "BTCUSD" -> "BTCUSDT"; "ETHUSD" -> "ETHUSDT"; else -> "" }
        if (b.isEmpty()) return ""
        val iv = when (interval) { "5m" -> "5m"; "30m" -> "30m"; "60m" -> "1h"; "1d" -> "1d"; else -> "15m" }
        return "https://api.binance.com/api/v3/klines?symbol=" + b + "&interval=" + iv + "&limit=" + limit.coerceIn(60, 1000)
    }

    fun parseBinance(json: String): List<Candle> {
        return try {
            val a = org.json.JSONArray(json)
            val out = ArrayList<Candle>()
            for (i in 0 until a.length()) {
                val k = a.getJSONArray(i)
                out.add(Candle(k.getLong(0), k.getString(1).toDouble(), k.getString(2).toDouble(), k.getString(3).toDouble(), k.getString(4).toDouble()))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Suppress("unused")
    private fun sd(v: List<Double>): Double {
        if (v.isEmpty()) return 0.0
        val m = v.average()
        return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
    }
}
