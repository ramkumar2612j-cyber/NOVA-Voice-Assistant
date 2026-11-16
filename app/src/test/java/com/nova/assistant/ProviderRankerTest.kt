package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRankerTest {
    private val ids = listOf("groq", "groqqwen", "zai", "zai2", "zai3", "openrouter", "pollinations")
    private fun ord(stats: Map<String, ProviderRanker.Stat> = emptyMap()) = ProviderRanker.order(ids, { it }, stats)

    @Test fun withoutStatsTheBestPriorIsFirst() {
        assertEquals(ids, ord())
    }

    @Test fun neverDropsOrAddsAProvider() {
        val st = mapOf("groq" to ProviderRanker.Stat(0.0, 0.0, 50))
        assertEquals(ids.sorted(), ord(st).sorted())
    }

    @Test fun aFailingProviderSinksAndAGoodOneRises() {
        val st = mapOf(
            "groq" to ProviderRanker.Stat(0.0, 0.0, 20),            // always failing
            "zai" to ProviderRanker.Stat(1.0, 800.0, 20)            // always answering, fast
        )
        val o = ord(st)
        assertTrue(o.indexOf("zai") < o.indexOf("groq"))
        assertEquals("zai", o[0])        // 70 + ~18.7 beats Qwen's prior 85
        assertEquals("groqqwen", o[1])
    }

    @Test fun fewSamplesCountLessThanManySamples() {
        val one = ProviderRanker.score("openrouter", ProviderRanker.Stat(1.0, 500.0, 1))
        val many = ProviderRanker.score("openrouter", ProviderRanker.Stat(1.0, 500.0, 10))
        assertTrue(many > one)
        assertTrue(one > ProviderRanker.score("openrouter", null))
    }

    @Test fun slowProviderLosesPoints() {
        val fast = ProviderRanker.score("groq", ProviderRanker.Stat(1.0, 600.0, 10))
        val slow = ProviderRanker.score("groq", ProviderRanker.Stat(1.0, 20000.0, 10))
        assertTrue(fast > slow)
    }

    @Test fun updateMovesTowardsTheResult() {
        var s: ProviderRanker.Stat? = null
        repeat(10) { s = ProviderRanker.update(s, true, 1000L) }
        assertTrue(s!!.rate > 0.8)
        assertEquals(10, s!!.n)
        assertEquals(1000.0, s!!.latMs, 1.0)
        repeat(10) { s = ProviderRanker.update(s, false, 0L) }
        assertTrue(s!!.rate < 0.3)
        assertEquals(1000.0, s!!.latMs, 1.0)   // a failure does not change the speed
    }

    @Test fun recoversAfterGoodAnswers() {
        var s: ProviderRanker.Stat? = ProviderRanker.Stat(0.0, 0.0, 20)
        repeat(8) { s = ProviderRanker.update(s, true, 900L) }
        assertTrue(s!!.rate > 0.5)
    }

    @Test fun encodeDecodeRoundTrip() {
        val m = mapOf("groq" to ProviderRanker.Stat(0.75, 1234.5, 12), "zai" to ProviderRanker.Stat(0.2, 0.0, 3))
        val d = ProviderRanker.decode(ProviderRanker.encode(m))
        assertEquals(2, d.size)
        assertEquals(0.75, d["groq"]!!.rate, 1e-9)
        assertEquals(1234.5, d["groq"]!!.latMs, 1e-9)
        assertEquals(3, d["zai"]!!.n)
    }

    @Test fun decodeIgnoresGarbage() {
        assertTrue(ProviderRanker.decode("").isEmpty())
        assertTrue(ProviderRanker.decode("junk;a,b,c,d;groq,2.0,1,1;,0.5,1,1;groq,0.5,-1,1").isEmpty())
        val d = ProviderRanker.decode("bad;groq,0.5,100,2")
        assertEquals(1, d.size)
    }

    @Test fun offlineAndOwnTimeoutAreNotTheProvidersFault() {
        assertEquals(true, ProviderRanker.outcomeOf(200))
        assertEquals(false, ProviderRanker.outcomeOf(429))
        assertEquals(false, ProviderRanker.outcomeOf(500))
        assertNull(ProviderRanker.outcomeOf(598))
        assertNull(ProviderRanker.outcomeOf(599))
        assertNull(ProviderRanker.outcomeOf(0))
    }
}
