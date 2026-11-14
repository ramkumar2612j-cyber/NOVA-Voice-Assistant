package com.nova.assistant

/**
 * v7.175 INSTAGRAM DM BY VOICE: "nova instagram pe rahul ko hello bhejo", "insta dm rahul ko kal milte hain likho", "rahul ko instagram pe hi bhejo".
 * Pure Kotlin (JUnit-testable). It only PARSES and builds the plain-language job for the existing screen agent (Accessibility):
 * there is no public Instagram send API, so the agent opens Instagram, finds that one chat and types the text.
 * Strict on purpose: needs an Instagram word, a "ko" after the name and a send verb; never guesses a name or a message. WhatsApp / mail words are left to their own rules.
 */
object InstagramDmRules {
    class Req(val name: String, val message: String)

    private val APP = setOf("instagram", "insta", "instgram", "इंस्टाग्राम", "इंस्टा")
    private val VERB = setOf("bhejo", "bhej", "bhejna", "bheji", "send", "likho", "likh", "likhna", "karo", "kar", "do", "de", "dena")
    private val OTHER_APP = setOf("whatsapp", "watsapp", "wa", "telegram", "gmail", "mail", "email", "sms", "call", "reel", "story", "post", "comment")
    private val FILLER = setOf("pe", "par", "me", "mein", "se", "dm", "message", "msg", "ko", "ek", "aur", "nova")

    private fun tokens(raw: String): List<String> = Regex("[\\p{L}\\p{M}\\p{N}']+").findAll(raw.trim()).map { it.value }.toList()

    fun parse(raw: String): Req? {
        val w = tokens(Logic.stripWake(raw.trim()))
        if (w.size < 5 || w.size > 40) return null
        val low = w.map { it.lowercase() }
        if (low.none { it in APP }) return null
        if (low.any { it in OTHER_APP }) return null
        if (low.none { it in VERB }) return null
        val koAt = low.indexOf("ko")
        if (koAt < 1) return null
        // name = words before "ko" that are not app / filler words (1-3 words)
        val nameWords = w.subList(0, koAt).filterIndexed { i, _ -> low[i] !in APP && low[i] !in FILLER && low[i] !in VERB }
        if (nameWords.isEmpty() || nameWords.size > 3) return null
        // message = words after "ko", minus a leading "instagram pe / dm" and a trailing send verb
        var rest = w.subList(koAt + 1, w.size).toMutableList()
        var lr = rest.map { it.lowercase() }.toMutableList()
        while (lr.isNotEmpty() && (lr[0] in APP || lr[0] in setOf("pe", "par", "me", "mein", "dm", "message", "msg", "ko"))) { rest.removeAt(0); lr.removeAt(0) }
        while (lr.isNotEmpty() && lr.last() in (VERB + setOf("pe", "par", "dm", "message", "msg", "ko"))) { rest.removeAt(rest.size - 1); lr.removeAt(lr.size - 1) }
        val msg = rest.joinToString(" ").trim()
        if (msg.isBlank() || msg.length > 280) return null
        return Req(nameWords.joinToString(" "), msg)
    }

    /** The plain job text for the screen agent. The text is quoted data; the job names exactly ONE chat. */
    fun task(r: Req): String =
        "Instagram kholo, Messages (DM) me \"${r.name}\" ki chat kholo aur ye ek message bhejo: \"${r.message}\". Sirf isi ek chat me bhejo, kisi aur ko nahi, aur koi doosra button mat dabao."
}
