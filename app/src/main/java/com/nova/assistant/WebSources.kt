package com.nova.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * v6.97 FIVE KEY-LESS "BROWSERS". No account, no API key, nothing secret is ever sent: only a short extracted topic / city /
 * currency pair goes to the public service (never the whole sentence, chat history, memory, audio or screen text).
 *
 *   WIKI     Wikipedia (facts, people, places, history, science) + Wiktionary (word meaning)   en.wikipedia.org, en.wiktionary.org
 *   INSTANT  DuckDuckGo Instant Answer (quick answers / second opinion for WIKI)                api.duckduckgo.com
 *   WEATHER  Open-Meteo (city -> weather now + today/tomorrow)                                 geocoding-api.open-meteo.com, api.open-meteo.com
 *   NEWS     Google News RSS (headlines, any topic)                                             news.google.com
 *   MARKET   Frankfurter (ECB currency rates) + CoinGecko (crypto price)                        api.frankfurter.dev, api.coingecko.com
 *
 * This file is PURE Kotlin (only org.json): the "which browser for which need" classifier, the request URLs, the response
 * parsers and the spoken text. The real network + cache + timeouts live in WebBrowsers. Everything here is unit tested
 * with sample payloads; a live call to the services has NOT been run in this sandbox (the sandbox blocks those hosts).
 */
object WebSources {
    enum class Browser(val id: String, val label: String, val service: String) {
        WIKI("wiki", "Wikipedia + Wiktionary", "Wikimedia"),
        INSTANT("instant", "DuckDuckGo Instant", "DuckDuckGo"),
        WEATHER("weather", "Open-Meteo Weather", "Open-Meteo"),
        NEWS("news", "Google News", "Google News RSS"),
        MARKET("market", "Currency + Crypto", "Frankfurter / CoinGecko")
    }

    enum class Kind(val browser: Browser, val live: Boolean) {
        WEATHER(Browser.WEATHER, true),
        NEWS(Browser.NEWS, true),
        CURRENCY(Browser.MARKET, true),
        CRYPTO(Browser.MARKET, true),
        DEFINE(Browser.WIKI, false),
        FACT(Browser.WIKI, false)
    }

    /** What the user needs. [query] = topic / city / coin / source currency, [to] = target currency, [amount] for currency, [dayOffset] 0 = today, 1 = tomorrow. */
    data class Need(val kind: Kind, val query: String, val to: String = "", val amount: Double = 1.0, val dayOffset: Int = 0)

    /** One planned HTTP GET. [role] tells the parser which response shape to expect. */
    data class Req(val browser: Browser, val role: String, val url: String)

    /** A finished answer: [text] is ready to speak / show, [browser] is who produced it. */
    data class Answer(val text: String, val browser: Browser)

    val ALLOWED_HOSTS: Set<String> = setOf(
        "en.wikipedia.org", "en.wiktionary.org", "api.duckduckgo.com", "geocoding-api.open-meteo.com", "api.open-meteo.com",
        "news.google.com", "api.frankfurter.dev", "api.frankfurter.app", "api.coingecko.com"
    )

    // ------------------------------------------------------------------ text helpers

    /** Lower-case words, ASCII only. Keeps digits and a decimal point between digits ("1.5"). */
    internal fun words(raw: String): List<String> {
        var s = raw.lowercase().replace("[unk]", " ")
        s = s.replace(Regex("(?<=\\d),(?=\\d{3})"), "")
        s = s.replace(Regex("[^a-z0-9. ]"), " ")
        s = s.replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), " ")
        return s.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun clip(s: String, max: Int): String {
        if (s.length <= max) return s
        val part = s.take(max)
        val stop = maxOf(part.lastIndexOf('.'), part.lastIndexOf('!'), part.lastIndexOf('?'))
        return if (stop >= max / 3) part.substring(0, stop + 1) else part.trimEnd() + "..."
    }

    // ------------------------------------------------------------------ the classifier: "what does this person need, and which browser has it?"

    private val WEATHER_WORDS = setOf("weather", "mausam", "mosam", "temperature", "tapman", "barish", "baarish", "rain", "raining", "garmi", "sardi", "thand", "forecast", "humidity", "dhoop")
    private val WEATHER_FILLER = setOf(
        "weather", "mausam", "mosam", "temperature", "tapman", "barish", "baarish", "rain", "raining", "garmi", "sardi", "thand", "forecast", "humidity", "dhoop",
        "kaisa", "kaisi", "kaise", "hai", "he", "h", "hoga", "hogi", "hoga", "ho", "rahega", "rahegi", "kya", "kitna", "kitni", "kitne", "abhi", "now", "aaj", "today", "tonight", "kal", "tomorrow",
        "ka", "ki", "ke", "me", "mein", "mai", "main", "mey", "in", "of", "at", "for", "the", "is", "it", "will", "be", "what", "whats", "how", "tell", "batao", "bata", "bataiye", "do", "dena", "de", "please",
        "nova", "mujhe", "hame", "hamen", "check", "karo", "kar", "dekho", "current", "right", "ab", "bahar", "outside", "here", "yaha", "yahan", "city", "shehar", "sheher", "wala", "wali", "raha", "rahi", "chal", "pad", "padegi", "padega", "kitna", "degree", "digree", "celsius", "garam", "thanda", "hogi", "hota", "hoti", "anywhere", "and", "to", "or"
    )

    private val NEWS_WORDS = setOf("news", "khabar", "khabarein", "khabrein", "khabren", "samachar", "samachaar", "headlines", "headline", "newsfeed")
    private val NEWS_FILLER = setOf(
        "news", "khabar", "khabarein", "khabrein", "khabren", "samachar", "samachaar", "headlines", "headline", "newsfeed", "taaza", "taza", "latest", "breaking", "top", "badi", "bade", "badi", "aaj", "ki", "ka", "ke", "ko", "me", "mein", "of", "on", "about", "for", "the",
        "sunao", "suna", "do", "de", "dena", "batao", "bata", "dikhao", "dikha", "tell", "me", "give", "show", "what", "whats", "is", "are", "today", "todays", "abhi", "now", "nova", "mujhe", "kuch", "koi", "please", "kya", "hai", "hain", "chal", "raha", "rahi", "rahe", "recent", "new", "naya", "nayi", "read", "padho", "padh", "paro", "aur", "and", "in", "from"
    )
    private val SPORT_WORDS = setOf("cricket", "ipl", "football", "match", "score", "scores", "tennis", "hockey", "kabaddi", "worldcup")

    private val CURRENCY_CODE = mapOf(
        "usd" to "USD", "dollar" to "USD", "dollars" to "USD", "doller" to "USD", "dolar" to "USD",
        "eur" to "EUR", "euro" to "EUR", "euros" to "EUR",
        "gbp" to "GBP", "pound" to "GBP", "pounds" to "GBP", "sterling" to "GBP",
        "inr" to "INR", "rupee" to "INR", "rupees" to "INR", "rupaye" to "INR", "rupaya" to "INR", "rupay" to "INR", "rupiya" to "INR", "rs" to "INR",
        "jpy" to "JPY", "yen" to "JPY", "cny" to "CNY", "yuan" to "CNY", "rmb" to "CNY",
        "aud" to "AUD", "cad" to "CAD", "chf" to "CHF", "franc" to "CHF", "sgd" to "SGD", "thb" to "THB", "baht" to "THB", "krw" to "KRW", "won" to "KRW",
        "try" to "TRY", "lira" to "TRY", "zar" to "ZAR", "rand" to "ZAR", "nzd" to "NZD", "hkd" to "HKD", "myr" to "MYR", "ringgit" to "MYR",
        "idr" to "IDR", "rupiah" to "IDR", "php" to "PHP", "peso" to "PHP",
        "aed" to "AED", "dirham" to "AED", "sar" to "SAR", "riyal" to "SAR", "pkr" to "PKR", "bdt" to "BDT", "taka" to "BDT", "npr" to "NPR", "lkr" to "LKR", "kwd" to "KWD", "dinar" to "KWD"
    )
    /** Currencies the Frankfurter (ECB) feed carries. */
    val ECB_CURRENCIES: Set<String> = setOf(
        "AUD", "BGN", "BRL", "CAD", "CHF", "CNY", "CZK", "DKK", "EUR", "GBP", "HKD", "HUF", "IDR", "ILS", "INR", "ISK", "JPY", "KRW", "MXN",
        "MYR", "NOK", "NZD", "PHP", "PLN", "RON", "SEK", "SGD", "THB", "TRY", "USD", "ZAR"
    )
    private val RATE_WORDS = setOf("rate", "bhav", "bhaav", "price", "kimat", "value", "convert", "conversion", "exchange", "kitne", "kitna", "kitni", "equals", "worth", "to", "in", "ko", "se", "hota", "hote", "hoga", "badla")

    private val COINS = mapOf(
        "bitcoin" to "bitcoin", "btc" to "bitcoin", "ethereum" to "ethereum", "eth" to "ethereum", "ether" to "ethereum",
        "dogecoin" to "dogecoin", "doge" to "dogecoin", "solana" to "solana", "sol" to "solana", "ripple" to "ripple", "xrp" to "ripple",
        "cardano" to "cardano", "ada" to "cardano", "litecoin" to "litecoin", "ltc" to "litecoin", "bnb" to "binancecoin", "tether" to "tether", "usdt" to "tether",
        "polygon" to "matic-network", "matic" to "matic-network", "shiba" to "shiba-inu", "tron" to "tron", "trx" to "tron", "polkadot" to "polkadot", "dot" to "polkadot"
    )
    private val COIN_NAMES = mapOf(
        "bitcoin" to "Bitcoin", "ethereum" to "Ethereum", "dogecoin" to "Dogecoin", "solana" to "Solana", "ripple" to "XRP", "cardano" to "Cardano",
        "litecoin" to "Litecoin", "binancecoin" to "BNB", "tether" to "Tether", "matic-network" to "Polygon", "shiba-inu" to "Shiba Inu", "tron" to "TRON", "polkadot" to "Polkadot"
    )
    private val PRICE_WORDS = setOf("price", "rate", "bhav", "bhaav", "kimat", "value", "kitna", "kitne", "kitni", "worth", "chal", "live", "today", "aaj", "abhi", "now")

    private val DEFINE_PREFIX = listOf("meaning of ", "definition of ", "define ", "what does ", "word meaning of ")
    private val DEFINE_SUFFIX = listOf(" ka matlab kya hai", " ka matlab batao", " ka matlab", " ka meaning kya hai", " ka meaning", " meaning in hindi", " meaning", " means", " ka arth")

    /** Words that mean the question is about the person / their phone, never a public lookup. */
    private val PERSONAL = setOf("phone", "contacts", "contact", "password", "otp", "pin", "bank", "address", "aadhaar", "aadhar", "pan", "private", "personal", "my", "mera", "meri", "mere")

    /** null = not a web lookup (the normal routing continues). Strict on purpose: a wrong web answer is worse than no web answer. */
    fun classify(raw: String): Need? {
        val text = raw.trim().replace(Regex("(?i)^(hey |ok |okay )?nova\\s*[,.:;-]?\\s*"), "")
        if (text.isEmpty() || text.length > 200) return null
        val w = words(text)
        if (w.isEmpty() || w.size > 14) return null
        val set = w.toSet()
        if (set.any { it in PERSONAL }) return null
        return weather(w, set) ?: crypto(w, set) ?: currency(w, set) ?: news(w, set) ?: define(text, w) ?: fact(text)
    }

    private fun weather(w: List<String>, set: Set<String>): Need? {
        if (set.none { it in WEATHER_WORDS }) return null
        // "garmi / sardi / thand / dhoop / rain" alone are too common in other talk: need a place or a question word with a real weather word
        val strong = set.any { it in setOf("weather", "mausam", "mosam", "temperature", "tapman", "barish", "baarish", "rain", "raining", "forecast", "humidity") }
        val city = w.filter { it !in WEATHER_FILLER && it.any { c -> c.isLetter() } }
        if (!strong && city.isEmpty()) return null
        if (city.size > 3) return null
        val day = if (set.contains("kal") || set.contains("tomorrow")) 1 else 0
        return Need(Kind.WEATHER, city.joinToString(" ").trim(), dayOffset = day)
    }

    private fun news(w: List<String>, set: Set<String>): Need? {
        val hasNews = set.any { it in NEWS_WORDS }
        val sport = set.any { it in SPORT_WORDS } && set.any { it in setOf("score", "scores", "match", "result", "live") }
        if (!hasNews && !sport) return null
        val topic = w.filter { it !in NEWS_FILLER }
        if (topic.size > 6) return null
        val q = topic.joinToString(" ")
        return Need(Kind.NEWS, if (sport && !hasNews && !q.contains("score")) (q + " score").trim() else q)
    }

    private fun currency(w: List<String>, set: Set<String>): Need? {
        val codes = ArrayList<String>()
        for (t in w) { val c = CURRENCY_CODE[t]; if (c != null && (codes.isEmpty() || codes.last() != c)) codes.add(c) }
        if (codes.isEmpty()) return null
        if (w.size > 9) return null
        val amount = w.firstOrNull { it.toDoubleOrNull() != null }?.toDoubleOrNull() ?: 1.0
        val rateish = set.any { it in RATE_WORDS }
        if (!rateish) return null
        // single word forms like "rs" / "to" are too weak alone: a number or a clear rate word must be there
        val clear = set.any { it in setOf("rate", "bhav", "bhaav", "price", "kimat", "value", "convert", "conversion", "exchange", "kitne", "kitna", "kitni", "worth") } ||
            (codes.size >= 2 && set.any { it in setOf("to", "in", "ko", "se") })
        if (!clear) return null
        val from: String
        val to: String
        if (codes.size >= 2) {
            // "100 dollar kitne rupaye" = dollar -> rupaye ; "usd to inr" same order ; "rupaye me dollar" (in) means dollar -> rupaye is ambiguous, keep spoken order
            from = codes[0]; to = codes[1]
            if (from == to) return null
        } else {
            from = codes[0]
            to = if (from == "INR") "USD" else "INR"
        }
        return Need(Kind.CURRENCY, from, to, amount.coerceIn(0.0, 1.0e12))
    }

    private fun crypto(w: List<String>, set: Set<String>): Need? {
        val coin = w.firstNotNullOfOrNull { COINS[it] } ?: return null
        if (set.none { it in PRICE_WORDS }) return null
        if (w.size > 8) return null
        return Need(Kind.CRYPTO, coin)
    }

    private fun define(text: String, w: List<String>): Need? {
        val n = w.joinToString(" ")
        var word: String? = null
        for (p in DEFINE_PREFIX) if (n.startsWith(p)) { word = n.removePrefix(p).removeSuffix(" mean").trim(); break }
        if (word == null) for (s in DEFINE_SUFFIX) if (n.endsWith(s)) { word = n.removeSuffix(s).trim(); break }
        if (word.isNullOrBlank()) return null
        val parts = word.split(" ").filter { it.isNotEmpty() }
        if (parts.size != 1 || parts[0].length < 3 || parts[0].length > 30 || parts[0].any { it.isDigit() }) return null
        return Need(Kind.DEFINE, parts[0])
    }

    private fun fact(text: String): Need? {
        val topic = PublicKnowledgeFallback.topicOf(text) ?: return extraFact(text)
        return Need(Kind.FACT, topic)
    }

    private val EXTRA_FACT_SUFFIX = listOf(" kaun tha", " kaun thi", " kab bana", " kab bani", " kab hua", " kab hui", " kahan hai", " kahan hota hai", " ki rajdhani", " ka founder kaun hai", " ka ceo kaun hai")
    private val EXTRA_FACT_PREFIX = listOf("capital of ", "founder of ", "ceo of ", "population of ", "who founded ", "where is ", "when did ", "how tall is ", "how big is ")

    private fun extraFact(text: String): Need? {
        val n = words(text).joinToString(" ")
        for (p in EXTRA_FACT_PREFIX) if (n.startsWith(p)) return topicNeed(p.trim() + " " + n.removePrefix(p))
        for (s in EXTRA_FACT_SUFFIX) if (n.endsWith(s)) return topicNeed(n)
        return null
    }

    private fun topicNeed(t: String): Need? {
        val topic = t.trim()
        val tokens = topic.split(" ")
        if (topic.length !in 4..100 || tokens.size > 10) return null
        if (tokens.any { it in PERSONAL }) return null
        return Need(Kind.FACT, topic)
    }

    // ------------------------------------------------------------------ requests

    fun requests(n: Need): List<Req> = when (n.kind) {
        Kind.FACT -> listOf(
            Req(Browser.WIKI, "wiki", wikiUrl(n.query)),
            Req(Browser.INSTANT, "ddg", ddgUrl(n.query))
        )
        Kind.DEFINE -> listOf(
            Req(Browser.WIKI, "wiktionary", "https://en.wiktionary.org/api/rest_v1/page/definition/" + enc(n.query)),
            Req(Browser.INSTANT, "ddg", ddgUrl(n.query))
        )
        Kind.NEWS -> listOf(Req(Browser.NEWS, "rss", newsUrl(n.query)))
        Kind.CURRENCY -> listOf(
            Req(Browser.MARKET, "fx", "https://api.frankfurter.dev/v1/latest?base=${enc(n.query)}&symbols=${enc(n.to)}"),
            Req(Browser.MARKET, "fx", "https://api.frankfurter.app/latest?from=${enc(n.query)}&to=${enc(n.to)}")
        )
        Kind.CRYPTO -> listOf(Req(Browser.MARKET, "coin", "https://api.coingecko.com/api/v3/simple/price?ids=${enc(n.query)}&vs_currencies=inr,usd&include_24hr_change=true"))
        Kind.WEATHER -> listOf(Req(Browser.WEATHER, "geo", geoUrl(n.query)))   // the forecast URL needs the coordinates: see forecastUrl
    }

    fun wikiUrl(topic: String): String =
        "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${enc(topic)}&gsrnamespace=0&gsrlimit=1&prop=extracts%7Cinfo" +
            "&exintro=1&explaintext=1&exsentences=3&inprop=url&redirects=1&format=json&formatversion=2"

    fun ddgUrl(q: String): String = "https://api.duckduckgo.com/?q=${enc(q)}&format=json&no_html=1&skip_disambig=1&t=nova"

    fun newsUrl(q: String): String =
        if (q.isBlank()) "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en"
        else "https://news.google.com/rss/search?q=${enc(q)}&hl=en-IN&gl=IN&ceid=IN:en"

    fun geoUrl(city: String): String = "https://geocoding-api.open-meteo.com/v1/search?name=${enc(city)}&count=1&language=en&format=json"

    fun forecastUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${"%.4f".format(java.util.Locale.US, lat)}&longitude=${"%.4f".format(java.util.Locale.US, lon)}" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code&timezone=auto&forecast_days=2"

    // ------------------------------------------------------------------ parsers (each returns null for anything it does not fully understand)

    private fun json(body: String): JSONObject? = try { JSONObject(body.trim()) } catch (e: Exception) { null }

    /** Wikipedia generator=search + extracts. Returns "Title: summary". */
    fun parseWiki(body: String, en: Boolean): Answer? {
        val page = json(body)?.optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0) ?: return null
        if (page.optBoolean("missing", false)) return null
        val title = page.optString("title", "").trim()
        val summary = page.optString("extract", "").replace(Regex("\\s+"), " ").trim()
        if (title.isEmpty() || summary.length < 40) return null
        if (summary.contains("may refer to:") || summary.endsWith("may refer to")) return null   // a disambiguation page is not an answer
        val s = clip(summary, 520)
        return Answer(if (en) "$title: $s (Wikipedia)" else "$title: $s (Wikipedia, English source)", Browser.WIKI)
    }

    /** Wiktionary REST /page/definition: first English entries, tags removed. */
    fun parseWiktionary(body: String, word: String, en: Boolean): Answer? {
        val root = json(body) ?: return null
        val entries: JSONArray = root.optJSONArray("en") ?: return null
        val out = ArrayList<String>()
        for (i in 0 until minOf(entries.length(), 2)) {
            val e = entries.optJSONObject(i) ?: continue
            val pos = e.optString("partOfSpeech", "").lowercase()
            val defs = e.optJSONArray("definitions") ?: continue
            var taken = 0
            for (j in 0 until defs.length()) {
                if (taken >= 2) break
                val d = stripTags(defs.optJSONObject(j)?.optString("definition", "") ?: "")
                if (d.length < 3) continue
                out.add((if (pos.isNotEmpty() && taken == 0) "($pos) " else "") + d)
                taken++
            }
        }
        if (out.isEmpty()) return null
        val joined = clip(out.joinToString("; "), 360)
        return Answer(if (en) "$word: $joined (Wiktionary)" else "$word ka matlab: $joined (Wiktionary, English)", Browser.WIKI)
    }

    /** DuckDuckGo Instant Answer: Answer, then AbstractText, then Definition. Related-topic lists are NOT used (too loose to be a real answer). */
    fun parseDdg(body: String, en: Boolean): Answer? {
        val r = json(body) ?: return null
        val heading = r.optString("Heading", "").trim()
        val ans = stripTags(r.optString("Answer", ""))
        if (ans.isNotEmpty() && ans.length <= 200) return Answer(if (en) "$ans (DuckDuckGo)" else "$ans (DuckDuckGo)", Browser.INSTANT)
        val abs = stripTags(r.optString("AbstractText", ""))
        val src = r.optString("AbstractSource", "").trim().ifEmpty { "DuckDuckGo" }
        if (abs.length >= 40) {
            val s = clip(abs, 520)
            return Answer((if (heading.isNotEmpty()) "$heading: " else "") + s + " ($src)", Browser.INSTANT)
        }
        val def = stripTags(r.optString("Definition", ""))
        val dsrc = r.optString("DefinitionSource", "").trim().ifEmpty { "DuckDuckGo" }
        if (def.length >= 20) return Answer((if (heading.isNotEmpty()) "$heading: " else "") + clip(def, 360) + " ($dsrc)", Browser.INSTANT)
        return null
    }

    class Geo(val name: String, val region: String, val lat: Double, val lon: Double)

    fun parseGeo(body: String): Geo? {
        val r = json(body)?.optJSONArray("results")?.optJSONObject(0) ?: return null
        val name = r.optString("name", "").trim()
        if (name.isEmpty() || !r.has("latitude") || !r.has("longitude")) return null
        val lat = r.optDouble("latitude", Double.NaN)
        val lon = r.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN() || lat < -90 || lat > 90 || lon < -180 || lon > 180) return null
        val region = r.optString("admin1", "").trim().ifEmpty { r.optString("country", "").trim() }
        return Geo(name, region, lat, lon)
    }

    fun weatherWord(code: Int, en: Boolean): String = when (code) {
        0 -> if (en) "clear sky" else "saaf aasmaan"
        1 -> if (en) "mostly clear" else "zyadatar saaf"
        2 -> if (en) "partly cloudy" else "halke badal"
        3 -> if (en) "cloudy" else "badal chhaye"
        45, 48 -> if (en) "fog" else "kohra"
        51, 53, 55, 56, 57 -> if (en) "drizzle" else "halki phuhaar"
        61, 80 -> if (en) "light rain" else "halki barish"
        63, 81 -> if (en) "rain" else "barish"
        65, 82 -> if (en) "heavy rain" else "tez barish"
        66, 67 -> if (en) "freezing rain" else "thandi barish"
        71, 73, 75, 77, 85, 86 -> if (en) "snow" else "baraf"
        95 -> if (en) "thunderstorm" else "bijli ke saath toofan"
        96, 99 -> if (en) "thunderstorm with hail" else "toofan aur ole"
        else -> if (en) "unsettled weather" else "mausam badla hua"
    }

    /** Open-Meteo forecast JSON + the place it was asked for. [dayOffset] 0 = today (adds the live reading), 1 = tomorrow. */
    fun parseForecast(body: String, geo: Geo, dayOffset: Int, en: Boolean): Answer? {
        val r = json(body) ?: return null
        val daily = r.optJSONObject("daily") ?: return null
        val idx = dayOffset.coerceIn(0, 1)
        val max = daily.optJSONArray("temperature_2m_max")?.optDouble(idx, Double.NaN) ?: Double.NaN
        val min = daily.optJSONArray("temperature_2m_min")?.optDouble(idx, Double.NaN) ?: Double.NaN
        if (max.isNaN() || min.isNaN()) return null
        val rainP = daily.optJSONArray("precipitation_probability_max")?.let { if (it.length() > idx && !it.isNull(idx)) it.optInt(idx, -1) else -1 } ?: -1
        val dcode = daily.optJSONArray("weather_code")?.let { if (it.length() > idx && !it.isNull(idx)) it.optInt(idx, -1) else -1 } ?: -1
        val place = if (geo.region.isNotEmpty() && !geo.region.equals(geo.name, true)) "${geo.name} (${geo.region})" else geo.name
        val sb = StringBuilder()
        val cur = r.optJSONObject("current")
        if (dayOffset == 0 && cur != null && !cur.isNull("temperature_2m")) {
            val t = cur.optDouble("temperature_2m", Double.NaN)
            if (t.isNaN()) return null
            val feels = cur.optDouble("apparent_temperature", Double.NaN)
            val code = cur.optInt("weather_code", -1)
            val hum = cur.optInt("relative_humidity_2m", -1)
            val wind = cur.optDouble("wind_speed_10m", Double.NaN)
            if (en) {
                sb.append("$place right now: ${deg(t)}")
                if (!feels.isNaN()) sb.append(", feels like ${deg(feels)}")
                if (code >= 0) sb.append(", ${weatherWord(code, true)}")
                sb.append(". Today ${deg(min)} to ${deg(max)}")
                if (rainP >= 0) sb.append(", rain chance $rainP%")
                if (hum >= 0) sb.append(". Humidity $hum%")
                if (!wind.isNaN()) sb.append(", wind ${wind.toInt()} km/h")
                sb.append(". (Open-Meteo)")
            } else {
                sb.append("$place me abhi ${deg(t)}")
                if (!feels.isNaN()) sb.append(", mehsoos ${deg(feels)}")
                if (code >= 0) sb.append(", ${weatherWord(code, false)}")
                sb.append(". Aaj ${deg(min)} se ${deg(max)} tak")
                if (rainP >= 0) sb.append(", barish ka chance $rainP%")
                if (hum >= 0) sb.append(". Nami $hum%")
                if (!wind.isNaN()) sb.append(", hawa ${wind.toInt()} km/h")
                sb.append(". (Open-Meteo)")
            }
        } else {
            val word = if (dcode >= 0) weatherWord(dcode, en) else ""
            if (en) {
                sb.append("$place ${if (dayOffset == 0) "today" else "tomorrow"}: ${deg(min)} to ${deg(max)}")
                if (word.isNotEmpty()) sb.append(", $word")
                if (rainP >= 0) sb.append(", rain chance $rainP%")
                sb.append(". (Open-Meteo)")
            } else {
                sb.append("$place me ${if (dayOffset == 0) "aaj" else "kal"} ${deg(min)} se ${deg(max)} tak")
                if (word.isNotEmpty()) sb.append(", $word")
                if (rainP >= 0) sb.append(", barish ka chance $rainP%")
                sb.append(". (Open-Meteo)")
            }
        }
        return Answer(sb.toString(), Browser.WEATHER)
    }

    private fun deg(v: Double): String = "${Math.round(v)}°C"

    /** Google News RSS: up to [max] headlines "Title (Source)". */
    fun parseNews(body: String, query: String, en: Boolean, max: Int = 3): Answer? {
        if (!body.contains("<item")) return null
        val items = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(body).toList()
        val lines = ArrayList<String>()
        val seen = HashSet<String>()
        for (m in items) {
            val chunk = m.groupValues[1]
            var title = tagText(chunk, "title") ?: continue
            val source = tagText(chunk, "source")
            if (source != null && title.endsWith(" - $source")) title = title.removeSuffix(" - $source")
            else { val i = title.lastIndexOf(" - "); if (i > 20) title = title.substring(0, i) }
            title = title.trim()
            if (title.length < 12) continue
            val key = title.lowercase().take(40)
            if (!seen.add(key)) continue
            val t = if (title.length > 120) title.take(117).trimEnd() + "..." else title
            lines.add(if (!source.isNullOrBlank()) "$t ($source)" else t)
            if (lines.size >= max) break
        }
        if (lines.isEmpty()) return null
        val head = if (query.isBlank()) (if (en) "Top headlines" else "Taaza badi khabrein")
                   else (if (en) "Headlines on $query" else "$query ki taaza khabrein")
        val numbered = lines.withIndex().joinToString(" ") { (i, l) -> "${i + 1}) $l." }
        return Answer("$head: $numbered (Google News)", Browser.NEWS)
    }

    private fun tagText(chunk: String, tag: String): String? {
        val m = Regex("<$tag(?:\\s[^>]*)?>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(chunk) ?: return null
        return decode(m.groupValues[1]).takeIf { it.isNotBlank() }
    }

    internal fun decode(s: String): String {
        var t = s.trim()
        if (t.startsWith("<![CDATA[") && t.endsWith("]]>")) t = t.substring(9, t.length - 3)
        t = stripTags(t)
        return t.replace(Regex("\\s+"), " ").trim()
    }

    internal fun stripTags(s: String): String {
        var t = s.replace(Regex("<[^>]*>"), " ")
        t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&nbsp;", " ")
        t = t.replace(Regex("&#(\\d{1,5});")) { mr -> mr.groupValues[1].toIntOrNull()?.let { c -> if (c in 32..0xFFFF) c.toChar().toString() else " " } ?: " " }
        return t.replace(Regex("\\s+"), " ").trim()
    }

    /** Frankfurter: {"base":"USD","date":"2026-10-14","rates":{"INR":88.1}}. The amount is multiplied HERE (never trusted from the feed). */
    fun parseFx(body: String, need: Need, en: Boolean): Answer? {
        val r = json(body) ?: return null
        val rate = r.optJSONObject("rates")?.optDouble(need.to, Double.NaN) ?: return null
        if (rate.isNaN() || rate <= 0.0) return null
        val date = r.optString("date", "")
        val total = rate * need.amount
        val amt = numText(need.amount)
        val line = "$amt ${need.query} = ${numText(total, true)} ${need.to}"
        return Answer(
            if (en) "$line (ECB daily reference rate${if (date.isNotEmpty()) ", $date" else ""}; not a live bank rate). (Frankfurter)"
            else "$line (ECB ka daily reference rate${if (date.isNotEmpty()) ", $date" else ""}; bank ka live rate nahi). (Frankfurter)",
            Browser.MARKET
        )
    }

    /** CoinGecko simple/price. */
    fun parseCoin(body: String, coinId: String, en: Boolean): Answer? {
        val c = json(body)?.optJSONObject(coinId) ?: return null
        if (!c.has("inr") && !c.has("usd")) return null
        val inr = c.optDouble("inr", Double.NaN)
        val usd = c.optDouble("usd", Double.NaN)
        if (inr.isNaN() && usd.isNaN()) return null
        val ch = if (c.has("inr_24h_change")) c.optDouble("inr_24h_change", Double.NaN) else c.optDouble("usd_24h_change", Double.NaN)
        val name = COIN_NAMES[coinId] ?: coinId
        val sb = StringBuilder(name).append(": ")
        if (!inr.isNaN()) sb.append("Rs ").append(numText(inr, true))
        if (!usd.isNaN()) sb.append(if (!inr.isNaN()) " (USD " else "USD ").append(numText(usd, true)).append(if (!inr.isNaN()) ")" else "")
        if (!ch.isNaN()) sb.append(if (en) ", last 24h " else ", pichhle 24 ghante me ").append(if (ch >= 0) "+" else "").append("%.1f".format(java.util.Locale.US, ch)).append("%")
        sb.append(". (CoinGecko)")
        return Answer(sb.toString(), Browser.MARKET)
    }

    /** 100.0 -> "100", 88.1234 -> "88.12", 1234567.5 -> "1,234,567.5". */
    internal fun numText(v: Double, money: Boolean = false): String {
        val abs = Math.abs(v)
        val s = when {
            abs >= 1000 -> if (v == Math.floor(v)) "%,.0f".format(java.util.Locale.US, v) else "%,.2f".format(java.util.Locale.US, v)
            abs >= 1 -> if (v == Math.floor(v)) "%.0f".format(java.util.Locale.US, v) else "%.2f".format(java.util.Locale.US, v)
            else -> "%.4f".format(java.util.Locale.US, v)
        }
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }
}
