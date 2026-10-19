package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.65 PART 14B-3: the guess run with a fake AI + fake search (no Android, no network). */
class MovieGuessPipelineTest {
    private val VISION = """{"candidates":[{"title":"Inception","year":"2010","basis":"text","evidence":["caption says Inception"]}],"text_seen":["Inception clip"],"scene":"a spinning top","safety":"ok"}"""
    private val GROUNDED = """{"candidates":[{"groundingMetadata":{"groundingChunks":[{"web":{"uri":"https://en.wikipedia.org/wiki/Inception","title":"Inception - Wikipedia"}},{"web":{"uri":"https://www.imdb.com/title/tt1375666/","title":"Inception (2010) - IMDb"}}]}}]}"""

    private class B(
        val gem: Pair<Int, String>, val raw: Pair<Int, String> = Pair(599, ""), val blockedS: String = "", val freeReady: Boolean = false,
        val free: Pair<Int, String> = Pair(0, ""), val web: (String) -> String? = { null }
    ) : AnalysisPipeline.Brain {
        var calls = 0; var rawCalls = 0; var freeCalls = 0; var lastJson = ""; var lastRaw = ""
        override fun en(): Boolean = false
        override fun blocked(): String = blockedS
        override fun call(json: String): Pair<Int, String> { calls++; lastJson = json; return gem }
        override fun freeVisionReady(): Boolean = freeReady
        override fun callFree(system: String, userText: String, framesB64: List<String>, stop: () -> Boolean): Pair<Int, String> { freeCalls++; return free }
        override fun callRaw(json: String, readMs: Long, deadlineMs: Long): Pair<Int, String> { rawCalls++; lastRaw = json; return raw }
        override fun webGet(url: String, ms: Int): String? = web(url)
    }

    private val frames = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
    private fun go(b: B, alive: () -> Boolean = { true }, st: MutableList<String> = ArrayList(), text: String = "Inception 2010 @filmclips") =
        MovieGuessPipeline.run(frames, text, b, alive, { s, _ -> st.add(s) })

    @Test fun happyPathHighWithSourcesAndRealStages() {
        val b = B(Pair(200, VISION), Pair(200, GROUNDED)); val st = ArrayList<String>()
        val d = go(b, st = st)
        assertEquals("OK", d.kind); val o = d.outcome!!
        assertEquals("Inception", o.title); assertEquals(MovieGuessRules.Level.HIGH, o.level); assertEquals(2, o.sources.size)
        assertEquals(listOf("READ TEXT", "VISION", "SEARCH"), st.distinct().take(3)); assertEquals("RESULT", st.last())
        assertTrue(b.lastJson.contains("AQID")); assertTrue(b.lastJson.contains("Inception 2010")); assertTrue(b.lastJson.contains("NEVER identify a real person"))
    }

    @Test fun onlyShortTitleQueriesGoToSearch() {
        val b = B(Pair(200, VISION), Pair(200, GROUNDED)); go(b)
        assertTrue(b.rawCalls in 1..3)
        assertTrue(b.lastRaw.contains("Inception"))
        assertTrue(!b.lastRaw.contains("AQID") && !b.lastRaw.contains("@filmclips") && !b.lastRaw.contains("inlineData"))   // no picture, no screen text leaves with the search
    }

    @Test fun offlineSearchGivesLowAndNoSources() {
        val d = go(B(Pair(200, VISION), Pair(599, ""), web = { null }))
        assertEquals("OK", d.kind); assertEquals(MovieGuessRules.Level.LOW, d.outcome!!.level); assertTrue(d.outcome!!.sources.isEmpty())
    }

    @Test fun noAiAndLimitStopBeforeAnyCall() {
        val a = B(Pair(200, VISION), blockedS = "NOKEY"); assertEquals("NO_AI", go(a).kind); assertEquals(0, a.calls)
        val l = B(Pair(200, VISION), blockedS = "LIMIT"); assertEquals("LIMIT", go(l).kind); assertEquals(0, l.calls)
    }

    @Test fun privateScreenSendsNoSearch() {
        val b = B(Pair(200, """{"candidates":[],"text_seen":[],"scene":"","safety":"private"}""")); val d = go(b)
        assertEquals("REFUSED", d.kind); assertEquals(0, b.rawCalls)
    }

    @Test fun nothingFoundIsAnHonestNull() {
        val b = B(Pair(200, """{"candidates":[],"text_seen":[],"scene":"night street","safety":"ok"}""")); val d = go(b)
        assertEquals("OK", d.kind); assertNull(d.outcome); assertEquals(0, b.rawCalls)
    }

    @Test fun invalidJsonTwiceIsUnreadableAfterOneRetry() {
        val b = B(Pair(200, "sorry")); assertEquals("UNREADABLE", go(b).kind); assertEquals(2, b.calls)
    }

    @Test fun stopBeforeVisionCancels() {
        val b = B(Pair(200, VISION)); assertEquals("CANCELLED", go(b, alive = { false }).kind); assertEquals(0, b.calls)
    }

    @Test fun geminiLimitFallsBackToFreeVision() {
        val b = B(Pair(429, ""), Pair(200, GROUNDED), freeReady = true, free = Pair(200, VISION)); val d = go(b)
        assertEquals("OK", d.kind); assertEquals(1, b.freeCalls); assertNotNull(d.outcome); assertEquals("Inception", d.outcome!!.title)
    }

    @Test fun noFramesIsOther() { assertEquals("OTHER", MovieGuessPipeline.run(emptyList(), "", B(Pair(200, VISION)), { true }, { _, _ -> }).kind) }
}
