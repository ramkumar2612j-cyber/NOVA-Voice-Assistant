package com.nova.assistant

import java.net.URLEncoder

/**
 * v7.58 PART 13C - SEARCH + COMPOSE rules (pure: no Android, no network; only MiniJson + AnalysisRules + AnalysisPipelineRules + WebSources.decode / stripTags). JUnit-able.
 * The Android side (AnalysisPipeline.kt) only hands real functions in: [runSearch] and [runCompose] take the network calls as lambdas, so tests use fakes.
 *
 * Honesty rules (never change):
 *  - A result address (URL) exists ONLY because a search tool returned it. It goes through AnalysisRules.sanitizeResults. The compose model never sees or writes a URL.
 *  - Only a short query (text, <= 80 chars, AnalysisRules.cleanQuery) leaves the phone for the search. Never the photo, the frames, the question as a whole sentence, memory or chat.
 *  - A search that did not run is never reported as "nothing found" and "nothing found" is never reported as verified.
 *  - Quota: <= 4 search calls (one per query) + 1 compose (+ 1 retry). Every call goes through the existing ApiUsage / cooldown path.
 */
object AnalysisSearchRules {
    const val MAX_SEARCH_CALLS = AnalysisRules.MAX_QUERIES          // 4
    const val MAX_COMPOSE_CALLS = 2                                  // first try + one retry
    const val SEARCH_READ_MS = 12_000L
    const val SEARCH_DEADLINE_MS = 15_000L
    const val COMPOSE_READ_MS = 22_000L
    const val COMPOSE_DEADLINE_MS = 25_000L
    const val KEYLESS_GET_MS = 3_500
    /** All key-less fetches of one analysis together. */
    const val KEYLESS_BUDGET_MS = 30_000L
    /** No NEW search after this much of the 120 s watchdog is used (frames + vision were slow); no new compose after COMPOSE_START_LIMIT_MS. */
    const val SEARCH_START_LIMIT_MS = 80_000L
    const val COMPOSE_START_LIMIT_MS = 95_000L
    const val MAX_PER_QUERY = 8
    const val MAX_RAW_BODY = 400_000
    const val MAX_RAW_RESULTS = 24
    const val MAX_OUT_TOKENS = 1024

    private fun t(en: Boolean, hi: String, e: String): String = if (en) e else hi

    fun searchAllowed(elapsedMs: Long): Boolean = elapsedMs in 0 until SEARCH_START_LIMIT_MS
    fun composeAllowed(elapsedMs: Long): Boolean = elapsedMs in 0 until COMPOSE_START_LIMIT_MS

    /** Compose is only worth a call when there is something to check AND something to check it against. */
    fun composeNeeded(v: AnalysisRules.Vision, results: List<AnalysisRules.Result>): Boolean = v.claims.isNotEmpty() && results.isNotEmpty()

    // ------------------------------------------------------------------ Gemini google_search grounding

    /** One Gemini body: ONLY the short query as text (no picture), google_search tool. (responseMimeType json is not allowed together with a tool.) */
    fun groundedRequestJson(query: String, en: Boolean): String {
        val ask = query + "\n" + t(en, "Chhota tathya-jawab do, tareekh aur number ke saath.", "Give a short factual answer with key dates and numbers.")
        return "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":" + MiniJson.quote(ask) + "}]}]," +
            "\"tools\":[{\"google_search\":{}}],\"generationConfig\":{\"temperature\":0.1,\"maxOutputTokens\":" + MAX_OUT_TOKENS + "}}"
    }

    /**
     * Real results from a grounded Gemini answer: groundingMetadata.groundingChunks[].web {uri, title}. The snippet is the answer text of the first
     * groundingSupport that points at that chunk (real model text tied to that source); "" if none. The model's own prose is NOT used as a result.
     * Anything not understood = empty list. Addresses are checked later by AnalysisRules.sanitizeResults.
     */
    fun parseGrounded(raw: String?): List<AnalysisRules.Result> {
        if (raw == null || raw.isBlank() || raw.length > MAX_RAW_BODY) return emptyList()
        val root = MiniJson.parse(raw) ?: return emptyList()
        val cand = MiniJson.at(MiniJson.field(root, "candidates"), 0) ?: return emptyList()
        val gm = MiniJson.field(cand, "groundingMetadata") ?: return emptyList()
        val chunks = MiniJson.field(gm, "groundingChunks") as? List<*> ?: return emptyList()
        val snip = HashMap<Int, String>()
        val supports = MiniJson.field(gm, "groundingSupports") as? List<*>
        if (supports != null) for (s in supports) {
            val text = MiniJson.field(MiniJson.field(s, "segment"), "text") as? String ?: continue
            val idx = MiniJson.field(s, "groundingChunkIndices") as? List<*> ?: continue
            for (x in idx) {
                val i = (x as? Double)?.toInt() ?: continue
                if (i !in snip) snip[i] = text
            }
        }
        val out = ArrayList<AnalysisRules.Result>()
        for ((i, ch) in chunks.withIndex()) {
            val web = MiniJson.field(ch, "web") ?: continue
            val uri = MiniJson.field(web, "uri") as? String ?: continue
            val title = MiniJson.field(web, "title") as? String ?: ""
            out.add(AnalysisRules.Result(AnalysisRules.clean(title, 160), AnalysisRules.clean(snip[i] ?: "", AnalysisRules.MAX_SNIPPET), uri, ""))
            if (out.size >= MAX_PER_QUERY) break
        }
        return out
    }

    // ------------------------------------------------------------------ key-less sources (Wikipedia + Google News RSS; same hosts as WebSources.ALLOWED_HOSTS)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** The two key-less lookups of one query: (role, url). Only the short query is in the address. */
    fun keylessRequests(query: String): List<Pair<String, String>> = listOf(
        Pair("wiki", WebSources.wikiUrl(query)),
        Pair("news", WebSources.newsUrl(query))
    )

    /** Wikipedia generator=search + extracts + info(url): one result (title, extract, page address). null = nothing usable. */
    fun parseWikiResult(body: String?): AnalysisRules.Result? {
        if (body == null || body.length > MAX_RAW_BODY) return null
        val root = MiniJson.parse(body) ?: return null
        val page = MiniJson.at(MiniJson.field(MiniJson.field(root, "query"), "pages"), 0) ?: return null
        if (MiniJson.field(page, "missing") == true) return null
        val title = (MiniJson.field(page, "title") as? String)?.trim().orEmpty()
        val extract = (MiniJson.field(page, "extract") as? String)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val url = MiniJson.field(page, "fullurl") as? String ?: return null
        if (title.isEmpty() || extract.length < 40) return null
        if (extract.contains("may refer to")) return null          // a disambiguation page is not a source
        return AnalysisRules.Result(AnalysisRules.clean(title, 160), AnalysisRules.clean(extract, AnalysisRules.MAX_SNIPPET), url, "")
    }

    /** Google News RSS: up to [max] items (title without the " - Source" tail, link, pubDate as date text). Title is the only snippet (a headline). */
    fun parseNewsResults(body: String?, max: Int = 3): List<AnalysisRules.Result> {
        if (body == null || body.length > MAX_RAW_BODY || !body.contains("<item")) return emptyList()
        val out = ArrayList<AnalysisRules.Result>()
        for (m in Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(body)) {
            val chunk = m.groupValues[1]
            fun tag(n: String): String? = Regex("<$n(?:\\s[^>]*)?>(.*?)</$n>", RegexOption.DOT_MATCHES_ALL).find(chunk)?.groupValues?.get(1)
                ?.let { WebSources.decode(it).trim() }?.takeIf { it.isNotBlank() }
            var title = tag("title") ?: continue
            val source = tag("source")
            if (source != null && title.endsWith(" - $source")) title = title.removeSuffix(" - $source")
            title = title.trim()
            val link = tag("link") ?: continue
            if (title.length < 12) continue
            val shown = if (source.isNullOrBlank()) title else "$title ($source)"
            out.add(AnalysisRules.Result(AnalysisRules.clean(shown, 160), "", link, AnalysisRules.clean(tag("pubDate") ?: "", 40)))
            if (out.size >= max) break
        }
        return out
    }

    /** Results of one query from the key-less sources. [ran] = at least one fetch returned a body (so "found nothing" is a real answer, not a dead network). */
    class Keyless(val results: List<AnalysisRules.Result>, val ran: Boolean)

    fun keyless(query: String, get: (String) -> String?): Keyless {
        val out = ArrayList<AnalysisRules.Result>()
        var ran = false
        for ((role, url) in keylessRequests(query)) {
            val body = get(url)
            if (body == null) continue
            ran = true
            if (role == "wiki") parseWikiResult(body)?.let { out.add(it) } else out.addAll(parseNewsResults(body))
        }
        return Keyless(out, ran)
    }

    // ------------------------------------------------------------------ the search run

    /** [results] already sanitized (<= 8, valid https, distinct). [ran] = a search service really answered at least once. [failKind] = first failure kind (OFFLINE / NOKEY / LIMIT / OTHER) or "". */
    class SearchOutcome(val results: List<AnalysisRules.Result>, val ran: Boolean, val failKind: String, val calls: Int)

    /** One result from each query in turn (round robin), so the cut at MAX_SOURCES does not drop a whole query. At most [MAX_RAW_RESULTS] go in. */
    fun interleave(lists: List<List<AnalysisRules.Result>>): List<AnalysisRules.Result> {
        val out = ArrayList<AnalysisRules.Result>()
        var round = 0
        while (out.size < MAX_RAW_RESULTS) {
            var any = false
            for (l in lists) if (round < l.size) { out.add(l[round]); any = true; if (out.size >= MAX_RAW_RESULTS) break }
            if (!any) break
            round++
        }
        return out
    }

    /**
     * Runs the search for [queries] (re-cleaned here, <= 4, one call each). [geminiRaw] = one grounded Gemini call (code, RAW body) or null when Gemini cannot be used;
     * [get] = a key-less GET (null on failure); [alive] = false stops at once (ROKO / STOP / watchdog); [now] = clock; [progress] = real (done, total).
     * Gemini failing once is not asked again; OFFLINE ends the search (every other service would fail as well).
     */
    fun runSearch(
        queries: List<String>, en: Boolean,
        geminiRaw: ((String) -> Pair<Int, String>)?, get: (String) -> String?,
        alive: () -> Boolean, now: () -> Long, progress: (Int, Int) -> Unit
    ): SearchOutcome {
        val qs = queries.map { AnalysisRules.cleanQuery(it) }.filter { it.isNotEmpty() }.distinct().take(MAX_SEARCH_CALLS)
        val lists = ArrayList<List<AnalysisRules.Result>>()
        var calls = 0
        var ran = false
        var fail = ""
        var useGemini = geminiRaw != null
        val t0 = now()
        for ((k, q) in qs.withIndex()) {
            if (!alive()) break
            progress(k, qs.size)
            var got: List<AnalysisRules.Result> = emptyList()
            if (useGemini && geminiRaw != null) {
                calls++
                val (code, body) = geminiRaw(groundedRequestJson(q, en))
                if (!alive()) break
                val o = AnalysisPipelineRules.callOutcome(code)
                if (o == "OK") { ran = true; got = parseGrounded(body) }
                else { if (fail.isEmpty()) fail = o; useGemini = false }
            }
            if (fail == "OFFLINE") break
            if (got.isEmpty() && now() - t0 < KEYLESS_BUDGET_MS) {
                val kl = keyless(q, get)
                if (!alive()) break
                if (kl.ran) ran = true
                got = kl.results
            }
            lists.add(got)
            progress(k + 1, qs.size)
        }
        val res = AnalysisRules.sanitizeResults(interleave(lists))
        return SearchOutcome(res, ran, if (ran && res.isNotEmpty()) "" else fail, calls)
    }

    // ------------------------------------------------------------------ compose

    /** Text-only Gemini body for the compose step, strict JSON answer. No picture, no address in it. */
    fun composeRequestJson(system: String, userText: String): String =
        "{\"systemInstruction\":{\"parts\":[{\"text\":" + MiniJson.quote(system) + "}]}," +
            "\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":" + MiniJson.quote(userText) + "}]}]," +
            "\"generationConfig\":{\"temperature\":0.1,\"responseMimeType\":\"application/json\",\"maxOutputTokens\":" + AnalysisPipelineRules.MAX_OUT_TOKENS + "}}"

    /** [composed] null = no usable compose answer (then every claim stays unverified). [failKind] = OFFLINE / NOKEY / LIMIT / OTHER / "" (answer came but was not valid twice). */
    class ComposeOutcome(val composed: AnalysisRules.Composed?, val failKind: String, val calls: Int)

    /** Compose with one retry. [call] = a Gemini text call (code, answer text). [results] = the SANITIZED list that is numbered 1..n in the prompt. */
    fun runCompose(
        question: String?, v: AnalysisRules.Vision, results: List<AnalysisRules.Result>, en: Boolean,
        call: (String) -> Pair<Int, String>, alive: () -> Boolean
    ): ComposeOutcome {
        val sys = AnalysisRules.composeSystemPrompt(en)
        val user = AnalysisRules.composeUserPrompt(question, v, results, en)
        var calls = 0
        for (attempt in 0 until MAX_COMPOSE_CALLS) {
            if (!alive()) return ComposeOutcome(null, "", calls)
            val ut = if (attempt == 0) user else user + AnalysisPipelineRules.retryNote(en)
            calls++
            val (code, text) = call(composeRequestJson(sys, ut))
            if (!alive()) return ComposeOutcome(null, "", calls)
            val o = AnalysisPipelineRules.callOutcome(code)
            if (o != "OK") return ComposeOutcome(null, o, calls)
            val c = AnalysisRules.parseCompose(text, results.size)
            if (c != null) return ComposeOutcome(c, "", calls)
        }
        return ComposeOutcome(null, "", calls)
    }

    // ------------------------------------------------------------------ honest limit lines (added to report.limits through AnalysisPipelineRules.withReason)

    /** The search did not run at all (so this is NOT "nothing found"). */
    fun searchNotRunLine(failKind: String, en: Boolean): String = when (failKind) {
        "OFFLINE" -> t(en, "Internet nahi hai ya bahut slow hai, isliye web par jaanch nahi hui.", "No internet or it is too slow, so the web check did not happen.")
        "LIMIT" -> t(en, "AI ki limit poori ho gayi, isliye web par jaanch nahi hui.", "The AI limit is reached, so the web check did not happen.")
        "NOKEY" -> t(en, "AI key nahi hai, isliye web par jaanch nahi hui.", "There is no AI key, so the web check did not happen.")
        "TIME" -> t(en, "Samay kam tha, isliye web par jaanch nahi hui.", "There was not enough time, so the web check did not happen.")
        else -> t(en, "Web jaanch se jawab nahi mila.", "The web check gave no answer.")
    }

    /** Sources were found but the compose step failed: they are listed, no claim is tied to them. */
    fun composeFailedLine(failKind: String, en: Boolean): String = when (failKind) {
        "OFFLINE" -> t(en, "Internet toot gaya, isliye sources dawon se jude nahi; dawe verify nahi hue.", "The internet dropped, so the sources are not tied to the claims; the claims are not verified.")
        "TIME" -> t(en, "Samay kam tha, isliye sources dawon se jude nahi; dawe verify nahi hue.", "There was not enough time, so the sources are not tied to the claims; the claims are not verified.")
        "LIMIT", "NOKEY" -> t(en, "AI abhi use nahi ho sakta, isliye sources dawon se jude nahi; dawe verify nahi hue.", "The AI cannot be used now, so the sources are not tied to the claims; the claims are not verified.")
        else -> t(en, "Sources mile par AI unhe dawon se jod nahi paya; dawe verify nahi hue.", "Sources were found but the AI could not tie them to the claims; the claims are not verified.")
    }

    /** Privacy line for the consent text: what goes out in the search. */
    fun searchPrivacyLine(en: Boolean): String = t(en,
        "Web jaanch ke liye sirf chhoti text query jaati hai, photo nahi.",
        "Only a short text query goes out for the web check, not the photo.")
}
