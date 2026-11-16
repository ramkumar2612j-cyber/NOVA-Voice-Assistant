package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.69 PART 18: safe mode rules (pure). */
class CrashGuardTest {
    private val M = 60_000L

    @Test fun oneCrashDoesNotStartSafeMode() {
        val l = CrashGuard.add(emptyList(), 100 * M)
        assertEquals(0L, CrashGuard.safeUntil(l, 100 * M, 0L))
    }

    @Test fun twoCrashesInTenMinutesStartThirtyMinutes() {
        val l = CrashGuard.add(CrashGuard.add(emptyList(), 100 * M), 105 * M)
        val u = CrashGuard.safeUntil(l, 105 * M, 0L)
        assertEquals(135 * M, u)
        assertTrue(CrashGuard.isSafe(u, 120 * M))
        assertFalse(CrashGuard.isSafe(u, 135 * M))
    }

    @Test fun twoCrashesFarApartDoNot() {
        val l = CrashGuard.add(CrashGuard.add(emptyList(), 100 * M), 120 * M)
        assertEquals(0L, CrashGuard.safeUntil(l, 120 * M, 0L))
    }

    @Test fun clockMovedBackIsNotSafeForever() {
        assertFalse(CrashGuard.isSafe(10_000 * M, 100 * M))
    }

    @Test fun listIsCappedAndRoundTrips() {
        var l = emptyList<Long>()
        for (i in 1..10) l = CrashGuard.add(l, i * M)
        assertEquals(CrashGuard.MAX_KEEP, l.size)
        assertEquals(l, CrashGuard.decode(CrashGuard.encode(l)))
        assertEquals(emptyList<Long>(), CrashGuard.decode("x,,"))
    }
}
