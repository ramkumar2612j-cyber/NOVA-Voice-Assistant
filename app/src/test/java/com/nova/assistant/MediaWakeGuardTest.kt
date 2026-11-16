package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaWakeGuardTest {
    @Test fun normalVoiceStillWakesWithoutMedia() {
        assertTrue(MediaWakeGuard.allowWake(900.0, 700.0, false))
    }
    @Test fun mediaBlocksFaintAndMidLoudness() {
        assertFalse(MediaWakeGuard.allowWake(900.0, 700.0, true))
        assertTrue(MediaWakeGuard.allowWake(2000.0, 700.0, true))
    }
    @Test fun multipliers() {
        assertEquals(2.5, MediaWakeGuard.loudMultiplier(true), 0.0001)
        assertEquals(1.0, MediaWakeGuard.loudMultiplier(false), 0.0001)
        assertEquals(2.0, MediaWakeGuard.cooldownMultiplier(true), 0.0001)
    }
}
