package com.nova.assistant

/**
 * v7.2: live web context for the BACKUP AIs on a typed chat turn (they have no internet of their own).
 * Pure text rules only (no Android, no network): NovaService fetches the pieces and WebContext shapes them, so it is unit tested.
 */
object WebContext {
    private val TRIGGERS = setOf(
        "aaj", "abhi", "today", "latest", "news", "khabar", "headlines", "price", "rate", "bhav", "bhaav", "score", "result",
        "weather", "mausam", "stock", "sensex", "nifty", "bitcoin", "current", "currently", "now", "trending", "live", "update",
        "kaun", "who", "winner", "release", "launched", "2025", "2026", "2027"
    )
    private val PHRASES = listOf("kaun hai", "who is", "who won", "kya hua", "what happened", "kab hai", "when is", "kitna hai", "how much is", "right now")

    const val MAX_ITEM_CHARS = 1200
    const val MAX_ITEMS = 3

    /** true when the question probably needs facts from after the model's training (live / current / recent). */
    fun needsWeb(said: String): Boolean {
        val n = Logic.norm(said)
        if (n.isBlank() || n.length < 6) return false
        val w = n.split(" ").toSet()
        return w.any { it in TRIGGERS } || PHRASES.any { n.contains(it) }
    }

    /** Short search text: the question itself, trimmed to 120 chars on a word end. */
    fun queryOf(said: String): String {
        val t = said.replace(Regex("\\s+"), " ").trim()
        if (t.length <= 120) return t
        val cut = t.substring(0, 120)
        val sp = cut.lastIndexOf(' ')
        return if (sp > 40) cut.substring(0, sp) else cut
    }

    /** "(Wikipedia)" / "(DuckDuckGo)" / "(Google News)" labels found at the end of the fetched items; "Google Search" for the Gemini one. */
    fun sourceNames(items: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (it in items) {
            if (it.startsWith("Google Search:")) { out.add("Google Search"); continue }
            val m = Regex("\\(([^()]{3,40})\\)\\s*$").find(it.trim()) ?: continue
            out.add(m.groupValues[1].removeSuffix(", English source").removeSuffix(", English"))
        }
        return out.toList()
    }

    /** The WEB RESULTS block for the system prompt, or "" when nothing was found. */
    fun block(items: List<String>, en: Boolean): String {
        val clean = items.map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.length >= 20 }.take(MAX_ITEMS)
            .map { if (it.length > MAX_ITEM_CHARS) it.substring(0, MAX_ITEM_CHARS).trimEnd() else it }
        if (clean.isEmpty()) return ""
        val head = if (en) "WEB RESULTS (fetched just now, use them as facts; if they do not answer the question, say so):"
        else "WEB RESULTS (abhi nikaale gaye, inhe facts maano; agar inse jawab nahi milta to saaf bolo):"
        return head + "\n" + clean.joinToString("\n") { "- $it" }
    }

    /** Adds a "Sources:" line when the answer used web results but the AI forgot to list them. */
    fun withSources(reply: String, items: List<String>): String {
        val names = sourceNames(items)
        if (names.isEmpty() || reply.lowercase().contains("sources:") || reply.lowercase().contains("source:")) return reply
        return reply.trimEnd() + "\n\nSources: " + names.joinToString(", ")
    }
}
