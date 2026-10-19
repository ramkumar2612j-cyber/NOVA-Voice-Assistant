package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.38 Part 3: native smoothness rules. Pure numbers and strings. */
class NativeSmoothTest {
    @Test fun trimLevelsMapToPagePressure() {
        assertEquals(0, NativeSmooth.pressure(0))
        assertEquals(1, NativeSmooth.pressure(NativeSmooth.RUNNING_MODERATE))
        assertEquals(2, NativeSmooth.pressure(NativeSmooth.RUNNING_LOW))
        assertEquals(3, NativeSmooth.pressure(NativeSmooth.RUNNING_CRITICAL))
    }

    @Test fun goingToBackgroundIsNotMemoryPressure() {
        for (l in listOf(NativeSmooth.UI_HIDDEN, 40, 60, 80)) assertEquals("level $l", 0, NativeSmooth.pressure(l))
    }

    @Test fun lowMemoryCallbackIsCritical() = assertEquals(3, NativeSmooth.pressure(0, true))

    @Test fun repeatedCallbacksAreThrottledButStrongerOnesPass() {
        assertFalse(NativeSmooth.shouldSend(10_000, 0, 0, 0))
        assertTrue(NativeSmooth.shouldSend(10_000, 0, 0, 1))
        assertFalse(NativeSmooth.shouldSend(11_000, 10_000, 2, 2))
        assertFalse(NativeSmooth.shouldSend(11_000, 10_000, 2, 1))
        assertTrue(NativeSmooth.shouldSend(11_000, 10_000, 1, 2))
        assertTrue(NativeSmooth.shouldSend(13_000, 10_000, 2, 2))
    }

    @Test fun jsIsGuardedAndClamped() {
        assertEquals("window.novaNative&&novaNative('mem',3)", NativeSmooth.pressureJs(9))
        assertEquals("window.novaNative&&novaNative('mem',0)", NativeSmooth.pressureJs(-4))
        assertEquals("window.novaNative&&novaNative('bg',1)", NativeSmooth.backgroundJs(true))
        assertEquals("window.novaNative&&novaNative('bg',0)", NativeSmooth.backgroundJs(false))
    }
}
