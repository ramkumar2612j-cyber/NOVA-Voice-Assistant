package com.nova.assistant

/**
 * v6.53 (owner request): NOVA answers WhatsApp / Instagram etc. messages at once, without asking, and tells the owner afterwards.
 * Pure Kotlin (no Android classes) so every rule is unit-tested. The safety limits that stay:
 *  - groups, code / OTP / password messages, messages without an inline Reply button and "2 new messages" summaries are never answered;
 *  - per sender: at most one auto reply per 10 minutes and 2 per hour (owner, 16 Oct 2026) (stops reply loops with bots);
 *  - at most 80 auto replies per day in total;
 *  - the reply text is the owner's own text (Settings), never invented, so it cannot promise money, rates or anything else.
 */
object AutoReplyRules {
    const val DEFAULT_TEXT = "Main abhi busy hoon, thodi der mein aapko wapas message karta hoon."
    const val MAX_TEXT = 300
    const val SENDER_COOLDOWN_MS = 600_000L
    const val SENDER_PER_HOUR = 2
    const val DAY_CAP = 80
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    private val SUMMARY_LINE = Regex("^(\\d+ (new )?messages?( from \\d+ chats?)?|checking for new messages|backup in progress.*|waiting for this message.*)$")

    /** Owner text, trimmed and capped; blank falls back to the default text. */
    fun cleanText(raw: String): String {
        val t = raw.replace(Regex("\\s+"), " ").trim().take(MAX_TEXT)
        return if (t.isBlank()) DEFAULT_TEXT else t
    }

    /** null = this message may get an auto reply. Otherwise a short reason code. */
    fun skipReason(isGroup: Boolean, canReply: Boolean, hidden: Boolean, text: String): String? = when {
        isGroup -> "group"
        hidden -> "code"
        text.isBlank() -> "empty"
        SUMMARY_LINE.matches(text.lowercase().trim()) -> "summary"
        !canReply -> "no_reply_button"
        else -> null
    }

    /** Reasons that are worth a line in the history (groups, empty and summary lines would only be noise). */
    fun worthLogging(reason: String): Boolean = reason == "code" || reason == "no_reply_button" || reason == "cooldown" || reason == "limit"

    fun reasonText(reason: String, hinglish: Boolean): String = when (reason) {
        "code" -> if (hinglish) "Code/password jaisa message, isliye jawab nahi diya" else "Looks like a code or password, no reply sent"
        "no_reply_button" -> if (hinglish) "Is notification me Reply button nahi tha" else "The notification had no Reply button"
        "cooldown" -> if (hinglish) "Isi bande ko abhi jawab bheja tha (10 min ruko)" else "Already replied to this person a moment ago"
        "limit" -> if (hinglish) "Jawab ki limit puri (har ghante 2, din me 80)" else "Reply limit reached (2 per hour, 80 per day)"
        else -> reason
    }

    /** In-memory rate limits. */
    class Gate {
        private val perSender = HashMap<String, ArrayList<Long>>()
        private val day = ArrayList<Long>()

        /** null = allowed (and counted). Otherwise "cooldown" or "limit". */
        @Synchronized fun allow(sender: String, now: Long): String? {
            day.removeAll { now - it >= DAY_MS }
            if (day.size >= DAY_CAP) return "limit"
            val list = perSender.getOrPut(sender.lowercase().take(40)) { ArrayList() }
            list.removeAll { now - it >= HOUR_MS }
            if (list.size >= SENDER_PER_HOUR) return "limit"
            val last = list.lastOrNull()
            if (last != null && now - last < SENDER_COOLDOWN_MS) return "cooldown"
            list.add(now)
            day.add(now)
            if (perSender.size > 300) perSender.clear()
            return null
        }

        @Synchronized fun reset() { perSender.clear(); day.clear() }
    }

    val gate = Gate()
}
