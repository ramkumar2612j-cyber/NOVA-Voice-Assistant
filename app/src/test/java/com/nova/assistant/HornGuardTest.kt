package com.nova.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.113: a car horn / beep forced into "nova" by the tiny wake grammar must not wake NOVA. */
class HornGuardTest {
    private fun res(conf: Double, st: Double, en: Double, w: String = "nova") =
        """{"result":[{"conf":$conf,"end":$en,"start":$st,"word":"$w"}],"text":"$w"}"""

    @Test fun realNovaPasses() = assertTrue(Logic.wakeVerified(res(0.98, 1.20, 1.62), "nova"))
    @Test fun lowConfidenceRejected() = assertFalse(Logic.wakeVerified(res(0.41, 1.20, 1.62), "nova"))
    @Test fun longHornRejected() = assertFalse(Logic.wakeVerified(res(0.95, 0.30, 2.10), "nova"))
    @Test fun tinyClickRejected() = assertFalse(Logic.wakeVerified(res(0.95, 0.30, 0.38), "nova"))
    @Test fun heyNovaUsesTheWakeWord() {
        val j = """{"result":[{"conf":0.9,"end":0.6,"start":0.2,"word":"hey"},{"conf":0.97,"end":1.1,"start":0.7,"word":"nova"}],"text":"hey nova"}"""
        assertTrue(Logic.wakeVerified(j, "nova"))
    }
    @Test fun unkThenWeakNovaRejected() {
        val j = """{"result":[{"conf":1.0,"end":0.9,"start":0.1,"word":"[unk]"},{"conf":0.5,"end":1.9,"start":0.9,"word":"nova"}],"text":"[unk] nova"}"""
        assertFalse(Logic.wakeVerified(j, "nova"))
    }
    @Test fun noWordDataCannotJudgeSoAllowed() = assertTrue(Logic.wakeVerified("""{"text":"nova"}""", "nova"))
}
