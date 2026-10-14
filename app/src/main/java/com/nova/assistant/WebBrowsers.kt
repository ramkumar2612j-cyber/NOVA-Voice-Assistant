package com.nova.assistant

import com.nova.assistant.WebSources.Browser
import com.nova.assistant.WebSources.Kind
import com.nova.assistant.WebSources.Need
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * v6.97: the engine of the five key-less browsers. No Android classes here, so it is unit tested with a fake fetcher.
 * Speed rules: answers are cached (TTL per kind), requests of one need run in PARALLEL and the first good answer in preference
 * order wins, every call has a hard total budget, and a browser that failed 3 times in a row is skipped for 90 s (so a dead
 * network never slows the next command). Offline = instant answer, no waiting.
 * Honesty rules: a live need (weather / news / currency / crypto) that cannot be answered gives a plain "could not get it"
 * line and is NEVER passed to an AI that could invent numbers. A FACT / DEFINE miss returns null and the normal chain goes on.
 */
object WebBrowsers {
    const val BUDGET_MS = 3500L
    private const val PREFERRED_GRACE_MS = 1200L
    private const val MAX_BODY = 600_000
    private const val UA = "NOVAAndroidAssistant/6.97 (keyless public lookup; Android)"

    /** [ok] = a real answer; false = an honest "no answer" line for a live need. */
    class Result(val text: String, val browser: Browser?, val ok: Boolean, val cached: Boolean = false)

    fun interface Fetcher { fun get(url: String, timeoutMs: Int): String? }

    @Volatile var fetcher: Fetcher = Fetcher { url, ms -> httpGet(url, ms) }
    @Volatile var clock: () -> Long = { System.currentTimeMillis() }

    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "nova-web").also { it.isDaemon = true } }

    // ------------------------------------------------------------------ cache + breaker

    private class Cached(val expires: Long, val text: String, val browser: Browser)
    private val cache = object : LinkedHashMap<String, Cached>(64, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Cached>?): Boolean = size > 96
    }
    private val geoCache = object : LinkedHashMap<String, WebSources.Geo>(32, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, WebSources.Geo>?): Boolean = size > 64
    }
    private class Health { var fails = 0; var until = 0L; var lastOk = 0L; var lastFail = 0L; var calls = 0; var oks = 0 }
    private val health = HashMap<Browser, Health>()

    fun ttlMs(kind: Kind): Long = when (kind) {
        Kind.WEATHER -> 20L * 60_000L
        Kind.NEWS -> 10L * 60_000L
        Kind.CURRENCY -> 30L * 60_000L
        Kind.CRYPTO -> 2L * 60_000L
        Kind.DEFINE, Kind.FACT -> 12L * 3_600_000L
    }

    private fun skipped(b: Browser): Boolean = synchronized(health) { (health[b]?.until ?: 0L) > clock() }
    private fun record(b: Browser, ok: Boolean) = synchronized(health) {
        val h = health.getOrPut(b) { Health() }
        h.calls++
        if (ok) { h.fails = 0; h.oks++; h.lastOk = clock() } else { h.fails++; h.lastFail = clock(); if (h.fails >= 3) { h.until = clock() + 90_000L; h.fails = 0 } }
    }

    /** For the status card: one line per browser from REAL call results (never a guess). */
    fun statusJson(): String {
        val sb = StringBuilder("[")
        for ((i, b) in Browser.values().withIndex()) {
            val h = synchronized(health) { health[b] }
            if (i > 0) sb.append(',')
            val state = when {
                h == null || h.calls == 0 -> "unused"
                skipped(b) -> "paused"
                h.lastOk >= h.lastFail -> "ok"
                else -> "failing"
            }
            sb.append("{\"id\":\"").append(b.id).append("\",\"label\":\"").append(b.label).append("\",\"service\":\"").append(b.service)
                .append("\",\"state\":\"").append(state).append("\",\"calls\":").append(h?.calls ?: 0).append(",\"ok\":").append(h?.oks ?: 0).append('}')
        }
        return sb.append(']').toString()
    }

    fun clearForTest() { synchronized(cache) { cache.clear() }; synchronized(geoCache) { geoCache.clear() }; synchronized(health) { health.clear() } }

    // ------------------------------------------------------------------ messages

    private fun offlineText(n: Need, en: Boolean): String = when (n.kind) {
        Kind.WEATHER -> if (en) "I need the internet for the weather and it is off right now." else "Mausam ke liye internet chahiye aur abhi band hai."
        Kind.NEWS -> if (en) "I need the internet for the news and it is off right now." else "Khabar ke liye internet chahiye aur abhi band hai."
        Kind.CURRENCY -> if (en) "I need the internet for a currency rate and it is off right now." else "Currency rate ke liye internet chahiye aur abhi band hai."
        else -> if (en) "I need the internet for a live price and it is off right now." else "Live bhav ke liye internet chahiye aur abhi band hai."
    }

    private fun failText(n: Need, en: Boolean): String = when (n.kind) {
        Kind.WEATHER -> if (en) "I could not get the weather for ${n.query.ifBlank { "your city" }} right now. Check the city name or try again in a moment." else "${n.query.ifBlank { "Is shehar" }} ka mausam abhi nahi mil paya. Shehar ka naam check karo ya thodi der baad poochho."
        Kind.NEWS -> if (en) "I could not load the news right now. Please try again in a moment." else "Khabar abhi load nahi ho payi. Thodi der baad dobara poochho."
        Kind.CURRENCY -> if (n.to !in WebSources.ECB_CURRENCIES || n.query !in WebSources.ECB_CURRENCIES)
            (if (en) "My free currency source does not carry ${if (n.query !in WebSources.ECB_CURRENCIES) n.query else n.to}. It has about 30 major currencies (USD, EUR, GBP, INR, JPY ...)." else "Mere free currency source me ${if (n.query !in WebSources.ECB_CURRENCIES) n.query else n.to} nahi hai. Usme lagbhag 30 badi currency hain (USD, EUR, GBP, INR, JPY ...).")
        else (if (en) "I could not get the currency rate right now. Please try again in a moment." else "Currency rate abhi nahi mil paya. Thodi der baad dobara poochho.")
        else -> if (en) "I could not get that price right now. Please try again in a moment." else "Wo bhav abhi nahi mil paya. Thodi der baad dobara poochho."
    }

    private fun askCity(en: Boolean): String =
        if (en) "Which city? Say for example 'Jaipur weather', or set My city in Settings."
        else "Kis shehar ka? Jaise 'Jaipur ka mausam' bolo, ya Settings me Mera shehar daalo."

    // ------------------------------------------------------------------ main entry

    /**
     * null = not answered here (normal routing continues). A Result with ok=false is final for live needs.
     * [homeCity] is used when the user did not say a city.
     */
    fun run(need: Need, en: Boolean, online: Boolean, homeCity: String = "", useCache: Boolean = true): Result? {
        var n = need
        if (n.kind == Kind.WEATHER && n.query.isBlank()) {
            if (homeCity.isBlank()) return Result(askCity(en), Browser.WEATHER, false)
            n = n.copy(query = homeCity.trim())
        }
        val key = "${n.kind}|${n.query.lowercase()}|${n.to}|${n.amount}|${n.dayOffset}|${if (en) "e" else "h"}"
        val now = clock()
        if (useCache) synchronized(cache) {
            val c = cache[key]
            if (c != null && c.expires > now) return Result(c.text, c.browser, true, true)
            if (c != null) cache.remove(key)
        }
        if (!online) return if (n.kind.live) Result(offlineText(n, en), n.kind.browser, false) else null
        val ans: WebSources.Answer? = try {
            when (n.kind) {
                Kind.WEATHER -> weather(n, en)
                else -> race(n, en)
            }
        } catch (e: Throwable) { null }
        if (ans != null) {
            synchronized(cache) { cache[key] = Cached(clock() + ttlMs(n.kind), ans.text, ans.browser) }
            return Result(ans.text, ans.browser, true)
        }
        return if (n.kind.live) Result(failText(n, en), n.kind.browser, false) else null
    }

    /** Status-card test of ONE browser: a real request, never answered from the cache, each browser tested on its own. */
    fun probe(b: Browser, en: Boolean, online: Boolean, city: String): Result {
        if (!online) return Result(if (en) "Internet is off." else "Internet band hai.", b, false)
        val body: String?
        val a: WebSources.Answer?
        when (b) {
            Browser.WIKI -> { body = fetcher.get(WebSources.wikiUrl("India"), 2600); a = body?.let { WebSources.parseWiki(it, en) } }
            Browser.INSTANT -> { body = fetcher.get(WebSources.ddgUrl("Python programming language"), 2600); a = body?.let { WebSources.parseDdg(it, en) } }
            Browser.WEATHER -> return run(Need(Kind.WEATHER, city.ifBlank { "Delhi" }), en, true, city, useCache = false) ?: Result("-", b, false)
            Browser.NEWS -> return run(Need(Kind.NEWS, ""), en, true, city, useCache = false) ?: Result("-", b, false)
            Browser.MARKET -> return run(Need(Kind.CURRENCY, "USD", "INR"), en, true, city, useCache = false) ?: Result("-", b, false)
        }
        record(b, body != null && a != null)
        return if (a != null) Result(a.text, b, true) else Result(if (en) "No answer from this browser right now." else "Is browser se abhi jawab nahi aaya.", b, false)
    }

    private fun parse(role: String, body: String, n: Need, en: Boolean): WebSources.Answer? = when (role) {
        "wiki" -> WebSources.parseWiki(body, en)
        "wiktionary" -> WebSources.parseWiktionary(body, n.query, en)
        "ddg" -> WebSources.parseDdg(body, en)
        "rss" -> WebSources.parseNews(body, n.query, en)
        "fx" -> WebSources.parseFx(body, n, en)
        "coin" -> WebSources.parseCoin(body, n.query, en)
        else -> null
    }

    private fun allowed(url: String): Boolean = try {
        val u = URL(url); u.protocol == "https" && u.host in WebSources.ALLOWED_HOSTS
    } catch (e: Exception) { false }

    /** All requests of the need run at once; the first parsed answer in preference order wins (a later one only after the grace time). */
    private fun race(n: Need, en: Boolean): WebSources.Answer? {
        val reqs = WebSources.requests(n).filter { allowed(it.url) && !skipped(it.browser) }
        if (reqs.isEmpty()) return null
        val start = clock()
        val futures: List<Future<WebSources.Answer?>> = reqs.map { r ->
            pool.submit(Callable<WebSources.Answer?> {
                val body = fetcher.get(r.url, 2600)
                val a = if (body == null) null else parse(r.role, body, n, en)
                record(r.browser, body != null && (a != null || r.role != "ddg"))
                a
            })
        }
        try {
            while (clock() - start < BUDGET_MS) {
                var pendingEarlier = false
                for (f in futures) {
                    if (!f.isDone) { pendingEarlier = true; continue }
                    val a = try { f.get() } catch (e: Exception) { null }
                    if (a != null && (!pendingEarlier || clock() - start >= PREFERRED_GRACE_MS)) return a
                }
                if (futures.all { it.isDone }) {
                    for (f in futures) { val a = try { f.get() } catch (e: Exception) { null }; if (a != null) return a }
                    return null
                }
                Thread.sleep(15)
            }
            // out of budget: take anything that finished
            for (f in futures) if (f.isDone) { val a = try { f.get() } catch (e: Exception) { null }; if (a != null) return a }
            return null
        } finally {
            for (f in futures) if (!f.isDone) f.cancel(true)
        }
    }

    private fun weather(n: Need, en: Boolean): WebSources.Answer? {
        if (skipped(Browser.WEATHER)) return null
        val ck = n.query.lowercase()
        var geo = synchronized(geoCache) { geoCache[ck] }
        if (geo == null) {
            val body = fetcher.get(WebSources.geoUrl(n.query), 2500)
            record(Browser.WEATHER, body != null)
            geo = if (body == null) null else WebSources.parseGeo(body)
            if (geo == null) return null
            synchronized(geoCache) { geoCache[ck] = geo }
        }
        val url = WebSources.forecastUrl(geo.lat, geo.lon)
        if (!allowed(url)) return null
        val body = fetcher.get(url, 2500)
        record(Browser.WEATHER, body != null)
        return if (body == null) null else WebSources.parseForecast(body, geo, n.dayOffset, en)
    }

    // ------------------------------------------------------------------ the real network call

    /** HTTPS GET, hosts from WebSources.ALLOWED_HOSTS only, at most 2 redirects (also to allowed hosts only), body capped. null = any failure. */
    fun httpGet(url0: String, timeoutMs: Int): String? {
        var url = url0
        var hops = 0
        while (hops <= 2) {
            if (!allowed(url)) return null
            var conn: HttpURLConnection? = null
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                conn = c
                c.requestMethod = "GET"
                c.connectTimeout = minOf(timeoutMs, 1800)
                c.readTimeout = timeoutMs
                c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Api-User-Agent", UA)
                c.setRequestProperty("Accept", "application/json, application/xml;q=0.9, text/xml;q=0.8, */*;q=0.5")
                val code = c.responseCode
                if (code in 301..308 && code != 304) {
                    val loc = c.getHeaderField("Location") ?: return null
                    url = try { URL(URL(url), loc).toString() } catch (e: Exception) { return null }
                    hops++
                    continue
                }
                if (code !in 200..299) return null
                val out = ByteArrayOutputStream()
                c.inputStream.use { input ->
                    val buf = ByteArray(8192)
                    while (out.size() < MAX_BODY) { val r = input.read(buf); if (r < 0) break; out.write(buf, 0, r) }
                }
                return out.toString("UTF-8")
            } catch (e: Exception) {
                return null
            } finally {
                try { conn?.disconnect() } catch (e: Exception) { }
            }
        }
        return null
    }
}
