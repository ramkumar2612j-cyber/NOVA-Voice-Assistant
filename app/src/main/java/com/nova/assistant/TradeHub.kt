package com.nova.assistant

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * v7.91 TRADE HUB (Android side of [TradeEngine]). Worker threads only (network + Accessibility waits).
 *
 *  - fetch(): real candles over HTTPS (free public chart feed), nothing is sent about the owner.
 *  - analyse(): signal + lot + (when MT5 is on screen) levels shifted to the MT5 price.
 *  - practice journal: every idea the owner accepts is stored as a DEMO trade and closed later against real candles.
 *  - sendToMt5(): opens MT5, REFUSES unless the account line says Demo (or, for REAL, the gate is open AND the owner confirmed in the NOVA screen),
 *    then fills volume / SL / TP and taps market Buy / Sell. Every stage is checked; any surprise stops the flow and says where.
 *
 * Honest status: the engine is unit tested. The MT5 screen driver has NOT been run on a real phone (MT5 screen texts differ by version / language).
 */
object TradeHub {
    private const val PREFS = "nova_trade_v791"
    private const val K_TRADES = "trades"
    private const val K_RISK = "risk"
    private const val K_BAL = "balance"
    private const val K_SYMS = "symbols"
    private const val MAX_TRADES = 400

    val DEFAULT_SYMBOLS = listOf("XAUUSD", "EURUSD", "GBPUSD", "USDJPY", "BTCUSD")

    // ---------------------------------------------------------------- settings

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun risk(ctx: Context): Double = sp(ctx).getFloat(K_RISK, 1.0f).toDouble().coerceIn(0.1, 2.0)
    fun setRisk(ctx: Context, v: Double) { sp(ctx).edit().putFloat(K_RISK, v.coerceIn(0.1, 2.0).toFloat()).apply() }
    fun balance(ctx: Context): Double = sp(ctx).getFloat(K_BAL, 10000f).toDouble()
    fun setBalance(ctx: Context, v: Double) { if (v > 0.0 && v < 1e9) sp(ctx).edit().putFloat(K_BAL, v.toFloat()).apply() }

    fun symbols(ctx: Context): List<String> {
        val raw = sp(ctx).getString(K_SYMS, null) ?: return DEFAULT_SYMBOLS
        val l = raw.split(",").map { TradeEngine.Spec.canon(it) }.filter { it.isNotEmpty() }.distinct()
        return if (l.isEmpty()) DEFAULT_SYMBOLS else l.take(12)
    }
    fun setSymbols(ctx: Context, csv: String) {
        val l = csv.split(",", " ").map { TradeEngine.Spec.canon(it) }.filter { it.isNotEmpty() }.distinct().take(12)
        sp(ctx).edit().putString(K_SYMS, l.joinToString(",")).apply()
    }

    // ---------------------------------------------------------------- journal

    @Synchronized fun trades(ctx: Context): MutableList<TradeEngine.Trade> {
        val raw = sp(ctx).getString(K_TRADES, null) ?: return mutableListOf()
        return try {
            val a = JSONArray(raw)
            val out = ArrayList<TradeEngine.Trade>()
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                out.add(TradeEngine.Trade(
                    o.optString("id"), o.optLong("ts"), o.optString("sym"),
                    runCatching { TradeEngine.Dir.valueOf(o.optString("dir")) }.getOrDefault(TradeEngine.Dir.WAIT),
                    o.optDouble("entry"), o.optDouble("sl"), o.optDouble("tp1"), o.optDouble("lots", 0.01),
                    runCatching { TradeEngine.Mode.valueOf(o.optString("mode")) }.getOrDefault(TradeEngine.Mode.DEMO),
                    runCatching { TradeEngine.Status.valueOf(o.optString("st")) }.getOrDefault(TradeEngine.Status.OPEN),
                    o.optDouble("exit", 0.0), o.optDouble("r", 0.0), o.optLong("cts", 0L)
                ))
            }
            out
        } catch (e: Exception) { mutableListOf() }
    }

    @Synchronized private fun save(ctx: Context, list: List<TradeEngine.Trade>) {
        val a = JSONArray()
        for (t in list.takeLast(MAX_TRADES)) {
            a.put(JSONObject().put("id", t.id).put("ts", t.ts).put("sym", t.symbol).put("dir", t.dir.name).put("entry", t.entry).put("sl", t.sl)
                .put("tp1", t.tp1).put("lots", t.lots).put("mode", t.mode.name).put("st", t.status.name).put("exit", t.exit).put("r", t.r).put("cts", t.closedTs))
        }
        sp(ctx).edit().putString(K_TRADES, a.toString()).apply()
    }

    @Synchronized fun resetPractice(ctx: Context) {
        save(ctx, trades(ctx).filter { it.mode != TradeEngine.Mode.DEMO })
    }

    /** Store one practice trade from a BUY/SELL signal. One open practice trade per symbol: returns false when one is already open. */
    @Synchronized fun openPractice(ctx: Context, s: TradeEngine.Signal, lots: Double): Boolean {
        if (s.dir == TradeEngine.Dir.WAIT) return false
        val l = trades(ctx)
        if (l.any { it.status == TradeEngine.Status.OPEN && it.symbol == s.symbol && it.mode == TradeEngine.Mode.DEMO }) return false
        val now = System.currentTimeMillis()
        l.add(TradeEngine.Trade("p" + now, now, s.symbol, s.dir, s.entry, s.sl, s.tp1, lots, TradeEngine.Mode.DEMO))
        save(ctx, l)
        return true
    }

    /** Close open practice trades against fresh candles. Returns how many closed. */
    fun settleAll(ctx: Context): Int {
        val l = trades(ctx)
        var n = 0
        val bySym = HashMap<String, List<TradeEngine.Candle>>()
        for (i in l.indices) {
            val t = l[i]
            if (t.status != TradeEngine.Status.OPEN) continue
            val c = bySym.getOrPut(t.symbol) { fetch(t.symbol, "5m", "5d") }
            if (c.isEmpty()) continue
            val s = TradeEngine.settle(t, c, now = System.currentTimeMillis())
            if (s.status != TradeEngine.Status.OPEN) { l[i] = s; n++ }
        }
        if (n > 0) save(ctx, l)
        return n
    }

    // ---------------------------------------------------------------- network

    fun fetch(symbol: String, interval: String = "15m", range: String = "5d"): List<TradeEngine.Candle> {
        val u = TradeEngine.yahooUrl(symbol, interval, range)
        if (u.isEmpty()) return emptyList()
        var c: HttpURLConnection? = null
        return try {
            c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 7000; c.readTimeout = 12000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) NOVA")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) emptyList()
            else TradeEngine.parseYahoo(c.inputStream.bufferedReader().use { it.readText().take(1_500_000) })
        } catch (e: Exception) { emptyList() } finally { try { c?.disconnect() } catch (e: Exception) { } }
    }

    // ---------------------------------------------------------------- analysis

    class Result(val signal: TradeEngine.Signal, val lots: Double, val calibrated: Boolean, val feedOk: Boolean, val mt5: TradeEngine.Mt5Read?, val spoken: String, val extra: Extra = Extra())

    /** Reads MT5 text when MT5 is the app on screen (Accessibility). null otherwise. */
    fun readMt5Now(): TradeEngine.Mt5Read? {
        val s = NovaAccessibilityService.readScreen(false) ?: return null
        if (s.pkg != TradeRules.MT5_PKG) return null
        return TradeEngine.readMt5(s.lines)
    }

    /** The newest analysis and when it was made. Orders only ever use this one, and only while it is fresh (3 minutes). */
    @Volatile var last: Result? = null
    @Volatile var lastAt: Long = 0L
    fun freshLast(): Result? = if (System.currentTimeMillis() - lastAt <= 180_000L) last else null

    fun analyse(ctx: Context, symbol: String, interval: String = "15m"): Result {
        val sym = TradeEngine.Spec.canon(symbol)
        val mt = readMt5Now()
        val candles = if (sym.isEmpty()) emptyList() else fetch(sym, interval, if (interval == "1d") "3mo" else "5d")
        var sig = TradeEngine.analyse(if (sym.isEmpty()) symbol else sym, candles)
        if (candles.isEmpty()) sig = sig.copy(waitWhy = if (sym.isEmpty()) "Ye symbol list me nahi hai" else "Market data nahi mila, internet check karo")
        var cal = false
        if (mt != null && mt.symbol == sig.symbol && mt.price != null && sig.dir != TradeEngine.Dir.WAIT) {
            sig = TradeEngine.calibrate(sig, mt.price); cal = true
        }
        val bal = mt?.balance ?: balance(ctx)
        if (mt?.balance != null) setBalance(ctx, mt.balance)
        val lot = if (sig.dir == TradeEngine.Dir.WAIT) 0.0 else TradeEngine.lots(sig.symbol, bal, risk(ctx), sig.entry, sig.sl)
        val res = Result(sig, lot, cal, candles.isNotEmpty(), mt, TradeEngine.speak(sig, lot))
        last = res; lastAt = System.currentTimeMillis()
        return res
    }

    fun resultJson(r: Result): JSONObject {
        val s = r.signal
        return JSONObject().put("symbol", s.symbol).put("dir", s.dir.name).put("entry", s.entry).put("sl", s.sl).put("tp1", s.tp1).put("tp2", s.tp2)
            .put("score", s.score).put("rsi", Math.round(s.rsi * 10.0) / 10.0).put("trend", s.trend).put("atr", s.atr)
            .put("reasons", JSONArray(s.reasons)).put("why", s.waitWhy).put("lots", r.lots).put("calibrated", r.calibrated).put("feed", r.feedOk)
            .put("mt5demo", r.mt5?.demo ?: JSONObject.NULL).put("spoken", r.spoken)
    }

    fun cardJson(ctx: Context): JSONObject {
        val all = trades(ctx)
        val c = TradeEngine.card(all, TradeEngine.Mode.DEMO)
        val miss = TradeEngine.Gate.missing(c)
        val realCard = TradeEngine.card(all, TradeEngine.Mode.REAL)
        val rec = JSONArray()
        for (t in all.filter { it.mode == TradeEngine.Mode.DEMO }.sortedByDescending { it.ts }.take(25)) {
            rec.put(JSONObject().put("sym", t.symbol).put("dir", t.dir.name).put("entry", t.entry).put("sl", t.sl).put("tp", t.tp1).put("lots", t.lots)
                .put("st", t.status.name).put("r", t.r).put("ts", t.ts))
        }
        return JSONObject().put("closed", c.closed).put("wins", c.wins).put("losses", c.losses).put("winRate", c.winRate).put("expectancy", c.expectancy)
            .put("pf", c.profitFactor).put("dd", c.maxDrawdownR).put("last10", c.last10R).put("totalR", c.totalR).put("days", c.activeDays).put("open", c.open)
            .put("unlocked", miss.isEmpty()).put("missing", JSONArray(miss)).put("realClosed", realCard.closed).put("realR", realCard.totalR)
            .put("need", JSONObject().put("trades", TradeEngine.Gate.MIN_TRADES).put("days", TradeEngine.Gate.MIN_DAYS).put("exp", TradeEngine.Gate.MIN_EXPECTANCY)
                .put("pf", TradeEngine.Gate.MIN_PROFIT_FACTOR).put("dd", TradeEngine.Gate.MAX_DRAWDOWN_R))
            .put("risk", risk(ctx)).put("balance", balance(ctx)).put("symbols", JSONArray(symbols(ctx))).put("recent", rec)
    }

    // ================================================================ v7.92 FIXES (add-only; the MT5 driver below is untouched)

    private const val K_HTF = "htf_on"
    private const val K_SESS = "sess_on"

    fun htfOn(ctx: Context): Boolean = sp(ctx).getBoolean(K_HTF, true)
    fun sessionOn(ctx: Context): Boolean = sp(ctx).getBoolean(K_SESS, true)
    fun setFilters(ctx: Context, htf: Boolean, session: Boolean) { sp(ctx).edit().putBoolean(K_HTF, htf).putBoolean(K_SESS, session).apply() }

    /** Extra facts about one analysis, shown in the TRADE screen. */
    class Extra(
        val htf: String = "UNKNOWN",
        val feed: String = "",
        val stale: Boolean = false,
        val spread: Double = 0.0,
        val costR: Double = 0.0,
        val filtered: Boolean = false,
        val session: String = ""
    )

    private fun httpText(u: String): String? {
        if (u.isEmpty()) return null
        var c: HttpURLConnection? = null
        return try {
            c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 7000; c.readTimeout = 12000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) NOVA")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) null
            else c.inputStream.bufferedReader().use { it.readText().take(1_500_000) }
        } catch (e: Exception) {
            ErrLog.add("TradeHub", "feed: " + e.toString())
            null
        } finally {
            try { c?.disconnect() } catch (e: Exception) { ErrLog.add("TradeHub", e.toString()) }
        }
    }

    /** FIX 6: three sources in a row (Yahoo, Yahoo mirror, Binance for crypto). Returns the candles and the name of the source that worked. */
    fun fetchFeed(symbol: String, interval: String, range: String, minCandles: Int = TradeEngine.MIN_CANDLES): Pair<List<TradeEngine.Candle>, String> {
        val a = httpText(TradeEngine.yahooUrl(symbol, interval, range))?.let { TradeEngine.parseYahoo(it) } ?: emptyList()
        if (a.size >= minCandles) return Pair(a, "yahoo")
        val b = httpText(TradeEngine.yahooUrl2(symbol, interval, range))?.let { TradeEngine.parseYahoo(it) } ?: emptyList()
        if (b.size >= minCandles) return Pair(b, "yahoo2")
        val c = httpText(TradeEngine.binanceUrl(symbol, interval))?.let { TradeEngine.parseBinance(it) } ?: emptyList()
        if (c.size >= minCandles) return Pair(c, "binance")
        val best = listOf(a, b, c).maxByOrNull { it.size } ?: emptyList()
        return Pair(best, if (best.isEmpty()) "none" else "weak")
    }

    /** v7.92 analysis: finished candles only, backup feeds, stale-feed check, 1 hour trend, session / spread filters. Orders still use freshLast(). */
    fun analyse2(ctx: Context, symbol: String, interval: String = "15m"): Result {
        val sym = TradeEngine.Spec.canon(symbol)
        val mt = readMt5Now()
        val now = System.currentTimeMillis()
        val ims = TradeEngine.intervalMs(interval)
        var feed = "none"
        var raw: List<TradeEngine.Candle> = emptyList()
        if (sym.isNotEmpty()) {
            val f = fetchFeed(sym, interval, if (interval == "1d") "3mo" else "5d")
            raw = f.first; feed = f.second
        }
        val candles = TradeEngine.closedOnly(raw, ims, now)
        var sig = TradeEngine.analyse(if (sym.isEmpty()) symbol else sym, candles)
        val before = sig.dir
        val stale = TradeEngine.feedStale(candles, ims, now, sig.symbol)
        if (stale && sig.dir != TradeEngine.Dir.WAIT) sig = TradeEngine.toWait(sig, "Market data purana hai (feed ruka hua), abhi signal nahi")
        var htf = "UNKNOWN"
        if (sym.isNotEmpty() && sig.dir != TradeEngine.Dir.WAIT && interval != "1d") {
            val h = fetchFeed(sym, "60m", "1mo", 55)
            htf = TradeEngine.htfTrend(TradeEngine.closedOnly(h.first, 3_600_000L, now))
        }
        sig = TradeEngine.applyFilters(sig, htf, now, htfOn(ctx), sessionOn(ctx))
        if (candles.isEmpty()) sig = sig.copy(waitWhy = if (sym.isEmpty()) "Ye symbol list me nahi hai" else "Market data nahi mila, internet check karo")
        var cal = false
        if (mt != null && mt.symbol == sig.symbol && mt.price != null && sig.dir != TradeEngine.Dir.WAIT) {
            sig = TradeEngine.calibrate(sig, mt.price); cal = true
        }
        val bal = mt?.balance ?: balance(ctx)
        if (mt?.balance != null) setBalance(ctx, mt.balance)
        val lot = if (sig.dir == TradeEngine.Dir.WAIT) 0.0 else TradeEngine.lots(sig.symbol, bal, risk(ctx), sig.entry, sig.sl)
        val cost = if (sig.dir == TradeEngine.Dir.WAIT) 0.0 else TradeEngine.costR(sig.symbol, sig.entry, sig.sl)
        val ex = Extra(htf, feed, stale, TradeEngine.spread(sig.symbol), cost, before != TradeEngine.Dir.WAIT && sig.dir == TradeEngine.Dir.WAIT, TradeEngine.sessionBlock(sig.symbol, now))
        val spoken = TradeEngine.speak(sig, lot) + (if (cost > 0.0) " Spread ka kharcha lagbhag " + cost + " R." else "")
        val res = Result(sig, lot, cal, candles.isNotEmpty(), mt, spoken, ex)
        last = res; lastAt = System.currentTimeMillis()
        return res
    }

    /** FIX 3 + 2: closes practice trades with the half-at-Target-1 + breakeven model and the spread cost. Network happens outside the lock. */
    fun settleAllV2(ctx: Context): Int {
        val open = trades(ctx).filter { it.status == TradeEngine.Status.OPEN }
        if (open.isEmpty()) return 0
        val bySym = HashMap<String, List<TradeEngine.Candle>>()
        val done = HashMap<String, TradeEngine.Trade>()
        val now = System.currentTimeMillis()
        for (t in open) {
            val c = bySym.getOrPut(t.symbol) { fetchFeed(t.symbol, "5m", "5d", 1).first }
            if (c.isEmpty()) continue
            val s = TradeEngine.settleScaled(t, c, now = now)
            if (s.status != TradeEngine.Status.OPEN) done[t.id] = s
        }
        if (done.isEmpty()) return 0
        return synchronized(this) {
            val l = trades(ctx)
            var n = 0
            for (i in l.indices) {
                val s = done[l[i].id] ?: continue
                if (l[i].status == TradeEngine.Status.OPEN) { l[i] = s; n++ }
            }
            if (n > 0) save(ctx, l)
            n
        }
    }

    fun resultJson2(r: Result): JSONObject {
        val x = r.extra
        return resultJson(r).put("htf", x.htf).put("feedName", x.feed).put("stale", x.stale).put("spread", x.spread)
            .put("costR", x.costR).put("filtered", x.filtered).put("session", x.session)
    }

    fun cardJson2(ctx: Context): JSONObject {
        val j = cardJson(ctx)
        val c = TradeEngine.card(trades(ctx), TradeEngine.Mode.DEMO)
        j.put("lb", TradeEngine.Gate.lowerBoundR(c)).put("sd", c.sdR).put("htfOn", htfOn(ctx)).put("sessOn", sessionOn(ctx))
        j.getJSONObject("need").put("lb", TradeEngine.Gate.MIN_LOWER_BOUND_R)
        return j
    }

    // ---------------------------------------------------------------- MT5 driver

    class Send(val ok: Boolean, val stage: String, val msg: String)

    private fun sleep(ms: Long) { try { Thread.sleep(ms) } catch (e: InterruptedException) { } }

    private fun waitMt5(ctx: Context, sec: Int): Boolean {
        for (i in 0 until sec * 2) {
            if (NovaAccessibilityService.foregroundPackage() == TradeRules.MT5_PKG) return true
            sleep(500)
        }
        return false
    }

    private fun snap(): List<AgentRules.Node> {
        val p = NovaAccessibilityService.agentSnapshot() ?: return emptyList()
        return if (p.first == TradeRules.MT5_PKG) p.second else emptyList()
    }

    private fun findIdx(nodes: List<AgentRules.Node>, vararg words: String, editable: Boolean? = null): Int {
        for (n in nodes) {
            if (editable != null && n.editable != editable) continue
            val t = (n.text + " " + n.desc).lowercase()
            if (words.all { t.contains(it) }) return n.i
        }
        return -1
    }

    private fun fmtPrice(sym: String, v: Double): String {
        val d = TradeEngine.Spec.of(sym).digits
        return String.format(java.util.Locale.US, "%." + d + "f", v)
    }

    /**
     * Put the order into MT5. [real] = false: only on a Demo account. [real] = true: the gate must be open, the owner must have confirmed in the NOVA screen,
     * and MT5 must say it is NOT a demo account. Returns where it stopped. Never loops, never retries a tap on the final Buy/Sell button.
     */
    fun sendToMt5(ctx: Context, s: TradeEngine.Signal, lots: Double, real: Boolean, ownerConfirmed: Boolean): Send {
        if (s.dir == TradeEngine.Dir.WAIT) return Send(false, "signal", "WAIT signal par order nahi lagta.")
        if (!NovaAccessibilityService.isEnabled(ctx)) return Send(false, "access", "Accessibility ON karo (Setup), tab main MT5 ki screen padh sakta hoon.")
        val li = try { ctx.packageManager.getLaunchIntentForPackage(TradeRules.MT5_PKG) } catch (e: Exception) { null }
            ?: return Send(false, "install", "Is phone me MetaTrader 5 install nahi hai.")
        if (real) {
            val c = TradeEngine.card(trades(ctx), TradeEngine.Mode.DEMO)
            val rt = trades(ctx).filter { it.mode == TradeEngine.Mode.REAL }
            val todayStart = System.currentTimeMillis() - System.currentTimeMillis() % 86_400_000L
            val loss = rt.filter { it.closedTs >= todayStart && it.r < 0 }.sumOf { -it.r }
            val ok = TradeEngine.Gate.realAllowed(c, ownerConfirmed, loss, rt.count { it.status == TradeEngine.Status.OPEN })
            if (!ok.first) return Send(false, "gate", ok.second)
        }
        try { ctx.startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { return Send(false, "open", "MT5 khul nahi paya.") }
        if (!waitMt5(ctx, 12)) return Send(false, "open", "MT5 screen par nahi aayi.")
        sleep(1200)
        // 1. account check, BEFORE any tap
        val mt = readMt5Now() ?: return Send(false, "read", "MT5 ki screen padh nahi paya.")
        if (!real && mt.demo != true) return Send(false, "account", "MT5 me Demo account nahi dikh raha. Practice sirf Demo par hoti hai, isliye ruk gaya.")
        if (real && mt.demo != false) return Send(false, "account", "Real order ke liye MT5 me Real account saaf dikhna chahiye, isliye ruk gaya.")
        // 2. open the Trade tab, then New Order
        var r = NovaAccessibilityService.tapLabel("Trade")
        if (!r.ok && !r.code.startsWith("ambiguous")) return Send(false, "tab", "MT5 me Trade tab nahi mila (" + r.code + ").")
        sleep(900)
        var nodes = snap()
        var plus = findIdx(nodes, "new order")
        if (plus < 0) plus = findIdx(nodes, "add")
        if (plus < 0) return Send(false, "neworder", "MT5 me New Order button nahi mila. Aap Trade tab me '+' dabao, phir dobara bolo.")
        NovaAccessibilityService.agentTap(plus, false)
        sleep(1100)
        nodes = snap()
        // 3. symbol must already be the right one
        val symOk = nodes.any { it.text.uppercase().replace(Regex("[^A-Z0-9]"), "").startsWith(s.symbol) }
        if (!symOk) return Send(false, "symbol", "Order screen par symbol " + s.symbol + " nahi hai. Pehle MT5 me " + s.symbol + " chuno.")
        // 4. fields
        val vol = findIdx(nodes, "volume", editable = true)
        val slI = findIdx(nodes, "stop loss", editable = true)
        val tpI = findIdx(nodes, "take profit", editable = true)
        if (vol < 0 || slI < 0 || tpI < 0) return Send(false, "fields", "Volume / Stop Loss / Take Profit ke box nahi mile. Kuch bhi nahi bheja.")
        val lotTxt = String.format(java.util.Locale.US, "%.2f", lots)
        if (!NovaAccessibilityService.agentType(vol, lotTxt).ok) return Send(false, "fields", "Volume likh nahi paya. Kuch bhi nahi bheja.")
        nodes = snap()
        val slI2 = findIdx(nodes, "stop loss", editable = true)
        if (slI2 < 0 || !NovaAccessibilityService.agentType(slI2, fmtPrice(s.symbol, s.sl)).ok) return Send(false, "fields", "Stop Loss likh nahi paya. Kuch bhi nahi bheja.")
        nodes = snap()
        val tpI2 = findIdx(nodes, "take profit", editable = true)
        if (tpI2 < 0 || !NovaAccessibilityService.agentType(tpI2, fmtPrice(s.symbol, s.tp1)).ok) return Send(false, "fields", "Take Profit likh nahi paya. Kuch bhi nahi bheja.")
        // 5. the one final tap: market Buy / Sell. Checked, never retried.
        nodes = snap()
        val word = if (s.dir == TradeEngine.Dir.BUY) "buy" else "sell"
        val btn = findIdx(nodes, word, editable = false)
        if (btn < 0) return Send(false, "button", "Market " + word.uppercase() + " button nahi mila. Kuch bhi nahi bheja.")
        r = NovaAccessibilityService.agentTap(btn, false)
        if (!r.ok) return Send(false, "button", "Button dab nahi paya (" + r.code + ").")
        sleep(1500)
        val after = NovaAccessibilityService.readScreen(false)?.lines?.joinToString(" ")?.lowercase() ?: ""
        val bad = listOf("invalid", "error", "rejected", "no money", "not enough", "disabled", "off quotes")
        if (bad.any { after.contains(it) }) return Send(false, "result", "MT5 ne order nahi liya (screen par error dikha). Screen khud dekh lo.")
        if (!real) openPractice(ctx, s, lots)
        else {
            val l = trades(ctx); val now = System.currentTimeMillis()
            l.add(TradeEngine.Trade("r" + now, now, s.symbol, s.dir, s.entry, s.sl, s.tp1, lots, TradeEngine.Mode.REAL)); save(ctx, l)
        }
        return Send(true, "done", (if (real) "REAL" else "DEMO") + " order bhej diya: " + s.symbol + " " + s.dir.name + " lot " + lotTxt + ". MT5 me Trade tab me check karo.")
    }
}
