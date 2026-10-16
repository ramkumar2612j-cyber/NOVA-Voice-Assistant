package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v6.99 Part 2: spoken STOP rules. Pure text. Written, NOT RUN. */
class StopRulesTest {
    @Test fun wholeUtteranceStopsAreStops() {
        for (s in listOf("ruko", "Ruk jao", "nova ruko", "stop", "bas karo", "roko", "rehne do", "cancel karo", "  stop   it  "))
            assertTrue(s, StopRules.isStop(s))
    }

    @Test fun stopWordInsideACommandIsNotAStop() {
        for (s in listOf("", "music stop karo", "torch jala do", "ruko mat gaana chalao", "stop the music", "nova ek billi ki photo banao"))
            assertFalse(s, StopRules.isStop(s))
    }

    @Test fun logicClassifiesNewStopPhrases() {
        for (s in listOf("ruk jao", "bas karo", "roko", "stop it"))
            assertEquals(s, "stop", Logic.classify(s)?.kind)
    }

    @Test fun stopCounterDetectsCancellation() {
        assertFalse(StopRules.wasStopped(3, 3))
        assertTrue(StopRules.wasStopped(3, 4))
    }
}
