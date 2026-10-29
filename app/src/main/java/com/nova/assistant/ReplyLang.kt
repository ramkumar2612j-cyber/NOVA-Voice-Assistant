package com.nova.assistant

/**
 * v7.138 AUTO VOICE LANGUAGE. Decides, for ONE spoken reply, whether it is Hindi or English:
 *  - Hindi = any Devanagari letter, or Hindi written in Roman letters (Hinglish: "theek hai, khol raha hoon").
 *  - English = everything else.
 * English replies go to the Jarvis voice, Hindi / Hinglish replies to a Hindi voice, with no setting to change.
 * Pure Kotlin (no Android). It only picks a voice: it never approves, taps or sends anything.
 */
object ReplyLang {
    /** "hi" / "en" = language of the reply being spoken right now; null = not decided yet (Cfg falls back to the app language setting). */
    @Volatile var voiceLang: String? = null

    /** Common Hinglish function words. Words that are also normal English (to, me, do, main, so, hi, the, band, sun) are NOT in the list. */
    private val HI = setOf(
        "hai", "hain", "hu", "hoon", "ho", "tha", "thi", "hoga", "hogi", "honge", "hota", "hoti", "hote", "hona", "gaya", "gayi", "gaye",
        "raha", "rahi", "rahe", "rahega", "rahegi", "diya", "di", "liya", "kiya", "kiye", "kare", "karo", "kro", "kar", "karna", "karne", "karke",
        "karta", "karti", "karte", "karunga", "karungi", "karenge", "kardiya", "kardo", "sakta", "sakti", "sakte", "paya", "paye", "paaya",
        "nahi", "nahin", "nhi", "mat", "haan", "ji", "theek", "thik", "accha", "achha", "sahi", "galat", "zaroor",
        "kya", "kyu", "kyun", "kyunki", "kaise", "kaisa", "kaisi", "kab", "kahan", "kaun", "kon", "kitna", "kitne", "kitni", "konsa", "kaunsa",
        "aap", "aapka", "aapki", "aapke", "mujhe", "tumhe", "tumhara", "hume", "humko", "mera", "meri", "mere", "tera", "teri", "tere",
        "uska", "uski", "uske", "unka", "iska", "iski", "iske", "isko", "usko", "unko", "inko", "mai", "mein", "hum", "tum",
        "yeh", "ye", "wo", "woh", "vo", "yahan", "wahan", "idhar", "udhar",
        "ka", "ki", "ke", "ko", "se", "par", "pe", "aur", "ya", "bhi", "toh", "lekin", "magar", "isliye", "jab", "tab", "phir", "fir",
        "abhi", "kal", "aaj", "bahut", "bohot", "zyada", "kuch", "koi", "sab", "sabhi", "ek",
        "chahiye", "chahta", "chahte", "milega", "mila", "mili", "bolo", "batao", "bata", "bataiye", "suno", "dekho", "dekh", "dekhna",
        "khol", "kholo", "kholta", "kholna", "khula", "chalu", "chalao", "chalo", "ruko", "ruk", "jao", "aao", "dena", "lena",
        "wala", "wali", "wale", "samajh", "samjha", "aaya", "aayi", "dobara", "dobaara", "shuru", "khatam", "bhejo", "bheja",
        "shukriya", "dhanyawad", "namaste", "kripya", "dost", "yaar", "bhai"
    )

    fun isHindi(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val sb = StringBuilder()
        for (c in text) {
            if (c in 'ऀ'..'ॿ') return true
            sb.append(if (c.isLetter()) c.lowercaseChar() else ' ')
        }
        val words = sb.toString().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val hits = words.count { it in HI }
        return hits >= 2 || (hits >= 1 && words.size <= 3)
    }

    /** "hi" or "en" for the voice. */
    fun langOf(text: String?): String = if (isHindi(text)) "hi" else "en"
}
