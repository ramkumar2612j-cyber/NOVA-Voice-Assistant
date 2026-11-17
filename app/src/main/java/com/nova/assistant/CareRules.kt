package com.nova.assistant

/**
 * v7.213 CARE: spoken phone-care requests (security scan, hang, faltu cleanup, delete).
 * Pure Kotlin, no Android classes, so it can be unit tested.
 *
 * Honesty rules:
 *  - Android apps cannot run a whole-phone virus scan. JARVIS never claims one.
 *  - JARVIS never deletes files or apps because of a voice command. Ever. It explains and lets the owner choose.
 *  - The hang answer uses only real numbers (free RAM, free storage). It never guesses which app is heavy.
 */
object CareRules {
    enum class Kind { SECURITY, HANG, CLEAN, DELETE }

    private val SECURITY_WORDS = listOf("virus", "malware", "security", "suraksha", "surakhsha", "safety", "spyware")
    private val HANG_WORDS = listOf("hang", "dheema", "dhima", "garam", "atak", "atakta", "stuck", "slow", "slow kyu")
    private val DELETE_VERBS = listOf("delete", "hatao", "mita", "remove", "uda")
    private val JUNK_WORDS = listOf("faltu", "junk", "cache", "kachra", "bekar", "extra", "storage", "saaf", "clean")
    private val SCREEN_WORDS = listOf("screen", "laser", "lazer", "reel", "video", "movie", "qr", "barcode", "wifi", "wi-fi")

    private fun tokens(n: String): List<String> = n.split(Regex("[^a-z0-9\\-]+")).filter { it.isNotBlank() }

    /** Returns the care request kind, or null when the sentence is not about phone care. */
    fun parse(text: String?): Kind? {
        val n = (text ?: "").lowercase().trim()
        if (n.isEmpty()) return null
        val t = tokens(n)
        val hasSecurity = SECURITY_WORDS.any { w -> t.contains(w) }
        val hasFullScan = t.contains("scan") && (t.contains("full") || t.contains("poora") || t.contains("pura"))
        if (hasSecurity || (hasFullScan && t.none { it in SCREEN_WORDS })) {
            return if (t.contains("scan") || t.contains("check") || t.contains("jaanch") || hasFullScan) Kind.SECURITY else null
        }
        if (HANG_WORDS.any { w -> t.contains(w) || n.contains(w) }) return Kind.HANG
        val hasJunk = JUNK_WORDS.any { t.contains(it) }
        if (DELETE_VERBS.any { t.contains(it) } && hasJunk) return Kind.DELETE
        if (hasJunk && (t.contains("phone") || t.contains("mobile") || t.contains("faltu"))) return Kind.CLEAN
        return null
    }

    /** Pure rule for the RAM reply: true when free RAM is low enough to explain a slowdown. */
    fun ramIsLow(freeMb: Long, totalMb: Long): Boolean = totalMb > 0 && freeMb * 100 / totalMb < 15
}
