package com.nova.assistant

/**
 * v7.34 PART 5: a small in-memory ring of the last ~30 AUTO decisions (SITE / IMAGE / PHOTO_EDIT / WRITE) and short UI lines
 * (website card shown, home core running, lowfx on, JS error). It answers "auto kya samjha" and is appended to the health report.
 * PRIVACY: never the user's full sentence. The subject is cut to 5 words / 40 chars and passed through ErrLog.clean (tokens, e-mails,
 * digit runs, quoted text become ***). Nothing is written to disk, nothing is uploaded. Pure Kotlin (no Android classes), unit tested.
 */
object AutoTrace {
    const val MAX = 30
    const val RATE_MS = 10_000L
    const val SUBJECT_LEN = 40
    const val UI_LEN = 60

    private class E(val at: Long, val kind: String, val subject: String, val route: String, val result: String)

    private val items = ArrayList<E>()
    private val lastUi = HashMap<String, Long>()
    private val UI_TAGS = setOf("p3", "p4", "lowfx", "photo_edit", "js_error")
    private val ASK_CUES = listOf("kya samjha", "kya samjhi", "kya samjhe", "samjha kya", "last auto", "auto decision", "auto trace", "auto log", "auto ne kya")

    /** Short and safe: first 5 words, at most SUBJECT_LEN chars, masked by ErrLog.clean. */
    fun cleanSubject(raw: String): String {
        val few = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(5).joinToString(" ")
        val c = ErrLog.clean(few)
        return if (c.length > SUBJECT_LEN) c.substring(0, SUBJECT_LEN) else c
    }

    /** kind = SITE / IMAGE / PHOTO_EDIT / WRITE / NONE, route = what NOVA did, result = started / blocked / busy / failed / hint. */
    fun add(kind: String, subject: String, route: String, result: String, now: Long = System.currentTimeMillis()) {
        val k = kind.uppercase().take(12).ifBlank { "NONE" }
        synchronized(items) {
            items.add(E(now, k, cleanSubject(subject), ErrLog.clean(route).take(24), ErrLog.clean(result).take(12)))
            while (items.size > MAX) items.removeAt(0)
        }
    }

    /**
     * One short line from the page. Returns "ok", "rate" (same tag again within 10 s, dropped) or "skip" (empty / unknown tag).
     * A js_error line is also kept in ErrLog so the health report shows it.
     */
    fun addUi(tag: String, msg: String, now: Long = System.currentTimeMillis()): String {
        val t = tag.trim().lowercase()
        if (t !in UI_TAGS) return "skip"
        val m = ErrLog.clean(msg).take(UI_LEN)
        if (m.isEmpty()) return "skip"
        synchronized(items) {
            val prev = lastUi[t]
            if (prev != null && now - prev < RATE_MS && now >= prev) return "rate"
            lastUi[t] = now
            items.add(E(now, "UI", t, "page", m))
            while (items.size > MAX) items.removeAt(0)
        }
        if (t == "js_error") ErrLog.add("UI", m, now)
        return "ok"
    }

    fun count(): Int = synchronized(items) { items.size }

    fun clear() {
        synchronized(items) { items.clear(); lastUi.clear() }
    }

    /** Newest first, a few Hinglish lines. */
    fun summary(now: Long = System.currentTimeMillis()): String {
        val copy: List<E> = synchronized(items) { ArrayList(items) }
        if (copy.isEmpty()) return "AUTO TRACE: abhi tak koi auto decision nahi hua"
        val sb = StringBuilder()
        sb.append("AUTO TRACE (newest first, last ").append(copy.size).append(", app band hone par saaf)\n")
        for (e in copy.asReversed()) {
            sb.append("- ").append(ErrLog.ago(now, e.at)).append(" | ").append(e.kind)
            if (e.subject.isNotEmpty()) sb.append(" | ").append(e.subject)
            sb.append(" | ").append(e.route).append(" | ").append(e.result).append('\n')
        }
        return sb.toString()
    }

    /** "auto kya samjha", "last auto decision". Needs the word auto + a cue; "auto reply" questions are not taken. */
    fun isAsk(text: String): Boolean {
        val n = Logic.norm(text)
        if (n.isBlank() || n.length > 60) return false
        if (!n.contains("auto") || n.contains("reply")) return false
        return ASK_CUES.any { n.contains(it) }
    }
}
