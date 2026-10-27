package com.nova.assistant

import org.junit.Assert.assertTrue
import org.junit.Test

class MouthMathTest {
    @Test fun alwaysBetweenZeroAndOneAndReallyMoves() {
        var lo = 1f
        var hi = 0f
        var ms = 0L
        while (ms < 20000L) {
            val v = MouthMath.open(ms)
            assertTrue(v in 0f..1f)
            if (v < lo) lo = v
            if (v > hi) hi = v
            ms += 16L
        }
        assertTrue(lo < 0.1f)
        assertTrue(hi > 0.6f)
    }
}
