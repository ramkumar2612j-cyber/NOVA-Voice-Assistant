package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class SourceMapTest {
    @Test fun everyNeedHasASourceAndASpokenLine() {
        assertEquals(12, SourceMap.entries.size)
        for (e in SourceMap.entries) {
            assertTrue(e.id, e.where.isNotBlank() && e.say.contains("->") && e.example.isNotBlank())
        }
        assertEquals(SourceMap.entries.size, SourceMap.entries.map { it.id }.toSet().size)
    }

    @Test fun cloudIsTheLastRowAndOnlyOneRowUsesIt() {
        assertEquals(SourceMap.Cost.CLOUD_AI, SourceMap.entries.last().cost)
        assertEquals(1, SourceMap.entries.count { it.cost == SourceMap.Cost.CLOUD_AI })
    }

    @Test fun liveNeedsNeedTheInternetAndLocalOnesDoNot() {
        for (id in listOf("weather", "news", "currency", "crypto")) assertTrue(id, SourceMap.entries.first { it.id == id }.needsNet)
        for (id in listOf("phone", "calc", "memory", "knowledge", "chat")) assertFalse(id, SourceMap.entries.first { it.id == id }.needsNet)
    }

    @Test fun localAiMayAnswerShortSimpleQuestions() {
        assertTrue(SourceMap.localCanHandle("kaise ho"))
        assertTrue(SourceMap.localCanHandle("tum kaun ho"))
    }

    @Test fun localAiNeverTakesLiveHeavyOrLongQuestions() {
        assertFalse(SourceMap.localCanHandle("jaipur ka mausam"))
        assertFalse(SourceMap.localCanHandle("ek email likho boss ko"))
        assertFalse(SourceMap.localCanHandle("bitcoin price today"))
        assertFalse(SourceMap.localCanHandle(""))
        assertFalse(SourceMap.localCanHandle("one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen"))
    }

    @Test fun sourceQuestionsAreRecognised() {
        for (s in listOf("tum jawab kahan se laate ho", "nova mausam kahan se milta hai", "which browser do you use for news"))
            assertTrue(s, SourceMap.isAsk(s))
    }

    @Test fun ordinaryRequestsAreNotHijacked() {
        for (s in listOf("aaj ka mausam batao", "jaipur ka mausam", "open source code likho", "nova torch jala do", "bitcoin ka price"))
            assertFalse(s, SourceMap.isAsk(s))
    }

    @Test fun explainIsShortAndNamesTheBrowsers() {
        val hi = SourceMap.explain(false)
        assertTrue(hi.contains("Open-Meteo") && hi.contains("Google News") && hi.contains("Frankfurter") && hi.contains("CoinGecko") && hi.contains("Wikipedia"))
        assertTrue(hi.length < 700)
        assertTrue(SourceMap.explain(true).startsWith("I pick"))
    }

    @Test fun aiFactsAreSafeForARawStringPrompt() {
        val f = SourceMap.aiFacts()
        assertFalse(f.contains("$"))
        assertTrue(f.contains("never invent numbers"))
    }
}
