package com.nova.assistant

/**
 * Part 2: a small in-memory list of the last things that FAILED (a phone command, a Gemini call, the whole AI chain).
 * It lives only while the app process lives (nothing is written to disk). It stores a short reason, never audio and never a key:
 * any long token-like string is replaced by *** before it is kept. The owner copies it from Settings and pastes it into a chat.
 * Pure Kotlin, unit tested.
 */
object ErrLog {
    const val MAX = 30
    const val MAX_LEN = 160

    private class E(val at: Long, val src: String, val msg: String)

    private val items = ArrayList<E>()
    private val TOKEN = Regex("[A-Za-z0-9_\\-]{30,}")
    // v6.83 Part 9: nothing a person typed or received may reach the log: e-mail addresses, digit runs of 4+ (phone numbers, OTP, card-like numbers)
    // and quoted text (message bodies / typed text) are masked, not just 30+ char tokens.
    private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)+")
    private val DIGITS = Regex("\\+?\\d[\\d \\-]{2,}\\d")
    private val QUOTED = Regex("(?<![\\p{L}\\p{N}])(?:\"[^\"\\n]{2,}\"|'[^'\\n]{2,}'|\u201C[^\u201D\\n]{2,}\u201D|`[^`\\n]{2,}`)(?![\\p{L}\\p{N}])")

    /** One line, no key-like tokens, at most MAX_LEN characters (plus "..." when cut). */
    fun clean(raw: String): String {
        val oneLine = raw.replace('\n', ' ').replace('\r', ' ').trim()
        val masked = oneLine.replace(TOKEN, "***").replace(EMAIL, "***").replace(QUOTED, "\"***\"")
            .replace(DIGITS) { m -> if (m.value.count { it.isDigit() } >= 4) "#" else m.value }
        return if (masked.length > MAX_LEN) masked.substring(0, MAX_LEN) + "..." else masked
    }

    fun add(src: String, msg: String, now: Long = System.currentTimeMillis()) {
        val m = clean(msg)
        if (m.isEmpty()) return
        synchronized(items) {
            items.add(E(now, clean(src).take(30), m))
            while (items.size > MAX) items.removeAt(0)
        }
    }

    /** v7.93: like [add] but the same source + message is kept only ONCE, so a quiet error that repeats cannot push real errors out of the 30 slots. */
    fun addOnce(src: String, msg: String, now: Long = System.currentTimeMillis()) {
        val m = clean(msg)
        if (m.isEmpty()) return
        val s = clean(src).take(30)
        synchronized(items) {
            if (items.any { it.src == s && it.msg == m }) return
            items.add(E(now, s, m))
            while (items.size > MAX) items.removeAt(0)
        }
    }

    fun count(): Int = synchronized(items) { items.size }

    /** v6.30: newest message of a source (prefix match) that is not older than withinMs, else null. */
    fun latest(src: String, withinMs: Long, now: Long = System.currentTimeMillis()): String? = synchronized(items) {
        items.lastOrNull { it.src.startsWith(src) && now - it.at <= withinMs }?.msg
    }

    fun clear() {
        synchronized(items) { items.clear() }
    }

    fun ago(now: Long, at: Long): String {
        val s = ((now - at) / 1000L).coerceAtLeast(0L)
        return when {
            s < 60L -> "${s}s ago"
            s < 3600L -> "${s / 60L}m ago"
            else -> "${s / 3600L}h ago"
        }
    }

    /** Newest first. */
    fun dump(now: Long = System.currentTimeMillis()): String {
        val copy: List<E> = synchronized(items) { ArrayList(items) }
        if (copy.isEmpty()) return "RECENT ERRORS: none"
        val sb = StringBuilder()
        sb.append("RECENT ERRORS (newest first, since the app started)\n")
        for (e in copy.asReversed()) {
            sb.append("- ").append(ago(now, e.at)).append(" | ").append(e.src).append(" | ").append(e.msg).append('\n')
        }
        return sb.toString()
    }
}
