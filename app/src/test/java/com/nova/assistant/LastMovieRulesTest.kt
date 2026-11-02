package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.124: "movie ka naam kya hai" after a scan is answered from the scan; a fresh-look sentence still scans again; the analyse popup phrase reads the screen. */
class LastMovieRulesTest {
    private fun saved(title: String, level: MovieGuessRules.Level, at: Long) =
        LastMovieRules.Saved(MovieGuessRules.Outcome(title, "2019", level, emptyList(), emptyList(), true), at)

    @Test fun nameFollowUpsMatch() {
        for (s in listOf("movie ka naam kya hai", "nova movie ka naam batao", "uska naam kya tha", "movie name", "film ka naam", "konsi movie thi", "naam batao uska", "what was the movie name"))
            assertTrue(s, LastMovieRules.isNameFollowUp(s))
    }

    @Test fun freshLookActorAndOtherSentencesDoNotMatch() {
        for (s in listOf("ye kon si movie hai", "dobara scan karo movie ka naam", "abhi movie ka naam batao", "hero ka naam kya hai", "movie me kaun hai", "movie ka naam batao actor",
            "movie chalao", "best movie ka naam", "gaane ka naam kya hai", "torch jala do", "", "movie ka naam kya hai ".repeat(5)))
            assertFalse(s, LastMovieRules.isNameFollowUp(s))
        assertFalse(LastMovieRules.isNameFollowUp(null))
    }

    @Test fun savedResultExpires() {
        val s = saved("Kabir Singh", MovieGuessRules.Level.HIGH, 1_000L)
        assertTrue(LastMovieRules.fresh(s, 1_000L + 60_000L))
        assertFalse(LastMovieRules.fresh(s, 1_000L + LastMovieRules.KEEP_MS + 1))
        assertFalse(LastMovieRules.fresh(null, 5L))
        assertFalse(LastMovieRules.fresh(saved("", MovieGuessRules.Level.LOW, 0L), 10L))
    }

    @Test fun answerKeepsHonestConfidence() {
        val hi = LastMovieRules.answer(saved("Kabir Singh", MovieGuessRules.Level.HIGH, 0L), false)
        assertTrue(hi, hi.contains("Kabir Singh") && hi.contains("high"))
        val lo = LastMovieRules.answer(saved("Kabir Singh", MovieGuessRules.Level.LOW, 0L), true)
        assertTrue(lo, lo.contains("not verified"))
    }

    @Test fun bareAnalyseReadsTheScreen() {
        for (s in listOf("analyse karo", "nova analyse karo", "ye analyse karo", "analyze", "laser scan karo", "nova scan karo", "analysis kro"))
            assertTrue(s, ScreenVideoRules.isBareAnalyse(s))
        for (s in listOf("business plan analyse karo", "gold ka analysis karo", "qr scan karo", "scan band karo", "ye photo analyse karo", "", "torch jala do"))
            assertFalse(s, ScreenVideoRules.isBareAnalyse(s))
        assertTrue(ScreenVideoRules.useVideoMode("com.android.chrome", "analyze screen"))
        assertEquals(false, ScreenVideoRules.useVideoMode("com.whatsapp", "screen dekho"))
    }
}
