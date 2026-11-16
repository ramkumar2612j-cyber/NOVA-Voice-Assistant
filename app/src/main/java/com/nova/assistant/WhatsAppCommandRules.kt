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
        var tail = restoreTail(body, r.message, whoMatch?.range?.last?.plus(1) ?: 0)
        if (!Logic.stripWake(Logic.norm(body)).startsWith("whatsapp ")) tail = tail.replace(Regex("(?i)\\s+(?:bhejo|bhej do|bhejdo|bhejna|karo|kar do|send)\\s*$"), "").trim().ifBlank { r.message }   // v7.133: natural order keeps the user's spelling but not the trailing verb
        return Request(who, tail, business)
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
        if (!n.startsWith("whatsapp ")) return natural(n)
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

    // v7.133: natural word order "Anand ko WhatsApp bhejo ki main late aaunga" / "mummy ko whatsapp karo ki khana ban gaya" / "papa ko whatsapp pe bolo main aa raha hu".
    // Before this only the cloud AI could do it (no key / no internet = nothing). Strict: needs the literal "ko whatsapp", a short name, a real text.
    private val NATURAL = Regex("^(.+?)\\s+ko\\s+(?:whatsapp|wa)\\s+(?:(?:par|pe|pr)\\s+)?(?:(?:message|msg)\\s+)?(?:(?:bhejo|bhejna|send|bhej do|bhejdo|karo|kar do|kardo|bolo|likho)\\s+)?(?:ki\\s+)?(.+)$")
    private val TAIL_VERB = Regex("\\s+(?:bhejo|bhej do|bhejdo|bhejna|karo|kar do|send)$")
    private val VERBS = setOf("message", "msg", "bhejo", "bhejna", "send", "karo", "kar", "do", "bhej", "bhejdo", "bolo", "likho", "ki")
    private val NOT_TEXT = setOf("photo", "pic", "picture", "image", "video", "file", "document", "pdf", "voice", "audio", "sticker", "gif", "status", "location", "screenshot", "reel", "contact", "link")
    private val TIME_OR_PLAN = setOf("roz", "har", "baje", "kal", "aaj", "parso", "subah", "shaam", "raat", "dopahar", "minute", "ghante", "ghanta", "baad", "alarm", "remind", "yaad", "schedule", "meeting", "reminder")
    private val PRONOUN = setOf("usko", "unko", "isko", "inko", "wo", "woh", "usi", "unhe", "use", "ye", "yeh")

    private fun natural(n: String): Request? {
        val m = NATURAL.find(n) ?: return null
        val who = m.groupValues[1].trim()
        val ww = who.split(" ").filter { it.isNotBlank() }
        if (ww.isEmpty() || ww.size > 3 || who.any { it.isDigit() }) return null
        if (ww.any { it in TIME_OR_PLAN || it in PRONOUN || it in NOT_TEXT }) return null
        var msg = m.groupValues[2].trim().replace(TAIL_VERB, "").trim()
        val mw = msg.split(" ").filter { it.isNotBlank() }
        if (mw.isEmpty() || mw.all { it in VERBS } || mw.any { it in NOT_TEXT }) return null
        if (msg.length > 1000) return null
        return clean(who, msg)
    }

    private fun clean(to: String, message: String): Request? {
        val r = to.trim(); val m = message.trim()
        if (r.isBlank() || m.isBlank() || r.length > 80 || m.length > 1000) return null
        if (r.equals("biswan", true)) return null
        return Request(r, m)
    }
}
