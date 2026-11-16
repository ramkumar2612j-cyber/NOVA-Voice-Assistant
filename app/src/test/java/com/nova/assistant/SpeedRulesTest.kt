package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedRulesTest {
    @Test fun neverStopsBeforeMinWhenReady() = assertFalse(SpeedRules.shouldStop(200, 500, 1800, true))
    @Test fun stopsAtMinWhenReady() = assertTrue(SpeedRules.shouldStop(500, 500, 1800, true))
    @Test fun keepsWaitingWhenNotReady() = assertFalse(SpeedRules.shouldStop(1799, 500, 1800, false))
    @Test fun capStopsEvenIfNotReady() = assertTrue(SpeedRules.shouldStop(1800, 500, 1800, false))
    @Test fun stepsRoundUpAndAtLeastOne() {
        assertEquals(22, SpeedRules.steps(6400L, 300L))
        assertEquals(1, SpeedRules.steps(0L, 300L))
        assertEquals(18, SpeedRules.steps(1800L))
    }
    @Test fun appReadyNeedsFrontAndStable() {
        assertFalse(SpeedRules.appReady(false, 5))
        assertFalse(SpeedRules.appReady(true, 1))
        assertTrue(SpeedRules.appReady(true, 2))
    }
    @Test fun retryPauseIsShorterThanOld700() = assertTrue(SpeedRules.HEAR_RETRY_MS in 1..699)
}
