package com.nova.assistant

/**
 * v6.98 (owner request 16 Oct 2026): Instagram / WhatsApp messages and comments.
 *  - an insult or shaming message / comment: NOVA does NOT reply, tells the owner, and (Instagram) marks it "to report";
 *  - a birthday / festival wish or a normal nice comment: NOVA replies with a short fixed thank-you and tells the owner;
 *  - anything else (questions, prices, business talk, unknown): unchanged, the old auto-reply rules decide.
 * Pure Kotlin, no Android classes, unit tested. Local keyword rules: fast, no AI call, no upload. The reply text is FIXED
 * (never invented), so it cannot promise anything. Per person limit (2 per hour) lives in AutoReplyRules.Gate.
 * NOT done here: the reel / post caption is not read (only the comment text), and the Instagram "Report" taps are not automated.
 */
object SocialReplyRules {
    enum class Kind { INSULT, WISH, NORMAL, OTHER }

    class Decision(val kind: Kind, val reply: String?, val report: Boolean)

    const val THANKS_WISH = "Thank you so much 😊🙏"
    const val THANKS_NORMAL = "Thank you 😊"

    private const val IG = "com.instagram.android"
    private val WA = setOf("com.whatsapp", "com.whatsapp.w4b")
    const val MAX_LEN = 220

    private val INSULT_WORDS = setOf(
        "chutiya", "chutiye", "madarchod", "behenchod", "bhosdi", "bhosdike", "bhosda", "randi", "harami", "haramkhor", "kutta", "kutte",
        "kamina", "kamine", "gandu", "gaandu", "lund", "mc", "bc", "besharam", "nikamma", "dhokebaaz", "chor", "ullu",
        "fuck", "fucking", "bitch", "bastard", "asshole", "idiot", "stupid", "loser", "useless", "trash", "garbage", "scammer", "cheater", "thug", "fraud", "scam"
    )
    private val INSULT_PHRASES = listOf(
        "shame on you", "sharam karo", "sharam nahi", "tumhe sharam", "hate you", "mar ja", "marja", "mar jao", "go die",
        "tera baap", "teri maa", "teri ma ", "bakwas", "gandagi", "fake hai", "fake page", "dhokha"
    )
    private val WISH_PHRASES = listOf(
        "happy birthday", "hbd", "bday", "b day", "birthday", "janamdin", "janmdin", "janam din", "janmadin", "anniversary",
        "congratulations", "congrats", "mubarak", "badhai", "shubhkamnaye", "best wishes", "happy returns", "happy diwali", "happy holi", "eid"
    )
    private val NICE_WORDS = setOf(
        "nice", "great", "awesome", "superb", "love", "loved", "beautiful", "amazing", "wow", "mast", "badiya", "badhiya", "zabardast",
        "best", "good", "excellent", "perfect", "cute", "gorgeous", "stunning", "wonderful", "lovely", "pretty", "fire",
        "thanks", "thank", "thanku", "shukriya", "dhanyawad"
    )
    private val NICE_PHRASES = listOf("well done", "keep it up", "thank you", "so nice", "very nice")
    private val QUESTION_WORDS = setOf("kya", "kitna", "kitne", "kitni", "price", "rate", "kaise", "kab", "kahan", "kaha", "how", "what", "when", "where", "why", "cost", "available", "link", "order", "size")
    private val EMOJI = listOf("❤", "😍", "👍", "🔥", "👏", "😊", "🙏", "💯", "😘", "🥰", "🎉", "🎂", "💐")

    private fun norm(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) sb.append(if (ch.isLetterOrDigit()) ch else ' ')
        return " " + sb.toString().trim().split(' ').filter { it.isNotEmpty() }.joinToString(" ") + " "
    }

    fun classify(text: String): Kind {
        val raw = text.trim()
        if (raw.isEmpty() || raw.length > MAX_LEN) return Kind.OTHER
        val t = norm(raw)
        val toks = t.trim().split(' ').filter { it.isNotEmpty() }
        if (toks.any { it in INSULT_WORDS } || INSULT_PHRASES.any { t.contains(" " + it.trim() + " ") }) return Kind.INSULT
        if (raw.contains('?') || toks.any { it in QUESTION_WORDS }) return Kind.OTHER
        if (WISH_PHRASES.any { t.contains(" $it ") }) return Kind.WISH
        if (toks.any { it in NICE_WORDS } || NICE_PHRASES.any { t.contains(" $it ") }) return Kind.NORMAL
        if (toks.isEmpty() && EMOJI.any { raw.contains(it) }) return Kind.NORMAL
        if (toks.size <= 2 && EMOJI.any { raw.contains(it) }) return Kind.NORMAL
        return Kind.OTHER
    }

    /** null = this app / text is not handled here (old auto-reply rules stay in charge). */
    fun decide(pkg: String, text: String): Decision? {
        val isIg = pkg == IG
        if (!isIg && pkg !in WA) return null
        return when (classify(text)) {
            Kind.INSULT -> Decision(Kind.INSULT, null, isIg)
            Kind.WISH -> Decision(Kind.WISH, THANKS_WISH, false)
            Kind.NORMAL -> Decision(Kind.NORMAL, THANKS_NORMAL, false)
            Kind.OTHER -> null
        }
    }

    fun insultNote(report: Boolean, hinglish: Boolean): String = when {
        hinglish && report -> "Beizzati wala message/comment: jawab nahi diya, report karna hai"
        hinglish -> "Beizzati wala message: jawab nahi diya"
        report -> "Insulting message/comment: no reply sent, needs a report"
        else -> "Insulting message: no reply sent"
    }
}
