package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.65 PART 14B-3: reading the vision answer, confidence earned by sources, honest result text. No person is ever named. */
class MovieGuessRulesTest {
    private fun r(title: String, url: String = "https://en.wikipedia.org/wiki/X", snip: String = "") = AnalysisRules.Result(title, snip, url, "")
    private fun c(title: String, basis: String, ev: List<String> = listOf("title written on screen")) = MovieGuessRules.Candidate(title, "2010", basis, ev)

    @Test fun parsesAGoodAnswerAndFences() {
        val j = """{"candidates":[{"title":"Inception","year":"2010","basis":"text","evidence":["caption says Inception"]}],"text_seen":["Inception clip","@filmclips"],"scene":"a spinning top","safety":"ok"}"""
        for (raw in listOf(j, "```json\n$j\n```")) {
            val v = MovieGuessRules.parseVision(raw); assertNotNull(v)
            assertEquals("Inception", v!!.candidates[0].title); assertEquals("2010", v.candidates[0].year); assertEquals("text", v.candidates[0].basis)
            assertEquals(2, v.textSeen.size); assertEquals("ok", v.safety)
        }
    }

    @Test fun dropsAddressesPersonClaimsAndBadFields() {
        val j = """{"candidates":[{"title":"http://x.com/a","year":"2010","basis":"text","evidence":[]},{"title":"The actor is Rahul Kumar","year":"","basis":"x","evidence":[]},
            {"title":"Iron Man","year":"20x0","basis":"weird","evidence":["The man is Rahul Kumar","red suit flying"]}],"text_seen":[],"scene":"","safety":"ok"}"""
        val v = MovieGuessRules.parseVision(j)!!
        assertEquals(1, v.candidates.size); assertEquals("Iron Man", v.candidates[0].title)
        assertEquals("", v.candidates[0].year); assertEquals("scene", v.candidates[0].basis)
        assertEquals(listOf("red suit flying"), v.candidates[0].evidence)
        assertTrue(v.dropped >= 3)
    }

    @Test fun privateSafetyAndBrokenJson() {
        val v = MovieGuessRules.parseVision("""{"candidates":[{"title":"Inception"}],"safety":"private"}""")!!
        assertEquals("private", v.safety); assertTrue(v.candidates.isEmpty())
        assertNull(MovieGuessRules.parseVision("not json")); assertNull(MovieGuessRules.parseVision("")); assertNull(MovieGuessRules.parseVision(null))
    }

    @Test fun confidenceIsEarnedBySources() {
        val two = listOf(r("Inception - Wikipedia"), r("Inception (2010) - IMDb", "https://www.imdb.com/title/tt1375666/"))
        val one = listOf(r("Inception - Wikipedia"))
        assertEquals(MovieGuessRules.Level.HIGH, MovieGuessRules.level(c("Inception", "text"), two))
        assertEquals(MovieGuessRules.Level.HIGH, MovieGuessRules.level(c("Inception", "both"), two))
        assertEquals(MovieGuessRules.Level.MEDIUM, MovieGuessRules.level(c("Inception", "scene"), two))
        assertEquals(MovieGuessRules.Level.MEDIUM, MovieGuessRules.level(c("Inception", "text"), one))
        assertEquals(MovieGuessRules.Level.LOW, MovieGuessRules.level(c("Inception", "scene"), one))
        assertEquals(MovieGuessRules.Level.LOW, MovieGuessRules.level(c("Inception", "text"), emptyList()))
        assertEquals(MovieGuessRules.Level.LOW, MovieGuessRules.level(c("Inception", "text", emptyList()), one))
    }

    @Test fun titleMatchIsWholeWordsOnly() {
        assertFalse(MovieGuessRules.carriesTitle("Her", r("Hero movie")))
        assertFalse(MovieGuessRules.carriesTitle("It", r("It is a film")))   // too short to trust
        assertTrue(MovieGuessRules.carriesTitle("Dil Chahta Hai", r("Dil Chahta Hai (2001) - IMDb")))
    }

    @Test fun pickNeverBeatsLowWithoutASearch() {
        val cands = listOf(c("Inception", "text"), c("Memento", "scene"))
        val none = MovieGuessRules.pick(cands, listOf(r("Inception - Wikipedia"), r("Inception IMDb", "https://www.imdb.com/x1")), false)!!
        assertEquals(MovieGuessRules.Level.LOW, none.level); assertTrue(none.sources.isEmpty()); assertFalse(none.searched)
        val got = MovieGuessRules.pick(cands, listOf(r("Memento - Wikipedia"), r("Memento IMDb", "https://www.imdb.com/x2"), r("Memento trailer", "https://www.imdb.com/x3"), r("Memento 4", "https://www.imdb.com/x4")), true)!!
        assertEquals("Memento", got.title); assertEquals(MovieGuessRules.Level.MEDIUM, got.level); assertEquals(3, got.sources.size)
        assertNull(MovieGuessRules.pick(emptyList(), emptyList(), true))
    }

    @Test fun resultTextIsHonestAndNamesNoPerson() {
        for (en in listOf(false, true)) {
            val low = MovieGuessRules.resultText(MovieGuessRules.Outcome("Inception", "2010", MovieGuessRules.Level.LOW, emptyList(), listOf("a spinning top"), false), en)
            assertTrue(low.contains("Inception")); assertFalse(low.lowercase().contains("high"))
            assertTrue(low.contains("not verified") || low.contains("verify nahi hua"))
            val hi = MovieGuessRules.resultText(MovieGuessRules.Outcome("Inception", "2010", MovieGuessRules.Level.HIGH, listOf(r("Inception - Wikipedia")), emptyList(), true), en)
            assertTrue(hi.lowercase().contains("high")); assertTrue(hi.contains("[1]"))
            assertFalse(MovieGuessRules.resultText(null, en).isEmpty())
            assertFalse(MovieGuessRules.speech(MovieGuessRules.Outcome("Inception", "2010", MovieGuessRules.Level.HIGH, listOf(r("x")), emptyList(), true), en).contains("https"))
        }
    }

    @Test fun promptForbidsFacesAndMarksScreenTextUntrusted() {
        val s = MovieGuessRules.systemPrompt(true)
        assertTrue(s.contains("NEVER identify a real person")); assertTrue(s.contains("untrusted"))
        val u = MovieGuessRules.userPrompt("Inception clip", 3, true)
        assertTrue(u.contains("untrusted data")); assertTrue(u.contains("Inception clip"))
        assertFalse(MovieGuessRules.userPrompt("", 3, true).contains("SCREEN TEXT"))
    }

    @Test fun screenTextIsShortAndHasNoAddresses() {
        val raw = "Inception clip\nInception clip\nhttps://x.com/a\nwww.site.com\n" + (1..30).joinToString("\n") { "line number $it" }
        val out = MovieGuessRules.cleanScreenText(raw).split('\n')
        assertEquals(MovieGuessRules.MAX_TEXT_LINES, out.size); assertEquals("Inception clip", out[0])
        assertTrue(out.none { it.contains("http") || it.contains("www") })
    }

    @Test fun queriesAreShortAndLimited() {
        val q = MovieGuessRules.searchQueries(listOf(c("Inception", "text"), c("Memento", "scene")))
        assertEquals(3, q.size); assertTrue(q.all { it.length <= AnalysisRules.MAX_QUERY_CHARS })
        assertTrue(MovieGuessRules.searchQueries(emptyList()).isEmpty())
    }
}
