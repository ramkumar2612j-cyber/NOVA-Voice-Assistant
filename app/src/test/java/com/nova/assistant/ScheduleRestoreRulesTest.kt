package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.12 AUTOPSY R4: re-arming saved jobs (pure rules). The real AlarmManager behaviour is NOT tested here. */
class ScheduleRestoreRulesTest {
    private val now = 1_000_000_000L

    @Test fun futureJobIsKept() { assertEquals(ScheduleRestoreRules.Plan.KEEP, ScheduleRestoreRules.planFor(now + 5_000, false, now)) }

    @Test fun repeatingPastDueMovesToNext() { assertEquals(ScheduleRestoreRules.Plan.MOVE_NEXT, ScheduleRestoreRules.planFor(now - 3_600_000, true, now)) }

    @Test fun oneShotJustPastDueFiresNow() { assertEquals(ScheduleRestoreRules.Plan.FIRE_NOW, ScheduleRestoreRules.planFor(now - 60_000, false, now)) }

    @Test fun oneShotLongPastDueIsMissedNotMovedToTomorrow() {
        assertEquals(ScheduleRestoreRules.Plan.DROP_MISSED, ScheduleRestoreRules.planFor(now - 3_600_000, false, now))
        assertEquals(ScheduleRestoreRules.Plan.DROP_MISSED, ScheduleRestoreRules.planFor(now - ScheduleRestoreRules.GRACE_MS - 1, false, now))
    }

    @Test fun bootPlanOnlyBootResetsActive() {
        val boot = ScheduleRestoreRules.bootPlan("android.intent.action.BOOT_COMPLETED")!!
        assertTrue(boot.resetActive && boot.rearm && boot.askToStart)
        for (a in listOf("android.intent.action.MY_PACKAGE_REPLACED", "android.intent.action.TIME_SET", "android.intent.action.TIMEZONE_CHANGED", "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) {
            val p = ScheduleRestoreRules.bootPlan(a)!!
            assertTrue(a, p.rearm && !p.resetActive && !p.askToStart)
        }
        assertNotNull(ScheduleRestoreRules.bootPlan("android.intent.action.QUICKBOOT_POWERON"))
        assertNull(ScheduleRestoreRules.bootPlan("android.intent.action.SOMETHING_ELSE"))
        assertNull(ScheduleRestoreRules.bootPlan(null))
    }
}
