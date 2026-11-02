package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.58 Part 13C: search + compose rules with fake network functions. Expected values worked out by hand. NOT RUN yet (owner rule: one compile + JUnit run after 13D). */
class AnalysisSearchRulesTest {
    private fun vis(claims: List<String> = listOf("Sale 50% ka offer hai")) =
        AnalysisRules.Vision("s", listOf("board"), emptyList(), claims, emptyList(), emptyList(), "none", 0)

    private val GROUNDED = """{"candidates":[{"content":{"parts":[{"text":"x"}]},"groundingMetadata":{"groundingChunks":[
        {"web":{"uri":"https://example.com/a","title":"example.com"}},{"web":{"uri":"https://wiki.org/b","title":"wiki.org"}}],
        "groundingSupports":[{"segment":{"text":"Sale 50 percent hai"},"groundingChunkIndices":[1]}]}}]}"""
    private val WIKI = """{"query":{"pages":[{"title":"Taj Mahal","extract":"The Taj Mahal is an ivory-white marble mausoleum in Agra, India, built long ago.","fullurl":"https://en.wikipedia.org/wiki/Taj_Mahal"}]}}"""
    private val RSS = "<rss><channel><item><title>Big sale at store today - Times</title><link>https://news.google.com/rss/articles/abc</link>" +
        "<pubDate>Sat, 18 Oct 2026 08:00:00 GMT</pubDate><source url=\"https://t.com\">Times</source></item></channel></rss>"
    private val COMPOSED = """{"summary":"Board par sale hai","verified":[{"claim":"Sale 50% ka offer hai","src":[1]}],"unverified":[]}"""

    // ---------------------------------------------------------------- grounded parse + request

    @Test fun groundedRequestHasOnlyQueryAndTool() {
        val j = AnalysisSearchRules.groundedRequestJson("store sale offer", true)
        assertTrue(j.contains("google_search"))
        assertTrue(j.contains("store sale offer"))
        assertFalse(j.contains("inlineData"))
        assertFalse(j.contains("responseMimeType"))      // not allowed together with a tool
    }

    @Test fun parseGroundedTakesChunksAndSupportSnippets() {
        val r = AnalysisSearchRules.parseGrounded(GROUNDED)
        assertEquals(2, r.size)
        assertEquals("https://example.com/a", r[0].url)
        assertEquals("example.com", r[0].title)
        assertEquals("", r[0].snippet)
        assertEquals("Sale 50 percent hai", r[1].snippet)
        assertEquals("", r[1].date)
    }

    @Test fun parseGroundedRejectsEverythingElse() {
        assertEquals(0, AnalysisSearchRules.parseGrounded(null).size)
        assertEquals(0, AnalysisSearchRules.parseGrounded("").size)
        assertEquals(0, AnalysisSearchRules.parseGrounded("not json").size)
        assertEquals(0, AnalysisSearchRules.parseGrounded("""{"candidates":[{"content":{}}]}""").size)   // answer text alone is never a source
        assertEquals(0, AnalysisSearchRules.parseGrounded("""{"candidates":[{"groundingMetadata":{"groundingChunks":[{"web":{"title":"no uri"}}]}}]}""").size)
    }

    @Test fun badAddressesDieInSanitize() {
        val raw = AnalysisSearchRules.parseGrounded("""{"candidates":[{"groundingMetadata":{"groundingChunks":[
            {"web":{"uri":"http://plain.com/x","title":"a"}},{"web":{"uri":"https://127.0.0.1/x","title":"b"}},{"web":{"uri":"https://ok.com/x","title":"c"}}]}}]}""")
        assertEquals(3, raw.size)
        assertEquals(listOf("https://ok.com/x"), AnalysisRules.sanitizeResults(raw).map { it.url })
    }

    // ---------------------------------------------------------------- key-less parsers

    @Test fun parseWikiResultNeedsTitleExtractAndUrl() {
        val r = AnalysisSearchRules.parseWikiResult(WIKI)
        assertNotNull(r)
        assertEquals("Taj Mahal", r!!.title)
        assertEquals("https://en.wikipedia.org/wiki/Taj_Mahal", r.url)
        assertNull(AnalysisSearchRules.parseWikiResult("""{"query":{"pages":[{"title":"X","extract":"short"}]}}"""))
        assertNull(AnalysisSearchRules.parseWikiResult("""{"query":{"pages":[{"missing":true,"title":"X"}]}}"""))
        assertNull(AnalysisSearchRules.parseWikiResult(null))
    }

    @Test fun parseNewsResultsKeepsLinkAndDate() {
        val r = AnalysisSearchRules.parseNewsResults(RSS)
        assertEquals(1, r.size)
        assertEquals("Big sale at store today (Times)", r[0].title)
        assertEquals("https://news.google.com/rss/articles/abc", r[0].url)
        assertEquals("Sat, 18 Oct 2026 08:00:00 GMT", r[0].date)
        assertEquals(0, AnalysisSearchRules.parseNewsResults("<html>no items</html>").size)
    }

    // ---------------------------------------------------------------- runSearch (fake network)

    private fun run(
        queries: List<String>, gem: ((String) -> Pair<Int, String>)?, get: (String) -> String?,
        alive: () -> Boolean = { true }, prog: MutableList<Pair<Int, Int>> = ArrayList()
    ) = AnalysisSearchRules.runSearch(queries, false, gem, get, alive, { 0L }, { d, t -> prog.add(Pair(d, t)) })

    @Test fun geminiGroundingOneCallPerQuery() {
        var n = 0
        val prog = ArrayList<Pair<Int, Int>>()
        val o = run(listOf("store sale offer", "bus route 42"), { n++; Pair(200, GROUNDED) }, { null }, prog = prog)
        assertEquals(2, n)
        assertEquals(2, o.calls)
        assertTrue(o.ran)
        assertEquals("", o.failKind)
        assertEquals(2, o.results.size)                      // same two addresses twice: duplicates removed
        assertEquals(listOf(Pair(0, 2), Pair(1, 2), Pair(1, 2), Pair(2, 2)), prog)
    }

    @Test fun neverMoreThanFourSearchCalls() {
        var n = 0
        run(listOf("aaa one", "bbb two", "ccc three", "ddd four", "eee five", "fff six"), { n++; Pair(200, GROUNDED) }, { null })
        assertEquals(4, n)
    }

    @Test fun offlineIsNotNothingFound() {
        var gets = 0
        val o = run(listOf("store sale offer"), { Pair(598, "") }, { gets++; null })
        assertFalse(o.ran)
        assertEquals("OFFLINE", o.failKind)
        assertEquals(0, gets)                                // offline: no key-less try either
        assertEquals(0, o.results.size)
    }

    @Test fun geminiLimitFallsBackToKeylessAndGeminiIsAskedOnce() {
        var n = 0
        val o = run(listOf("taj mahal", "agra fort"), { n++; Pair(429, "") }, { u -> if (u.contains("wikipedia.org")) WIKI else null })
        assertEquals(1, n)
        assertTrue(o.ran)
        assertEquals(1, o.results.size)                      // same wiki page for both queries
        assertEquals("", o.failKind)
    }

    @Test fun keylessNothingFoundIsRanButEmpty() {
        val o = run(listOf("store sale offer"), null, { "{}" })
        assertTrue(o.ran)
        assertEquals(0, o.results.size)
        val dead = run(listOf("store sale offer"), null, { null })
        assertFalse(dead.ran)
    }

    @Test fun stopEndsAtOnce() {
        var n = 0
        val o = run(listOf("store sale offer"), { n++; Pair(200, GROUNDED) }, { null }, alive = { false })
        assertEquals(0, n)
        assertEquals(0, o.calls)
    }

    @Test fun interleaveTakesOneFromEachQueryFirst() {
        fun r(s: String) = AnalysisRules.Result(s, "", "https://$s.com/x", "")
        val out = AnalysisSearchRules.interleave(listOf(listOf(r("a1"), r("a2")), listOf(r("b1")), emptyList()))
        assertEquals(listOf("a1", "b1", "a2"), out.map { it.title })
    }

    // ---------------------------------------------------------------- compose

    private val one = listOf(AnalysisRules.Result("T", "S", "https://site.com/p", ""))

    @Test fun composeRequestIsTextOnlyWithoutAddress() {
        val sys = AnalysisRules.composeSystemPrompt(true)
        val user = AnalysisRules.composeUserPrompt("q", vis(), one, true)
        val j = AnalysisSearchRules.composeRequestJson(sys, user)
        assertFalse(j.contains("inlineData"))
        assertFalse(j.contains("https://"))                  // the result address never goes to the model
        assertTrue(j.contains("responseMimeType"))
    }

    @Test fun composeOkFirstTry() {
        var n = 0
        val o = AnalysisSearchRules.runCompose("q", vis(), one, false, { n++; Pair(200, COMPOSED) }, { true })
        assertEquals(1, n)
        assertNotNull(o.composed)
        assertEquals(listOf(1), o.composed!!.verified[0].src)
    }

    @Test fun composeRetriesOnceThenGivesUp() {
        var n = 0
        val ok = AnalysisSearchRules.runCompose("q", vis(), one, false, { n++; if (n == 1) Pair(200, "garbage") else Pair(200, COMPOSED) }, { true })
        assertEquals(2, n); assertNotNull(ok.composed)
        n = 0
        val bad = AnalysisSearchRules.runCompose("q", vis(), one, false, { n++; Pair(200, "garbage") }, { true })
        assertEquals(2, n); assertNull(bad.composed); assertEquals("", bad.failKind)
    }

    @Test fun composeNumberOutOfRangeStaysUnverified() {
        val far = """{"summary":"s","verified":[{"claim":"Sale 50% ka offer hai","src":[5]}],"unverified":[]}"""
        val o = AnalysisSearchRules.runCompose("q", vis(), one, false, { Pair(200, far) }, { true })
        val rep = AnalysisRules.buildReport(vis(), o.composed, one, 1, false, true, false)
        assertEquals(0, rep.verified.size)
        assertEquals(listOf("Sale 50% ka offer hai"), rep.unverified)
    }

    @Test fun composeFailureKindsAndStop() {
        var n = 0
        val off = AnalysisSearchRules.runCompose("q", vis(), one, false, { n++; Pair(598, "") }, { true })
        assertEquals(1, n); assertNull(off.composed); assertEquals("OFFLINE", off.failKind)
        val stop = AnalysisSearchRules.runCompose("q", vis(), one, false, { n++; Pair(200, COMPOSED) }, { false })
        assertNull(stop.composed); assertEquals(1, n)
    }

    @Test fun composeOnlyWhenThereIsSomethingToCheck() {
        assertTrue(AnalysisSearchRules.composeNeeded(vis(), one))
        assertFalse(AnalysisSearchRules.composeNeeded(vis(), emptyList()))
        assertFalse(AnalysisSearchRules.composeNeeded(vis(emptyList()), one))
    }

    // ---------------------------------------------------------------- time + honest lines

    @Test fun startLimitsFitInsideTheWatchdog() {
        assertTrue(AnalysisSearchRules.searchAllowed(0))
        assertTrue(AnalysisSearchRules.searchAllowed(79_999))
        assertFalse(AnalysisSearchRules.searchAllowed(80_000))
        assertTrue(AnalysisSearchRules.composeAllowed(94_999))
        assertFalse(AnalysisSearchRules.composeAllowed(95_000))
        assertTrue(AnalysisSearchRules.COMPOSE_START_LIMIT_MS + AnalysisSearchRules.COMPOSE_DEADLINE_MS <= AnalysisPipelineRules.WATCHDOG_MS + 5_000L)
    }

    @Test fun searchRanButFoundNothingSaysSo() {
        val hi = AnalysisRules.buildReport(vis(), null, emptyList(), 1, false, true, false)
        assertTrue(hi.limits.contains("Search me kuch nahi mila, isliye koi dawa verify nahi hua."))
        assertEquals(1, hi.unverified.size)
        val en = AnalysisRules.buildReport(vis(), null, emptyList(), 1, false, true, true)
        assertTrue(en.limits.contains("The search found nothing, so no claim is verified."))
        val notRun = AnalysisRules.buildReport(vis(), null, emptyList(), 1, false, false, false)
        assertFalse(notRun.limits.contains("Search me kuch nahi mila, isliye koi dawa verify nahi hua."))   // did not run is a different line
    }

    @Test fun notRunLinesDiffer() {
        val kinds = listOf("OFFLINE", "LIMIT", "NOKEY", "TIME", "OTHER")
        assertEquals(5, kinds.map { AnalysisSearchRules.searchNotRunLine(it, false) }.toSet().size)
        assertTrue(AnalysisSearchRules.composeFailedLine("OTHER", true).contains("not verified"))
        assertTrue(AnalysisSearchRules.searchPrivacyLine(false).contains("photo nahi"))
    }
}
