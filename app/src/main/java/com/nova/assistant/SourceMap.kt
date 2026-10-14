package com.nova.assistant

/**
 * v6.97 "KAUN SI CHEEZ KAHAN MILEGI": the one table that says, for every kind of need, WHERE NOVA gets the answer, what it costs
 * (time / API load) and whether it needs the internet. The order of the rows IS the routing order used by NovaService.handle:
 * the cheapest and fastest source that can really answer goes first, a cloud AI only gets what nothing cheaper can answer.
 * Pure Kotlin. Used for (1) the AI prompt facts so the cloud AI never claims what NOVA cannot do, (2) the help / status screen,
 * (3) the local-first gate (which questions the on-device AI may answer even when online).
 */
object SourceMap {
    enum class Cost(val label: String) {
        INSTANT("turant, phone par"),
        LOCAL_FILE("turant, phone ki file se"),
        KEYLESS_WEB("1-3 sec, internet, bina key"),
        ON_DEVICE_AI("on-device AI, bina internet"),
        CLOUD_AI("cloud AI, key + limit lagti hai")
    }

    class Entry(val id: String, val need: String, val where: String, val cost: Cost, val needsNet: Boolean, val example: String, val say: String)

    val entries: List<Entry> = listOf(
        Entry("phone", "Phone control (torch, volume, brightness, wifi, apps, calls, alarm, timer, battery, time, date)", "Logic.classify rules + Android APIs", Cost.INSTANT, false, "torch on karo", "phone ke kaam -> seedha phone se"),
        Entry("calc", "Hisaab, unit badalna, tarikh ka hisaab, emergency number, first aid", "LocalSkills (rules)", Cost.INSTANT, false, "25 percent of 480", "hisaab -> phone ke rules se"),
        Entry("memory", "Meri yaad rakhi baatein, nicknames, sikhaye hue words", "PersonalMemory + TeachStore (phone par)", Cost.INSTANT, false, "mera naam kya hai", "meri yaad -> phone ki memory se"),
        Entry("knowledge", "Common general knowledge (science, India, health, daily life, NOVA ke baare me)", "knowledge.json (offline)", Cost.LOCAL_FILE, false, "paani kitne degree par ubalta hai", "aam gyaan -> phone ki offline file se"),
        Entry("weather", "Mausam, temperature, barish (aaj / kal)", "Open-Meteo browser", Cost.KEYLESS_WEB, true, "jaipur ka mausam", "mausam -> Open-Meteo"),
        Entry("news", "Taaza khabar, headlines, kisi topic ki khabar", "Google News RSS browser", Cost.KEYLESS_WEB, true, "cricket ki khabar sunao", "khabar -> Google News"),
        Entry("currency", "Dollar / euro / pound ... ka rate, paise badalna", "Frankfurter (ECB) browser", Cost.KEYLESS_WEB, true, "100 dollar kitne rupaye", "currency -> Frankfurter"),
        Entry("crypto", "Bitcoin / ethereum ... ka price", "CoinGecko browser", Cost.KEYLESS_WEB, true, "bitcoin ka price", "bitcoin -> CoinGecko"),
        Entry("define", "Kisi shabd ka matlab", "Wiktionary browser", Cost.KEYLESS_WEB, true, "meaning of serendipity", "shabd ka matlab -> Wiktionary"),
        Entry("fact", "Kisi insaan / jagah / cheez ke baare me jankari", "Wikipedia browser, phir DuckDuckGo", Cost.KEYLESS_WEB, true, "taj mahal ke baare me batao", "jankari -> Wikipedia, phir DuckDuckGo"),
        Entry("chat", "Chhoti baat-cheet, simple sawal, hello, joke", "On-device AI (online ho tab bhi pehle ye)", Cost.ON_DEVICE_AI, false, "kaise ho", "chhoti baat -> phone ka offline AI"),
        Entry("deep", "Lamba / mushkil jawab, code, likhna, translate, analysis, website, image", "Cloud AI (Gemini / Groq / OpenRouter / GLM ...)", Cost.CLOUD_AI, true, "ek email likho boss ko", "lamba ya mushkil -> cloud AI")
    )

    private val HEAVY = setOf(
        "code", "program", "programming", "script", "essay", "likho", "likh", "write", "translate", "anuvad", "summarize", "summary", "analyze", "analyse", "analysis",
        "explain", "samjhao", "detail", "detailed", "step", "steps", "website", "image", "photo", "picture", "tasveer", "poem", "kavita", "story", "kahani", "letter", "email", "essay", "compare", "difference",
        "plan", "itinerary", "recipe", "resume", "cv", "debug", "error", "function", "calculate", "equation", "prove", "proof", "algorithm"
    )
    private val LIVE = setOf("news", "weather", "mausam", "score", "price", "rate", "stock", "sensex", "nifty", "gold", "sona", "cricket", "match", "latest", "today", "aaj", "abhi", "live", "trending", "headlines", "khabar", "result", "election", "bhav", "bhaav")

    /** The on-device AI may answer this even while online: short, not heavy, not about live facts. */
    fun localCanHandle(text: String): Boolean {
        val w = Logic.norm(text).split(" ").filter { it.isNotEmpty() }
        if (w.isEmpty() || w.size > 14) return false
        return w.none { it in HEAVY || it in LIVE }
    }

    /** Facts for the cloud AI prompt: what NOVA does itself, so the AI does not promise or duplicate it. */
    fun aiFacts(): String {
        val sb = StringBuilder("NOVA answers these ITSELF before asking you: ")
        sb.append(entries.filter { it.cost != Cost.CLOUD_AI }.joinToString("; ") { it.need.substringBefore(" (").lowercase() })
        sb.append(". You only get long or hard questions. You have no live data (weather, news, prices): never invent numbers for them.")
        return sb.toString()
    }

    /** "Kahan se milega": text answer for 'tum ye jawab kahan se laate ho' style questions and the help card. */
    fun explain(en: Boolean): String {
        val head = if (en) "I pick the fastest real source for each need: " else "Main har kaam ke liye sabse tez asli source chunta hoon: "
        return head + entries.joinToString("; ") { it.say } + "."
    }

    /** "tum jawab kahan se laate ho", "kaunsa browser", "internet se kya kya dekh sakte ho" */
    fun isAsk(text: String): Boolean {
        val n = Logic.norm(text)
        val about = listOf("browser", "source", "sources", "kahan se", "kaha se", "kahan milta", "kaha milta", "where do you get", "where from")
        val subject = listOf("jawab", "answer", "jankari", "information", "info", "mausam", "khabar", "news", "rate")
        return n.length <= 80 && about.any { n.contains(it) } && subject.any { n.contains(it) } && (n.contains("tum") || n.contains("aap") || n.contains("you") || n.contains("nova") || n.contains("browser"))
    }
}
