package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure maths behind the LIMITS screen (no Android classes). */
class ApiUsageTest {
    @Test fun remainingNeverNegativeAndUnknownWithoutLimit() {
        assertEquals(-1, UsageMath.remaining(0, 5))
        assertEquals(995, UsageMath.remaining(1000, 5))
        assertEquals(0, UsageMath.remaining(50, 80))
        assertEquals(50, UsageMath.remaining(50, -3))
    }

    @Test fun percentIsCappedAndUnknownWithoutLimit() {
        assertEquals(-1, UsageMath.percent(0, 10))
        assertEquals(0, UsageMath.percent(1000, 0))
        assertEquals(50, UsageMath.percent(50, 25))
        assertEquals(100, UsageMath.percent(50, 500))
    }

    @Test fun headerParsingRejectsJunk() {
        assertEquals(1000, UsageMath.headerInt("1000"))
        assertEquals(997, UsageMath.headerInt(" 997 "))
        assertEquals(-1, UsageMath.headerInt(null))
        assertEquals(-1, UsageMath.headerInt("abc"))
        assertEquals(-1, UsageMath.headerInt("-5"))
        assertEquals(-1, UsageMath.headerInt("99999999999"))
    }

    @Test fun onlyRealRequestsAreCounted() {
        assertTrue(UsageMath.countsAsCall(200))
        assertTrue(UsageMath.countsAsCall(429))
        assertTrue(UsageMath.countsAsCall(500))
        assertFalse(UsageMath.countsAsCall(0))
        assertFalse(UsageMath.countsAsCall(598))
        assertFalse(UsageMath.countsAsCall(599))
    }

    @Test fun limitOverrideIsClamped() {
        assertEquals(0, UsageMath.cleanLimit(0))
        assertEquals(0, UsageMath.cleanLimit(-4))
        assertEquals(250, UsageMath.cleanLimit(250))
        assertEquals(10_000_000, UsageMath.cleanLimit(Int.MAX_VALUE))
    }

    @Test fun dayKeyFollowsTheProviderZone() {
        // 2026-10-11 03:00 UTC is still 2026-10-10 in Los Angeles (UTC-7 in October).
        val t = 1_791_687_600_000L
        assertEquals("2026-10-11", UsageMath.dayKey(t, "UTC"))
        assertEquals("2026-10-10", UsageMath.dayKey(t, "America/Los_Angeles"))
        assertEquals("2026-10-11", UsageMath.dayKey(t, "not/a-zone"))
    }

    @Test fun everyRowHasUniqueIdAndSaneDefault() {
        val ids = ApiUsage.ROWS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (r in ApiUsage.ROWS) {
            assertTrue(r.defLimit >= 0)
            assertTrue(r.limitKind == "official" || r.limitKind == "estimate" || r.limitKind == "none")
            assertTrue((r.defLimit == 0) == (r.limitKind == "none"))
        }
    }
}
