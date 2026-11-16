package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaserMathTest {
    @Test fun parsesRealStages() {
        assertEquals("SEARCH", LaserMath.parse("SEARCH 55").name); assertEquals(55, LaserMath.parse("SEARCH 55").percent)
        assertEquals("READ TEXT", LaserMath.parse("READ TEXT 10").name); assertEquals(10, LaserMath.parse("READ TEXT 10").percent)
        assertEquals("RESULT", LaserMath.parse("RESULT 100").name)
    }
    @Test fun blankOrUnknownIsCapture() {
        assertEquals("CAPTURE", LaserMath.parse("").name); assertEquals("CAPTURE", LaserMath.parse("WHAT 5").name)
        assertEquals(0, LaserMath.parse("").percent)
    }
    @Test fun percentClamps() { assertEquals(100, LaserMath.parse("SEARCH 900").percent); assertEquals(0, LaserMath.parse("VISION -4").percent) }
    @Test fun stageOrder() {
        assertEquals(listOf("CAPTURE", "READ TEXT", "VISION", "SEARCH", "RESULT"), LaserMath.STAGES)
        assertEquals(3, LaserMath.stageIndex("SEARCH")); assertEquals(0, LaserMath.stageIndex("nope"))
    }
    @Test fun sweepGoesTopToBottomThenRestarts() {
        assertEquals(0f, LaserMath.sweep(0L), 0.0001f)
        assertTrue(LaserMath.sweep(1300L) in 0.49f..0.51f)
        assertTrue(LaserMath.sweep(LaserMath.SWEEP_MS - 1) > 0.99f)
        assertEquals(0f, LaserMath.sweep(LaserMath.SWEEP_MS), 0.0001f)
    }
    @Test fun simpleMode() {
        assertTrue(LaserMath.simple(true, 1f)); assertTrue(LaserMath.simple(false, 0f)); assertFalse(LaserMath.simple(false, 1f))
        assertEquals(0.4f, LaserMath.staticLine(40), 0.0001f)
    }
    @Test fun glitchIsRareAndBounded() {
        var hits = 0
        for (t in 0L until 9900L step 90L) { val v = LaserMath.glitchDx(t, 6f); assertTrue(v == 0f || v == 6f || v == -6f); if (v != 0f) hits++ }
        assertTrue(hits in 5..15)
    }
    @Test fun glyphIsStableAndPrintable() {
        assertEquals(LaserMath.glyph(3, 4, 9L), LaserMath.glyph(3, 4, 9L))
        for (c in 0..20) for (r in 0..20) assertTrue(LaserMath.glyph(c, r, 5L).code in 33..126)
    }
    @Test fun labelsBothLanguages() {
        for (s in LaserMath.STAGES) { assertTrue(LaserMath.label(s, true).isNotBlank()); assertTrue(LaserMath.label(s, false).isNotBlank()) }
        assertEquals("ROKO", LaserMath.stopText(false)); assertEquals("STOP", LaserMath.stopText(true))
    }
}

class MovieCardTest {
    private fun r(u: String) = AnalysisRules.Result("T", "", u, "")
    @Test fun cardShowsNameConfidenceAndDomainsOnly() {
        val o = MovieGuessRules.Outcome("Inception", "2010", MovieGuessRules.Level.HIGH, listOf(r("https://www.imdb.com/title/tt1375666/"), r("https://en.wikipedia.org/wiki/Inception")), listOf("title on screen"), true)
        val c = MovieGuessRules.card(o, true)
        assertTrue(c.contains("Inception (2010)")); assertTrue(c.contains("HIGH")); assertTrue(c.contains("imdb.com, en.wikipedia.org"))
        assertFalse(c.contains("http")); assertFalse(c.contains("/title/"))
    }
    @Test fun lowIsMarkedNotVerified() {
        val o = MovieGuessRules.Outcome("X", "", MovieGuessRules.Level.LOW, emptyList(), emptyList(), true)
        assertTrue(MovieGuessRules.card(o, true).contains("not verified")); assertTrue(MovieGuessRules.card(o, false).contains("verify nahi hua"))
    }
    @Test fun nullAndHistory() {
        assertTrue(MovieGuessRules.card(null, true).isNotBlank()); assertEquals("movie scan: no name", MovieGuessRules.historyLine(null))
        val o = MovieGuessRules.Outcome("Dune", "2021", MovieGuessRules.Level.MEDIUM, listOf(r("https://a.com/x")), emptyList(), true)
        assertEquals("movie scan: Dune (2021) | MEDIUM | 1 src", MovieGuessRules.historyLine(o))
    }
}
