package com.nova.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.113: a car horn / beep forced into "jarvis" by the tiny wake grammar must not wake JARVIS. */
class HornGuardTest {
    private fun res(conf: Double, st: Double, en: Double, w: String = "jarvis") =
        """{"result":[{"conf":$conf,"end":$en,"start":$st,"word":"$w"}],"text":"$w"}"""

    @Test fun realJarvisPasses() = assertTrue(Logic.wakeVerified(res(0.98, 1.20, 1.62), "jarvis"))
    @Test fun lowConfidenceRejected() = assertFalse(Logic.wakeVerified(res(0.41, 1.20, 1.62), "jarvis"))
    @Test fun longHornRejected() = assertFalse(Logic.wakeVerified(res(0.95, 0.30, 2.10), "jarvis"))
    @Test fun tinyClickRejected() = assertFalse(Logic.wakeVerified(res(0.95, 0.30, 0.38), "jarvis"))
    @Test fun heyJarvisUsesTheWakeWord() {
        val j = """{"result":[{"conf":0.9,"end":0.6,"start":0.2,"word":"hey"},{"conf":0.97,"end":1.1,"start":0.7,"word":"jarvis"}],"text":"hey jarvis"}"""
        assertTrue(Logic.wakeVerified(j, "jarvis"))
    }
    @Test fun unkThenWeakJarvisRejected() {
        val j = """{"result":[{"conf":1.0,"end":0.9,"start":0.1,"word":"[unk]"},{"conf":0.5,"end":1.9,"start":0.9,"word":"jarvis"}],"text":"[unk] jarvis"}"""
        assertFalse(Logic.wakeVerified(j, "jarvis"))
    }
    @Test fun noWordDataCannotJudgeSoAllowed() = assertTrue(Logic.wakeVerified("""{"text":"jarvis"}""", "jarvis"))
}
