package com.nova.assistant

/**
 * v7.205 CLAUDE APP ROUTE (add-only, pure Kotlin, unit-tested).
 *
 * Owner ki maang (2026-11-16): phone par Claude app khol kar sawal wahan le jaana, chupke nahi.
 * Ye rule sirf bolta hai "jarvis claude se poocho: <sawal>". JARVIS:
 *  1. Claude app ko normal tarah kholta hai (jaise koi bhi app), owner ke phone par.
 *  2. Sawal clipboard par rakhta hai, taaki owner Claude me paste karke bhej sake.
 *  3. Kuch bhi khud se send nahi karta. Jawab owner Claude app me hi padhta hai.
 * HARD DENY (paisa / bank / password / OTP / PIN / CVV) wale sawal yahan nahi jaate.
 */
internal object ClaudeAppRules {
    const val PACKAGE = "com.anthropic.claude"

    private val TRIGGER = Regex("^\\s*(jarvis\\s+)?(claude\\s+(se|ko|pe|par)\\s+(poochh?o|pucho|puchho|bolo|batao|kaho|le\\s*jao|khol)|claude\\s+khol)\\b", RegexOption.IGNORE_CASE)

    /** true if the sentence asks to use the Claude app. */
    fun isRoute(said: String): Boolean = TRIGGER.containsMatchIn(said.trim())

    private val FILLER = setOf("do", "karo", "dijiye", "kar do", "please", "plz", "de do", "")

    /** The question after the trigger words (may be empty, e.g. "claude khol do"). */
    fun questionOf(said: String): String {
        val m = TRIGGER.find(said.trim()) ?: return ""
        val rest = said.trim().substring(m.range.last + 1).trimStart(' ', ':', ',', '-', '.').trim().take(800)
        // "claude khol do" / "claude khol dijiye": these only open the app, the rest is a filler, not a question
        return if (rest.lowercase() in FILLER) "" else rest
    }

    /** Refuse payment / secret questions (same owner rule as everywhere else). */
    fun isDenied(question: String): Boolean = TaskManagerRules.isDenied(question)

    fun denyReply(en: Boolean): String = if (en) "I will not send payment, bank, password, OTP, PIN or CVV questions to Claude. Ask those yourself."
        else "Paisa, bank, password, OTP, PIN ya CVV wale sawal main Claude ko nahi bhejta. Ye aap khud poochna."

    fun openedText(question: String, en: Boolean): String = when {
        question.isBlank() && en -> "Claude app is open."
        question.isBlank() -> "Claude app khul gayi."
        en -> "Claude app is open. Your question is copied; paste it there and send: \"$question\""
        else -> "Claude app khul gayi. Aapka sawal copy ho gaya hai, wahan paste karke bhejo: \"$question\""
    }
}
