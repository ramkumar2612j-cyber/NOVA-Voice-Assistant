package com.nova.assistant

/**
 * v6.99 Part 5: money talk in WhatsApp / chat auto reply. NOVA does not ask the owner; it reads the message and answers with a SAFE FIXED
 * line that fits what was asked. No AI, no amount, no UPI / account detail, no yes / no to paying. Codes (OTP, PIN, CVV, password ...) get no reply at all.
 * Pure text, no Android. Evidence level: written. Not compiled, not phone tested.
 */
object PaymentReplyRules {
    enum class Kind { SECRET, ASK_MONEY, PAID_CHECK, DETAILS, MONEY }
    data class Decision(val kind: Kind, val reply: String?)

    private val SECRET = listOf("otp", "pin", "cvv", "password", "passcode", "verification code", "aadhaar", "aadhar", "pan card", "kyc", "card number")
    private val DETAILS = listOf("upi id", "upi", "account number", "ifsc", "qr", "scanner", "bank details", "gpay number", "phonepe number", "paytm number")
    private val PAID = listOf("payment ho gaya", "payment ho gayi", "paise mile", "paise mil gaye", "mil gaya", "bhej diya", "bhej di", "kar diya", "received", "paid")
    private val ASK = listOf("paise bhej", "paisa bhej", "send money", "payment karo", "pay karo", "transfer karo", "bhej do", "udhar", "udhaar", "loan", "lend")
    private val ASK_ALWAYS_MONEY = listOf("paise bhej", "paisa bhej", "send money", "udhar", "udhaar")
    private val MONEY = listOf("paisa", "paise", "rupee", "rupees", "rs.", "\u20b9", "payment", "transfer", "amount", "bill", "upi", "gpay", "phonepe", "paytm", "salary", "fees", "fee")

    const val REPLY_ASK = "Abhi main busy hoon, ye baat thodi der mein khud dekhkar batata hoon."
    const val REPLY_PAID = "Abhi check nahi kar paaya, thodi der mein dekhkar batata hoon."
    const val REPLY_DETAILS = "Ye details yahan nahi bhej sakta, thodi der mein seedha baat karte hain."

    private fun has(l: String, w: String): Boolean =
        Regex("(?<![a-z0-9])" + Regex.escape(w) + "(?![a-z0-9])").containsMatchIn(l)

    private fun any(l: String, ws: List<String>): Boolean = ws.any { has(l, it) }

    /** null = not money talk (the normal auto-reply rules decide). */
    fun decide(text: String): Decision? {
        val l = text.lowercase().replace(Regex("\\s+"), " ").trim()
        if (l.isEmpty()) return null
        if (any(l, SECRET)) return Decision(Kind.SECRET, null)
        val money = any(l, MONEY)
        if (!money && !any(l, ASK_ALWAYS_MONEY)) return null
        return when {
            any(l, DETAILS) -> Decision(Kind.DETAILS, REPLY_DETAILS)
            any(l, PAID) -> Decision(Kind.PAID_CHECK, REPLY_PAID)
            any(l, ASK) -> Decision(Kind.ASK_MONEY, REPLY_ASK)
            else -> Decision(Kind.MONEY, REPLY_ASK)
        }
    }

    fun secretNote(): String = "Code ya password maanga gaya, scam ho sakta hai: jawab nahi diya"
    fun note(k: Kind): String = "Paise ki baat: safe fixed jawab diya, koi amount ya detail nahi bheji (" + k.name + ")"
}
