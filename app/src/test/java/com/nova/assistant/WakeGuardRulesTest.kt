package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.115: TV / room-talk wake guard. Pure text. Written, NOT RUN here. */
class WakeGuardRulesTest {
    private fun one(conf: Double, w: String = "nova") =
        """{"result":[{"conf":$conf,"end":0.9,"start":0.5,"word":"$w"}],"text":"$w"}"""

    @Test fun realNovaPasses() = assertTrue(WakeGuardRules.allow(one(0.97), "nova", 0))
    @Test fun lowConfidenceRejected() = assertFalse(WakeGuardRules.allow(one(0.60), "nova", 0))
    @Test fun noWordDataIsAllowed() = assertTrue(WakeGuardRules.allow("""{"text":"nova"}""", "nova", 3))

    @Test fun longTalkIsRejected() {
        val j = """{"text":"nova [unk] [unk] [unk] [unk] [unk] [unk] [unk] [unk]"}"""
        assertTrue(WakeGuardRules.tooLong(j))
        assertFalse(WakeGuardRules.allow(j, "nova", 0))
    }

    @Test fun shortCommandInOneBreathIsAllowed() {
        val j = """{"text":"nova [unk] [unk]"}"""
        assertFalse(WakeGuardRules.tooLong(j))
        assertTrue(WakeGuardRules.allow(j, "nova", 0))
    }

    @Test fun falseWakesRaiseTheBar() {
        assertEquals(0.75, WakeGuardRules.minConf(0), 1e-9)
        assertEquals(0.85, WakeGuardRules.minConf(2), 1e-9)
        assertEquals(WakeGuardRules.MAX_CONF, WakeGuardRules.minConf(50), 1e-9)
        assertTrue(WakeGuardRules.allow(one(0.80), "nova", 0))
        assertFalse(WakeGuardRules.allow(one(0.80), "nova", 3))
        assertTrue(WakeGuardRules.allow(one(0.95), "nova", 3))
    }

    @Test fun unkBeforeNovaNeedsASurerNova() {
        val j = """{"result":[{"conf":1.0,"end":0.4,"start":0.1,"word":"[unk]"},{"conf":0.80,"end":1.0,"start":0.5,"word":"nova"}],"text":"[unk] nova"}"""
        assertTrue(WakeGuardRules.weakStart(j))
        assertFalse(WakeGuardRules.allow(j, "nova", 0))
        assertTrue(WakeGuardRules.allow(j.replace("0.80", "0.93"), "nova", 0))
    }

    @Test fun missesFadeAfterQuietPeriod() {
        assertEquals(2, WakeGuardRules.activeMisses(2, 1000L, 2000L))
        assertEquals(0, WakeGuardRules.activeMisses(2, 1000L, 1000L + WakeAdapt.RELAX_MS + 1))
        assertEquals(0, WakeGuardRules.activeMisses(0, 1000L, 2000L))
    }
}
