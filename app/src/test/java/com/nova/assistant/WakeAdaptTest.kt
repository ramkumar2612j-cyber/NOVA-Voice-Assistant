package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeAdaptTest {
    @Test fun noMissesIsNormal() { assertEquals(1.0, WakeAdapt.scale(0, 0L, 1000L), 0.0) }
    @Test fun eachMissRaisesAndIsCapped() {
        assertEquals(1.25, WakeAdapt.scale(1, 1000L, 2000L), 1e-9)
        assertEquals(1.5, WakeAdapt.scale(2, 1000L, 2000L), 1e-9)
        assertEquals(2.0, WakeAdapt.scale(9, 1000L, 2000L), 1e-9)
    }
    @Test fun relaxesAfterQuietPeriod() { assertEquals(1.0, WakeAdapt.scale(5, 1000L, 1000L + WakeAdapt.RELAX_MS + 1), 0.0) }
    @Test fun tiers() {
        assertEquals(DeviceTier.Tier.LOW, DeviceTier.of(2000, 8, 33))
        assertEquals(DeviceTier.Tier.LOW, DeviceTier.of(8000, 4, 33))
        assertEquals(DeviceTier.Tier.MID, DeviceTier.of(4000, 8, 30))
        assertEquals(DeviceTier.Tier.HIGH, DeviceTier.of(8000, 8, 34))
        assertTrue(!DeviceTier.allowsOfflineBrain(DeviceTier.Tier.LOW))
        assertTrue(DeviceTier.allowsFancyEffects(DeviceTier.Tier.HIGH))
    }
}
