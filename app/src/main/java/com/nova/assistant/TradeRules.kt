package com.nova.assistant

/**
 * v7.90 TRADING EXPERT (analysis only, demo account first). Pure Kotlin so JUnit can test it.
 * NOVA analyses with the owner's AI key + Google Search (live price / news), answers short, and can open MT5.
 * It never places, edits or closes a trade by itself (MT5 has no public API; blind screen taps on a money app are unsafe),
 * never says "sure profit" / "no loss", and always gives entry + stop loss + target + small risk, or says WAIT.
 */
object TradeRules {
    data class Req(val query: String, val openMt5: Boolean, val placeAsk: Boolean)

    const val MT5_PKG = "net.metaquotes.metatrader5"

    private val MARKET = setOf("gold", "xauusd", "xau", "silver", "xagusd", "eurusd", "gbpusd", "usdjpy", "usdinr", "forex", "btc", "btcusd", "bitcoin", "ethereum", "eth", "crypto",
        "nifty", "banknifty", "sensex", "nasdaq", "us30", "dow", "oil", "crude", "stock", "share", "mt5", "metatrader", "trade", "trading")
    private val CUE = setOf("analysis", "analyse", "analyze", "analyses", "signal", "entry", "target", "stoploss", "sl", "tp", "buy", "sell", "trend", "chart", "levels", "level", "support", "resistance",
        "lena", "lun", "bechna", "becho", "kharidna", "kharid", "karu", "kare", "kre", "karo", "lagao", "lagana", "dekho", "batao", "bolo", "kaisa", "kaisi", "chahiye")
    private val BLOCK = setOf("website", "site", "photo", "video", "reel", "movie", "song", "gaana", "call", "message", "whatsapp", "alarm", "timer", "torch", "wifi", "brightness", "volume", "meeting", "schedule")
    private val PLACE = setOf("trade", "lagao", "lagana", "place", "execute", "order", "kholo", "khol", "open")

    fun parse(raw: String): Req? {
        val n = Logic.norm(raw)
        if (n.isBlank() || n.length > 160) return null
        val t = n.split(" ").filter { it.isNotBlank() }
        if (t.size < 2) return null
        if (t.any { it in BLOCK }) return null
        val market = t.any { it in MARKET }
        if (!market) return null
        // "mt5 kholo" alone is the normal open-app command (it must keep working)
        val onlyOpen = t.all { it in setOf("nova", "mt5", "metatrader", "metatrader5", "5", "kholo", "khol", "open", "karo", "do", "app", "please", "chalu") }
        if (onlyOpen) return null
        val cue = t.any { it in CUE }
        if (!cue) return null
        // question words about meaning, not about a trade ("bitcoin kya hai", "trading kya hoti hai")
        if ((t.contains("kya") && (t.contains("hai") || t.contains("hota") || t.contains("hoti"))) && t.none { it in setOf("analysis", "signal", "entry", "target", "buy", "sell", "trend") }) return null
        val openMt5 = t.any { it == "mt5" || it == "metatrader" || it == "metatrader5" } && t.any { it in setOf("kholo", "khol", "open", "chalu", "lagao", "trade") }
        val placeAsk = t.any { it in PLACE } && t.any { it in setOf("lagao", "lagana", "place", "execute", "karo", "kar", "do") } && (t.contains("trade") || t.contains("order"))
        return Req(n.take(160), openMt5 || placeAsk, placeAsk)
    }

    fun systemPrompt(en: Boolean): String =
        "You are NOVA's trading analyst for a DEMO account practice. Use Google Search for the live price, today's news and key levels before answering. " +
        "Answer in " + (if (en) "simple English" else "short Hinglish (Roman Hindi)") + ", max 110 words, spoken-friendly, no tables, no markdown symbols. " +
        "Format: 1) Market abhi (price + trend in one line). 2) Idea: BUY or SELL or WAIT. 3) Entry, Stop Loss, Target 1, Target 2 as numbers. 4) Risk: at most 1 percent of balance per trade, lot size small. 5) One-line reason. " +
        "If the picture is unclear or news risk is high, say WAIT and why. NEVER promise profit, never say sure-shot, never say no loss. Say clearly that this is analysis, not advice, and to use the DEMO account only. " +
        "If the user asks for a live-money trade, answer: first practice on demo."

    fun userPrompt(q: String): String = "Owner ne bola: \"" + q.take(160) + "\". Abhi ka analysis do."

    fun disclaimer(en: Boolean): String = if (en) "This is analysis, not a guarantee. No one can promise zero loss. Trade only on the demo account." else "Ye sirf analysis hai, guarantee nahi. Koi bhi loss zero ki guarantee nahi de sakta. Sirf demo account par trade karo."

    fun noKeyText(en: Boolean) = if (en) "Trading analysis needs an AI key. Add a Gemini key in Settings." else "Trading analysis ke liye AI key chahiye. Settings me Gemini key daalo."
    fun failedText(en: Boolean) = if (en) "I could not get the market analysis right now. Check the internet and try again." else "Abhi market analysis nahi mil paya. Internet check karke dobara bolo."
    fun placeText(en: Boolean) = if (en) "I cannot place the trade myself yet: I will open MT5, you tap the order using the entry, stop loss and target above, on the DEMO account." else "Trade main khud nahi laga sakta abhi: MT5 khol deta hoon, upar wale entry, stop loss aur target se DEMO account me order aap lagao."
    fun mt5MissingText(en: Boolean) = if (en) "MT5 is not installed on this phone." else "Is phone me MT5 app install nahi hai."
}
