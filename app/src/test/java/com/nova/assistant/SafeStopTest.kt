package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.39 Part 4: SAFE STOP rules. Pure. */
class SafeStopTest {
    @Test fun hudStopOnlyStopsByDefault() {
        assertEquals(SafeStop.HudStop.SOFT, SafeStop.hudStop(false))
        assertEquals(SafeStop.HudStop.POWER_OFF, SafeStop.hudStop(true))
    }

    @Test fun halfHeardCommandIsDropped() {
        assertTrue(SafeStop.dropsHeard(capturing = true, collectedText = false))
        assertTrue(SafeStop.dropsHeard(capturing = false, collectedText = true))
        assertFalse(SafeStop.dropsHeard(capturing = false, collectedText = false))
        assertTrue(SafeStop.resetsRecognizers(true))
        assertFalse(SafeStop.resetsRecognizers(false))
    }

    @Test fun spokenStopStillNeedsAWholeUtterance() {
        assertTrue(StopRules.isStop("nova ruko"))
        assertFalse(StopRules.isStop("ruko mat gaana chalao"))
        assertFalse(StopRules.isStop("stop"+" the music please now"))
    }
}
