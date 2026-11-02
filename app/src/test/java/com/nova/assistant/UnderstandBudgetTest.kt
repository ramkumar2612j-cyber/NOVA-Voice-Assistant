package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class UnderstandBudgetTest {
    @Test fun budgetNumbers() {
        assertEquals(4500L, UnderstandBudget.TOTAL_MS)
        assertEquals(3000L, UnderstandBudget.remaining(1000L, 2500L))
        assertEquals(0L, UnderstandBudget.remaining(0L, 9000L))
        assertEquals(1200, UnderstandBudget.callTimeoutMs(100L))
        assertEquals(3000, UnderstandBudget.callTimeoutMs(3000L))
        assertEquals(15000, UnderstandBudget.callTimeoutMs(99_000L))
    }

    @Test fun offlineModelNeverEatsTheCloudTime() {
        assertEquals(2500L, UnderstandBudget.offlineModelMs(0L, true))
        assertEquals(1000L, UnderstandBudget.offlineModelMs(2000L, true))
        assertEquals(0L, UnderstandBudget.offlineModelMs(4000L, true))
        assertEquals(2500L, UnderstandBudget.offlineModelMs(1000L, false))
    }

    @Test fun hedgedWinnerIsFirstValidByFinishTime() {
        val a = UnderstandBudget.Attempt("groq", "ok groq", 900L)
        val b = UnderstandBudget.Attempt("zai", "ok zai", 400L)
        val c = UnderstandBudget.Attempt("x", null, 100L)
        assertEquals("zai", UnderstandBudget.hedgedWinner(listOf(a, b, c))!!.id)
        assertNull(UnderstandBudget.hedgedWinner(listOf(c, UnderstandBudget.Attempt("y", " ", 50L))))
    }

    @Test fun honestFailureLine() {
        assertTrue(UnderstandBudget.failureLine(false).contains("dobara"))
    }

    @Test fun latencyKeepsOnlyNamesAndNumbers() {
        Latency.clear()
        Latency.record("action_ai", 1200L); Latency.record("action_ai", 6000L)
        val s = Latency.summary()
        assertTrue(s.contains("action_ai: 2x"))
        assertTrue(s.contains(">5s 1"))
        Latency.clear()
    }

    private fun prov(id: String) = FreeProvider(id, id, "https://x.invalid/v1", listOf("m"), null)

    @Test fun hedgedChainTakesFastValidAnswerAndSkipsSlowOne() {
        val cd = Cooldowns()
        val ok = "{\"choices\":[{\"message\":{\"content\":\"hello there\"}}]}"
        val r = ProviderChain.runHedged(listOf(prov("slow"), prov("fast")), { "" }, { true }, "s", "u", cd, { System.currentTimeMillis() }, budgetMs = 5000L) { p, _, _, _ ->
            if (p.id == "slow") { Thread.sleep(1500); NetAnswer(200, ok.replace("hello there", "from slow")) } else NetAnswer(200, ok)
        }
        assertEquals("hello there", r.text)
        assertEquals("fast", r.providerId)
    }

    @Test fun hedgedChainFallsThroughWhenBothFail() {
        val cd = Cooldowns()
        val ok = "{\"choices\":[{\"message\":{\"content\":\"third works\"}}]}"
        val r = ProviderChain.runHedged(listOf(prov("a"), prov("b"), prov("c")), { "" }, { true }, "s", "u", cd, { System.currentTimeMillis() }, budgetMs = 5000L) { p, _, _, _ ->
            if (p.id == "c") NetAnswer(200, ok) else NetAnswer(500, "")
        }
        assertEquals("third works", r.text)
        assertEquals("c", r.providerId)
    }
}
