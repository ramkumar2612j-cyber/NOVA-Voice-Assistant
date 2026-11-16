package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReleaseRulesTest {
    private val now = 1_000_000_000L
    private fun ok(phase: String = SelfBuildRules.GREEN, last: Long = 0L, today: Int = 0, locked: Boolean = false, call: Boolean = false, busy: Boolean = false, en: Boolean = true) =
        AutoReleaseRules.shouldAutoInstall(phase, last, today, now, locked, call, busy, en).go

    @Test fun greenBuildInstalls() { assertTrue(ok()) }
    @Test fun notGreenNeverInstalls() { assertFalse(ok(phase = SelfBuildRules.RED)); assertFalse(ok(phase = SelfBuildRules.RUNNING)) }
    @Test fun lockedCallBusyOrDisabledBlock() {
        assertFalse(ok(locked = true)); assertFalse(ok(call = true)); assertFalse(ok(busy = true)); assertFalse(ok(en = false))
    }
    @Test fun gapAndDailyCapHold() {
        assertFalse(ok(last = now - 3600_000L))
        assertTrue(ok(last = now - AutoReleaseRules.MIN_GAP_MS - 1))
        assertFalse(ok(today = AutoReleaseRules.MAX_PER_DAY))
    }
    @Test fun countTodayCountsOnlyLast24h() {
        val stamps = listOf(now - 1000, now - 2 * 24L * 3600_000L, now - 5000)
        assertEquals(2, AutoReleaseRules.countToday(stamps, now))
    }
}
