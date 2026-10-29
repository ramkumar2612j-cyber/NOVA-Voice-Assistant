package com.nova.assistant

/**
 * v7.138: JARVIS-style voice that needs NO server and NO setup: the phone's own offline British (en-GB) male voice, a little deeper and calmer.
 * Pure rules, no Android. The Android Voice API has no gender field, so male voices are found by the Google TTS en-GB voice codes (gbb, rjs, gbd).
 * This is Jarvis-LIKE (British assistant), not the cloned film voice (that one needs the Colab server, see tools/jarvis_server).
 */
object JarvisLocalVoice {
    const val PITCH = 0.85f
    const val RATE_FACTOR = 0.95f
    private val MALE_CODES = listOf("gbb", "rjs", "gbd")

    class Cand(val name: String, val language: String, val country: String, val quality: Int, val needsNetwork: Boolean)

    /** Best offline en-GB voice: a known male code first (in the order above), then higher quality; any other offline en-GB voice only as the last choice. null = no en-GB voice on the phone. */
    fun pick(all: List<Cand>): Cand? {
        var best: Cand? = null
        var bestScore = Int.MIN_VALUE
        for (c in all) {
            if (c.language.lowercase() != "en" || c.country.uppercase() != "GB" || c.needsNetwork) continue
            val n = c.name.lowercase()
            var score = c.quality
            val i = MALE_CODES.indexOfFirst { n.contains("-$it-") || n.contains("-x-$it") }
            if (i >= 0) score += 1000 - i * 100
            if (score > bestScore) { best = c; bestScore = score }
        }
        return best
    }

    fun isMale(c: Cand): Boolean { val n = c.name.lowercase(); return MALE_CODES.any { n.contains("-$it-") || n.contains("-x-$it") } }
}
