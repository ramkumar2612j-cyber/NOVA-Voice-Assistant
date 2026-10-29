package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class JarvisLocalVoiceTest {
    private fun c(n: String, lang: String = "en", ctry: String = "GB", q: Int = 400, net: Boolean = false) = JarvisLocalVoice.Cand(n, lang, ctry, q, net)

    @Test fun prefersKnownMaleCode() {
        val p = JarvisLocalVoice.pick(listOf(c("en-gb-x-gba-local", q = 500), c("en-gb-x-gbd-local"), c("en-gb-x-rjs-local"), c("en-us-x-iom-local")))
        assertEquals("en-gb-x-rjs-local", p!!.name)
        assertTrue(JarvisLocalVoice.isMale(p))
    }
    @Test fun skipsNetworkAndOtherLocales() {
        assertNull(JarvisLocalVoice.pick(listOf(c("en-gb-x-gbb-network", net = true), c("en-us-x-tpd-local", ctry = "US"), c("hi-in-x-hia-local", lang = "hi", ctry = "IN"))))
    }
    @Test fun fallsBackToAnyOfflineBritishVoice() {
        val p = JarvisLocalVoice.pick(listOf(c("en-gb-x-fis-local", q = 300), c("en-gb-x-gba-local", q = 400)))
        assertEquals("en-gb-x-gba-local", p!!.name); assertFalse(JarvisLocalVoice.isMale(p))
    }
    @Test fun emptyList() { assertNull(JarvisLocalVoice.pick(emptyList())) }
}
