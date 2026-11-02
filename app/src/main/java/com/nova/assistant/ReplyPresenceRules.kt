package com.nova.assistant

/**
 * v7.99 OWNER FIRST (owner request): the AI must not talk over the owner.
 *  - After a message arrives the AI waits [WAIT_MS]. If the owner opens / swipes the notification (= saw it) or answers the chat himself during that time, the AI sends nothing.
 *  - If the owner wrote last in the chat (his own message shows in the notification), the AI stays out of that person's chat for [OWNER_TAKEOVER_MS].
 *  - The recent chat lines from the notification (including what the OWNER wrote earlier) go to the AI as read-only context, so it keeps his tone and never contradicts him.
 * Pure Kotlin (no Android classes): every rule is unit-tested. Honest limit: Android shows NOVA only the notification, not WhatsApp's blue ticks. "Seen" = the notification was opened / swiped / cancelled by the app.
 */
object ReplyPresenceRules {
    const val WAIT_MS = 25_000L
    const val STEP_MS = 500L
    const val OWNER_TAKEOVER_MS = 15 * 60_000L
    const val SEEN_TAKEOVER_MS = 3 * 60_000L
    const val OWN_ECHO_MS = 10_000L
    const val MAX_CONTEXT = 6
    const val LINE_MAX = 160

    class Line(val own: Boolean, val text: String)
    enum class Verdict { SEND, SEEN, OWNER, REPLACED }

    private val OWN_NAMES = setOf("you", "me", "aap", "tum", "main", "mai")

    /** Own message = the notification names the writer "You" or the notification's own display name. A blank writer is NEVER treated as the owner. */
    fun isOwnSender(sender: String?, selfName: String?): Boolean {
        val s = sender?.trim()?.lowercase().orEmpty()
        if (s.isEmpty()) return false
        if (s in OWN_NAMES) return true
        val me = selfName?.trim()?.lowercase().orEmpty()
        return me.isNotEmpty() && s == me
    }

    fun ownerSpokeLast(lines: List<Line>): Boolean = lines.lastOrNull()?.own == true

    /** Read-only context for the AI. Secrets are dropped, lines are short, the last [MAX_CONTEXT] stay. */
    fun context(lines: List<Line>): List<String> {
        val out = ArrayList<String>()
        for (l in lines) {
            val t = l.text.replace(Regex("\\s+"), " ").trim().take(LINE_MAX)
            if (t.isBlank()) continue
            if (SmartReplyRules.unsafeReason(t) != null) continue
            out.add((if (l.own) "OWNER: " else "PERSON: ") + t)
        }
        return out.takeLast(MAX_CONTEXT)
    }

    fun skipText(v: Verdict, hinglish: Boolean): String = when (v) {
        Verdict.SEEN -> if (hinglish) "Aapne message dekh liya, isliye AI ne jawab nahi diya" else "You already saw the message, so the AI did not reply"
        Verdict.OWNER -> if (hinglish) "Aap is chat me khud baat kar rahe ho, isliye AI ruka raha" else "You are chatting yourself, so the AI stayed out"
        Verdict.REPLACED -> if (hinglish) "Naya message aa gaya, usi ka jawab diya jayega" else "A newer message arrived; that one gets the answer"
        Verdict.SEND -> ""
    }

    /** Per sender: while active the AI stays out. */
    class Takeover {
        private val until = HashMap<String, Long>()
        private fun k(s: String) = s.lowercase().trim().take(40)
        @Synchronized fun mark(sender: String, now: Long, ms: Long) {
            if (sender.isBlank()) return
            val u = now + ms
            if ((until[k(sender)] ?: 0L) < u) until[k(sender)] = u
            if (until.size > 300) until.clear()
        }
        @Synchronized fun active(sender: String, now: Long): Boolean = (until[k(sender)] ?: 0L) > now
        @Synchronized fun clear(sender: String) { until.remove(k(sender)) }
        @Synchronized fun reset() { until.clear() }
    }

    /** Chats the AI is about to answer: the waiting thread asks "still mine and not seen?". */
    class Pending {
        private val map = HashMap<String, Long>()
        @Synchronized fun start(key: String, stamp: Long) { if (map.size > 300) map.clear(); map[key] = stamp }
        @Synchronized fun cancel(key: String, code: Long) { if (map.containsKey(key)) map[key] = code }
        @Synchronized fun state(key: String, stamp: Long): Verdict {
            val v = map[key]
            return when {
                v == stamp -> Verdict.SEND
                v == CODE_SEEN -> Verdict.SEEN
                v == CODE_OWNER -> Verdict.OWNER
                else -> Verdict.REPLACED
            }
        }
        @Synchronized fun done(key: String, stamp: Long) { if (map[key] == stamp) map.remove(key) }
        @Synchronized fun reset() { map.clear() }
    }

    const val CODE_SEEN = -1L
    const val CODE_OWNER = -2L

    /** NOVA's own reply echoes back as a notification update; it must not count as the owner talking / seeing. */
    class Sent {
        private val map = HashMap<String, Long>()
        @Synchronized fun mark(key: String, now: Long) { map[key] = now; if (map.size > 300) map.clear() }
        @Synchronized fun recent(key: String, now: Long): Boolean { val t = map[key] ?: return false; return now - t < OWN_ECHO_MS }
        @Synchronized fun reset() { map.clear() }
    }

    /** Waits up to [waitMs] in [stepMs] steps; returns early when the owner saw the chat / answered / a newer message took over. [sleep] is injected for tests. */
    fun waitVerdict(pending: Pending, key: String, stamp: Long, waitMs: Long = WAIT_MS, stepMs: Long = STEP_MS, sleep: (Long) -> Unit): Verdict {
        var t = 0L
        while (true) {
            val v = pending.state(key, stamp)
            if (v != Verdict.SEND) return v
            if (t >= waitMs) return Verdict.SEND
            sleep(stepMs)
            t += stepMs
        }
    }

    val takeover = Takeover()
    val pending = Pending()
    val sent = Sent()
}
