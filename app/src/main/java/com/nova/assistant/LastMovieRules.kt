package com.nova.assistant

/**
 * v7.124 - pure rules (no Android, no network; JUnit-able): the movie name asked AFTER a laser scan / analysis.
 *
 * Problem (owner, 27 Oct 2026): the scan finished, then "movie ka naam kya hai?" started a NEW scan. By then the movie is no longer
 * on the screen (NOVA's chat / popup is in front), so NOVA found nothing. Now the last real scan result is kept in memory for a while and a
 * follow-up name question is answered from it, with the same honest confidence. A sentence that asks to look again ("dobara", "phir se",
 * "ye", "abhi", "scan karo") still starts a fresh scan. Nothing is guessed: with no saved result this never matches.
 * Never names a person: a question about actor / hero / cast is not matched (same rule as MovieScanRules).
 */
object LastMovieRules {
    const val KEEP_MS = 10 * 60_000L

    class Saved(val outcome: MovieGuessRules.Outcome, val atMs: Long)

    private val TITLE = setOf("movie", "movies", "film", "films", "series", "serial", "anime", "cartoon", "video", "reel", "clip", "uska", "uski", "iska", "iski", "wo", "woh", "vo")
    private val NAME = setOf("naam", "nam", "name", "title", "konsi", "kaunsi", "konsa", "kaunsa", "kon", "kaun", "which", "what", "btao", "batao", "bata", "bta", "pata")
    private val AGAIN = setOf("dobara", "dubara", "phir", "fir", "again", "naya", "nayi", "new", "scan", "skan", "laser", "lazer", "ye", "yeh", "is", "iss", "abhi", "screen", "yahan", "yaha", "now", "this", "analyse", "analyze", "dekho", "dekh")
    private val PEOPLE = setOf("actor", "actress", "hero", "heroine", "hiroin", "cast", "singer", "star", "ladka", "ladki", "aadmi", "admi", "banda", "aurat", "insaan", "person", "who", "kalakar", "director")
    private val OTHER = setOf("chalao", "chala", "play", "lagao", "download", "dekhu", "dekhna", "suggest", "recommend", "best", "top", "release", "rating", "review", "gaana", "gana", "song", "music")

    private fun words(text: String?): List<String> =
        (text ?: "").lowercase().replace(Regex("[^\\p{L}\\p{N}\\p{M} ]"), " ").split(' ').filter { it.isNotEmpty() && it != "nova" }

    /** True when the sentence is a short "what was the movie called" question about the scan that just finished. */
    fun isNameFollowUp(text: String?): Boolean {
        val w = words(text)
        if (w.isEmpty() || w.size > 10) return false
        val s = w.toSet()
        if (s.none { it in NAME }) return false
        if (s.none { it in TITLE }) return false
        if (s.any { it in AGAIN || it in PEOPLE || it in OTHER }) return false
        val joined = w.joinToString(" ")
        if (joined.contains("kaun hai") || joined.contains("kon hai") || joined.contains("koun hai")) return false   // "movie me kaun hai" is a people question
        return true
    }

    fun fresh(saved: Saved?, nowMs: Long): Boolean = saved != null && saved.outcome.title.length >= 2 && nowMs - saved.atMs in 0..KEEP_MS

    private fun t(en: Boolean, hi: String, e: String): String = if (en) e else hi

    /** The answer: same honest confidence wording as the scan itself, plus a pointer to scan again. */
    fun answer(saved: Saved, en: Boolean): String =
        MovieGuessRules.speech(saved.outcome, en) + " " +
            t(en, "(Abhi ke scan se. Naya scan chahiye to bolo: nova ye kaun si movie hai.)", "(From the scan I just did. For a fresh scan say: nova which movie is this.)")
}
