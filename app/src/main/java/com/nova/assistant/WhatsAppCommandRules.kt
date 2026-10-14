package com.nova.assistant

/** Strict local parser for explicit WhatsApp send commands. It never guesses a contact or message. */
object WhatsAppCommandRules {
    data class Request(val recipient: String, val message: String, val business: Boolean = false)



    fun parse(input: String): Request? {
        // "WhatsApp Business <name> ..." is a different Android app (com.whatsapp.w4b): keep that identity and never
        // let "business" become part of the contact name.
        val stripped = Logic.stripWake(input.trim())
        val bizMatch = Regex("^(?:whatsapp|wa)\\s+business\\s+(.+)$", RegexOption.IGNORE_CASE).find(stripped)
        val business = bizMatch != null
        val body = if (bizMatch != null) "WhatsApp " + bizMatch.groupValues[1] else input
        val r = parseNorm(body) ?: return null
        // Logic.norm lowercases and strips punctuation / non-Latin script: give back the user's exact text.
        val whoMatch = locate(body, r.recipient, 0)
        val who = whoMatch?.value ?: r.recipient
        return Request(who, restoreTail(body, r.message, whoMatch?.range?.last?.plus(1) ?: 0), business)
    }

    /** Finds [norm] (lower-cased, punctuation-free words) inside [raw] and returns the user's original spelling. */
    private fun locate(raw: String, norm: String, start: Int): MatchResult? {
        val words = norm.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        val sep = "[^\\p{L}\\p{N}%]+"
        return Regex(words.joinToString(sep) { Regex.escape(it) }, RegexOption.IGNORE_CASE).find(raw, start)
    }
    /** The message is always the last part of the command, so keep everything to the end (trailing "!" "?" etc.). */
    private fun restoreTail(raw: String, norm: String, start: Int): String {
        val m = locate(raw, norm, start) ?: return norm
        return raw.substring(m.range.first).trim()
    }

    private fun parseNorm(input: String): Request? {
        val n = Logic.stripWake(Logic.norm(input))
        if (!n.startsWith("whatsapp ")) return null
        val rest = n.removePrefix("whatsapp ").trim()
        if (rest.isBlank()) return null
        val explicit = Regex("^(.+?)\\s+ko\\s+(?:message\\s+)?(?:bhejo|bhejna|send)\\s+(.+)$").find(rest)
        if (explicit != null) return clean(explicit.groupValues[1], explicit.groupValues[2])
        val explicit2 = Regex("^(.+?)\\s+ko\\s+(?:whatsapp\\s+)?(?:par\\s+)?message\\s+(.+)$").find(rest)
        if (explicit2 != null) return clean(explicit2.groupValues[1], explicit2.groupValues[2])
        val send = Regex("^(.+?)\\s+(?:bhejo|bhejna|send)\\s+(.+)$").find(rest)
        if (send != null) return clean(send.groupValues[1], send.groupValues[2])
        // Exact requested short form: WhatsApp <two-word contact> <message>.
        // Contact resolution against the real Contacts database remains authoritative.
        val words = rest.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size >= 3) return clean(words.take(2).joinToString(" "), words.drop(2).joinToString(" "))
        return null
    }

    private fun clean(to: String, message: String): Request? {
        val r = to.trim(); val m = message.trim()
        if (r.isBlank() || m.isBlank() || r.length > 80 || m.length > 1000) return null
        if (r.equals("biswan", true)) return null
        return Request(r, m)
    }
}
