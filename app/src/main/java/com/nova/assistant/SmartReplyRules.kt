package com.nova.assistant

/**
 * v6.99 AI SMART REPLY rules (Part 9B). Pure Kotlin, unit tested.
 * Opt-in (default OFF). For a plain chat message NOVA asks the AI chain for ONE short, polite, safe reply.
 * The fixed reply (ActionLog.autoReplyText / SocialReplyRules) is always the fallback, so a reply is never empty.
 * Messages with money, OTP / codes, links, passwords or insults never go to a cloud AI: the fixed reply is used.
 */
object SmartReplyRules {
    const val MAX_IN = 300
    const val MAX_OUT = 160

    private val SENSITIVE = listOf(
        "otp", "pin", "password", "passcode", "cvv", "upi", "account number", "ifsc", "card number", "bank", "paisa", "paise",
        "rupee", "rupees", "rs.", "₹", "transfer", "payment", "loan", "aadhaar", "aadhar", "pan card", "verification code", "kyc"
    )

    /** True when this message may be shown to an AI at all. */
    fun allowedForAi(text: String): Boolean {
        val l = text.lowercase().trim()
        if (l.isBlank() || l.length > MAX_IN) return false
        if (SENSITIVE.any { it in l }) return false
        if (Regex("https?://|www\\.|\\.com\\b|\\.in\\b").containsMatchIn(l)) return false
        if (Regex("\\b\\d{4,}\\b").containsMatchIn(l)) return false      // codes, phone numbers, amounts
        return true
    }

    fun systemPrompt(en: Boolean): String =
        if (en) "You write ONE short, friendly reply (max 25 words) for the phone owner who is busy. Same language as the message. " +
            "Never promise money, never share personal data, never agree to meet or pay, no links, no emoji spam. Reply text only."
        else "Tum phone ke malik ke liye EK chhota, dostana jawab likho (max 25 shabd) kyunki wo busy hai. Message jis bhasha mein ho usi mein likho (Hinglish theek hai). " +
            "Paise ka vaada, personal jaankari, milne ya payment ki haan, link kuch nahi. Sirf jawab ka text likho."

    fun userPrompt(sender: String, text: String): String =
        "Message from " + sender.take(40).replace(Regex("[\\r\\n]+"), " ") + ": " + text.take(MAX_IN).replace(Regex("[\\r\\n]+"), " ")

    /** Clean the AI answer. null = not usable (the caller uses the fixed reply). */
    fun sanitize(raw: String?): String? {
        var s = raw?.trim() ?: return null
        s = s.removeSurrounding("\"").removeSurrounding("'").replace(Regex("[\\r\\n]+"), " ").trim()
        if (s.isBlank()) return null
        val l = s.lowercase()
        if (listOf("as an ai", "i cannot", "i can't", "language model", "sorry, i", "main ek ai", "i am an ai").any { it in l }) return null
        if (Regex("https?://|www\\.").containsMatchIn(l)) return null
        if (Regex("\\b\\d{4,}\\b").containsMatchIn(l)) return null
        if (SENSITIVE.any { it in l }) return null
        if (s.length > MAX_OUT) {
            val cut = s.take(MAX_OUT)
            val dot = maxOf(cut.lastIndexOf('.'), cut.lastIndexOf('!'), cut.lastIndexOf('?'))
            s = if (dot >= 30) cut.take(dot + 1) else cut.substringBeforeLast(' ').trim()
        }
        return s.takeIf { it.length >= 2 }
    }

    /** AI reply when usable, otherwise [fixed]. */
    fun choose(aiRaw: String?, fixed: String): String = sanitize(aiRaw) ?: fixed

    /** Reply text plus whether it came from the AI (for the history note). */
    class Picked(val text: String, val ai: Boolean)

    /** Same as [choose] but also says which one won. The text is never empty (blank [fixed] falls back to the default text). */
    fun pick(aiRaw: String?, fixed: String): Picked {
        val ai = sanitize(aiRaw)
        if (ai != null) return Picked(ai, true)
        return Picked(if (fixed.isBlank()) AutoReplyRules.DEFAULT_TEXT else fixed, false)
    }

    /**
     * True only when the AI may be asked: the owner switched smart reply ON, the social rules did not already give a fixed
     * thank-you, and the message itself is safe to show a cloud AI (no money, OTP, link, long number).
     */
    fun useAi(enabled: Boolean, socialReply: String?, text: String): Boolean =
        enabled && socialReply == null && allowedForAi(text)

    const val NOTE_AI = "AI"
    const val NOTE_FIXED = "fixed"
    const val TIMEOUT_MS = 8_000L

    fun note(ai: Boolean): String = if (ai) NOTE_AI else NOTE_FIXED
}
