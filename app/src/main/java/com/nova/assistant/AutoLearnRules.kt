package com.nova.assistant

/**
 * v6.37 AUTO LEARN. Pure Kotlin, unit tested. A good AI answer to a plain factual question is kept as a short text note,
 * so the next time the same question is asked NOVA can answer from its own notes (faster, works offline, saves API calls).
 * A note is TEXT ONLY: it can never trigger an action. It is stored only in the app's private folder, never uploaded, and the
 * owner can read / delete every note in Settings > KYA SEEKHA or switch auto-learn off.
 * What is NEVER learned: commands/actions, anything personal or sensitive (passwords, OTP, bank, numbers with 4+ digits,
 * e-mail), live or time-dependent questions (price, score, weather, news, today...), failure / offline texts.
 */
object AutoLearnRules {
    const val MIN_WORDS = 3
    const val MAX_WORDS = 20
    const val MAX_AUTO_NOTES = 100
    /** An auto note answers a later question only when it covers this much of the topic (kept notes use LearnRules.MIN_SCORE). */
    const val AUTO_MIN_SCORE = 0.8

    private val QUESTION = setOf("kya", "kaun", "kaise", "kitna", "kitni", "kitne", "kyun", "kyu", "kab", "kahan", "what", "who", "how", "why", "when", "where", "explain", "samjhao", "batao", "bata", "tell", "define", "meaning")
    private val LIVE = setOf("aaj", "abhi", "today", "now", "latest", "price", "rate", "bhav", "score", "weather", "mausam", "news", "khabar", "khabrein", "current", "live", "tomorrow", "kal", "tonight", "traffic", "match", "result", "stock", "share")
    private val PERSONAL = setOf("mera", "meri", "mere", "my", "mine", "mujhe", "humara", "password", "otp", "pin", "cvv", "aadhaar", "aadhar", "pan", "passport", "bank", "card", "account", "salary", "address", "email", "location", "ghar")
    private val ACTION = setOf("kholo", "khol", "open", "call", "delete", "hatao", "bhejo", "send", "share", "photo", "image", "banao", "bana", "make", "build", "play", "chalao", "alarm", "timer", "remind", "lock", "unlock", "install", "pay", "transfer", "order", "whatsapp", "message", "sms")
    private val FILLER = setOf("nova", "ek", "ki", "ka", "ke", "ko", "se", "me", "mein", "par", "pe", "ye", "wo", "hai", "ho", "hota", "hoti", "hain", "the", "a", "an", "of", "is", "are", "to", "in", "for", "please", "plz", "mujhe", "tum", "aap", "kar", "karo", "do", "de", "dena", "about", "does", "was", "i")
    private val BAD_ANSWER = listOf("nahi mil", "samajh nahi", "no answer", "did not catch", "could not", "not available", "failed", "offline", "try again", "dobara bolo")

    class Learned(val topic: String, val note: String)

    /** null = do not learn this one. */
    fun shouldLearn(said: String, answer: String): Learned? {
        val t = LearnRules.tokens(said)
        if (t.size < MIN_WORDS || t.size > MAX_WORDS) return null
        if (t.none { it in QUESTION }) return null
        for (w in t) if (w in ACTION || w in LIVE || w in PERSONAL) return null
        if (Regex("\\d{4,}").containsMatchIn(said) || said.contains('@')) return null
        val low = answer.lowercase()
        if (BAD_ANSWER.any { low.contains(it) }) return null
        val note = LearnRules.cleanNote(answer) ?: return null
        val words = t.filter { it !in QUESTION && it !in FILLER && it.length >= 2 }.take(6)
        if (words.none { it.length >= 3 }) return null
        return Learned(words.joinToString(" "), note)
    }

    /** true = an existing note already covers this topic (both ways), so do not store a copy. */
    fun duplicate(topic: String, existingTopics: List<String>): Boolean =
        existingTopics.any { LearnRules.score(topic, it) >= 0.9 && LearnRules.score(it, topic) >= 0.9 }

    /** Ids of the OLDEST auto notes to delete so that at most [max] remain. [autoIdsOldestFirst] = ids of notes with src "auto". */
    fun overflow(autoIdsOldestFirst: List<String>, max: Int = MAX_AUTO_NOTES): List<String> =
        if (autoIdsOldestFirst.size <= max) emptyList() else autoIdsOldestFirst.take(autoIdsOldestFirst.size - max)

    fun minScoreFor(src: String): Double = if (src == "auto") AUTO_MIN_SCORE else LearnRules.MIN_SCORE
}
