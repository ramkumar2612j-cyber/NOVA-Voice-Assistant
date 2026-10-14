package com.nova.assistant

/**
 * v6.69 chat sessions, pure rules (no Android): ids, titles, limits and which stored turns may be handed back to the model.
 * A NEW chat never receives the transcript of an old one; only an explicitly OPENED chat gets its own last turns back.
 * v6.80 (Part 8): pin, rename, search, export text, context counters. Still pure, still no Android.
 */
object ChatSessionRules {
    const val MAX_CHATS = 30
    const val MAX_PINNED = 10
    const val MAX_MSGS = 500
    const val MAX_TEXT = 2000
    const val CONTEXT_MSGS = 10
    const val CONTEXT_CHARS = 3000
    const val TITLE_MAX = 36
    const val RENAME_MAX = 60
    const val QUERY_MAX = 60
    const val MAX_QUERY_TOKENS = 6
    const val DEFAULT_TITLE = "Nayi chat"
    private const val MIC = "🎤 "

    class Msg(val role: String, val text: String, val at: Long)

    /** One chat in the index. [renamed] = the owner chose the title, so the first message never overwrites it. */
    class Chat(val id: String, val title: String, val updated: Long, val count: Int, val pinned: Boolean = false, val renamed: Boolean = false) {
        fun copy(title: String = this.title, updated: Long = this.updated, count: Int = this.count, pinned: Boolean = this.pinned, renamed: Boolean = this.renamed) =
            Chat(id, title, updated, count, pinned, renamed)
    }

    /** A search result: the chat and a short piece of the matching line ("" when only the title matched). */
    class Hit(val chat: Chat, val snippet: String)

    /** Unique, short, sortable by time; salt keeps two ids made in the same millisecond apart. */
    fun newId(now: Long, salt: Int): String =
        "c" + java.lang.Long.toString(now, 36) + java.lang.Integer.toString(Math.abs(salt % 1296), 36)

    /** Ids are used as file names and come from the web page: only a-z / 0-9, 1..40 characters. Nothing else ever reaches the disk. */
    fun validId(id: String?): Boolean = id != null && id.length in 1..40 && id.all { it in 'a'..'z' || it in '0'..'9' }

    fun cleanText(raw: String): String = raw.removePrefix(MIC).trim().take(MAX_TEXT)

    /** Only what the owner said ("me") and what NOVA answered ("ai") is kept. Status lines ("sys", "mode") never are. */
    fun isStorable(role: String, text: String): Boolean = (role == "me" || role == "ai") && cleanText(text).isNotEmpty()

    fun titleFrom(text: String): String {
        val t = cleanText(text).replace(Regex("\\s+"), " ")
        return when {
            t.isEmpty() -> DEFAULT_TITLE
            t.length <= TITLE_MAX -> t
            else -> t.take(TITLE_MAX - 1).trimEnd() + "…"
        }
    }

    /** A title typed by the owner: one line, trimmed, at most RENAME_MAX characters. null = nothing usable (blank). */
    fun cleanTitle(raw: String?): String? {
        val t = (raw ?: "").replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim().take(RENAME_MAX).trim()
        return if (t.isEmpty()) null else t
    }

    fun <T> newest(list: List<T>, max: Int): List<T> = if (list.size <= max) list else list.takeLast(max)

    /**
     * The turns to give the model when an old chat is opened: the newest CONTEXT_MSGS messages, cut from the old end until they fit
     * CONTEXT_CHARS, never starting with an answer (a turn starts with the owner's message).
     */
    fun contextTurns(all: List<Msg>): List<Msg> {
        var part = newest(all, CONTEXT_MSGS)
        var total = part.sumOf { it.text.length }
        while (part.isNotEmpty() && (total > CONTEXT_CHARS || part.first().role != "me")) {
            total -= part.first().text.length
            part = part.drop(1)
        }
        return part
    }

    /** Short label for the history list, e.g. "12 messages". */
    fun countLabel(n: Int): String = if (n == 1) "1 message" else "$n messages"

    // ------------------------------------------------------------------ v6.80 list order, eviction

    /** Pinned chats first, then newest first. Stable for equal times. */
    fun sortForList(list: List<Chat>): List<Chat> =
        list.withIndex().sortedWith(compareByDescending<IndexedValue<Chat>> { it.value.pinned }.thenByDescending { it.value.updated }.thenBy { it.index }).map { it.value }

    /** The chat to drop when there are too many: the oldest NOT pinned one. null = nothing may be dropped (never happens: MAX_PINNED < MAX_CHATS). */
    fun evictionVictim(list: List<Chat>): Chat? = list.filter { !it.pinned }.minByOrNull { it.updated }

    // ------------------------------------------------------------------ v6.80 search

    /** Lower-case words of a search box, at most MAX_QUERY_TOKENS, query capped at QUERY_MAX characters. */
    fun queryTokens(raw: String?): List<String> =
        (raw ?: "").take(QUERY_MAX).lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(MAX_QUERY_TOKENS)

    /** True when EVERY word of the query is somewhere in [haystack] (case-insensitive). An empty query matches everything. */
    fun matches(haystack: String, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return true
        val h = haystack.lowercase()
        return tokens.all { h.contains(it) }
    }

    /** About 80 characters around the first query word in [text]; "" when nothing matches or there is no query. */
    fun snippet(text: String, tokens: List<String>): String {
        if (tokens.isEmpty()) return ""
        val flat = text.replace(Regex("\\s+"), " ").trim()
        val low = flat.lowercase()
        var pos = -1
        for (t in tokens) { val p = low.indexOf(t); if (p >= 0 && (pos < 0 || p < pos)) pos = p }
        if (pos < 0) return ""
        val from = maxOf(0, pos - 30)
        val to = minOf(flat.length, pos + 50)
        return (if (from > 0) "…" else "") + flat.substring(from, to) + (if (to < flat.length) "…" else "")
    }

    // ------------------------------------------------------------------ v6.80 context counters

    /** How many stored messages the model gets and how many are cut. [sent] + [cut] = [total]. */
    class Context(val total: Int, val sent: Int, val cut: Int)

    /**
     * Same rule as FallbackContext.fullWindow, counted in stored messages: the newest owner message is the question being asked now and
     * is not history; messages are taken from the newest end while they fit [maxChars] (each counted up to FallbackContext.FULL_MSG_CHARS);
     * the part sent starts with the owner, so a leading answer is cut as well.
     */
    fun contextCounts(msgs: List<Msg>, maxChars: Int): Context {
        val total = msgs.size
        var upto = msgs.size
        if (upto > 0 && msgs[upto - 1].role == "me") upto--
        while (upto > 0 && msgs[upto - 1].role == "me") upto--   // trailing owner turns are not history either (the window ends with an answer)
        var used = 0
        var start = upto
        while (start > 0) {
            val n = minOf(msgs[start - 1].text.length, FallbackContext.FULL_MSG_CHARS)
            if (used + n > maxChars && start < upto) break
            used += n
            start--
        }
        while (start < upto && msgs[start].role != "me") start++
        val sent = upto - start
        return Context(total, sent, total - sent - (total - upto))
    }

    // ------------------------------------------------------------------ v6.80 export

    private fun roleLabel(r: String) = if (r == "me") "Main" else "NOVA"

    /** Plain text for sharing / saving. [stamp] formats a time (injected so tests do not depend on the time zone). */
    fun exportText(chat: Chat, msgs: List<Msg>, stamp: (Long) -> String): String {
        val sb = StringBuilder()
        sb.append("NOVA chat: ").append(chat.title).append('\n')
        sb.append(countLabel(msgs.size)).append('\n').append('\n')
        for (m in msgs) {
            sb.append('[').append(stamp(m.at)).append("] ").append(roleLabel(m.role)).append(": ").append(m.text).append('\n')
        }
        return sb.toString()
    }

    /** A safe file name for the export, e.g. "nova-chat-torch-jala-do.txt". */
    fun exportFileName(title: String): String {
        val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30).trim('-')
        return "nova-chat-" + (if (slug.isEmpty()) "export" else slug) + ".txt"
    }
}
