package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class ScheduledDaysTest {
    @Test fun parsesDaysAndTime() {
        val s = ScheduledTaskRules.parse("somvar mangalvar subah 7 baje gym yaad dilao")
        assertNotNull(s)
        assertEquals(3, s!!.days)          // Mon + Tue
        assertEquals(7, s.hour)
        assertTrue(s.repeatDaily)          // two days = weekly
        assertEquals("gym", s.label)
    }
    @Test fun roundTripKeepsDays() {
        val s = ScheduledTaskRules.Spec(9, 30, true, ScheduledTaskRules.Kind.REMINDER, "class", days = 21)
        assertEquals(21, ScheduledTaskRules.decode(ScheduledTaskRules.encode(s))!!.days)
    }
    @Test fun nextFollowsDay() {
        val z = ZoneId.of("Asia/Kolkata")
        val tue = LocalDateTime.of(2026, 10, 13, 10, 0).atZone(z).toInstant().toEpochMilli()   // a Tuesday
        val s = ScheduledTaskRules.Spec(8, 0, true, ScheduledTaskRules.Kind.REMINDER, "x", days = 1) // Monday only
        val next = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ScheduledTaskRules.nextAtMillis(s, tue, z)), z)
        assertEquals(java.time.DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(19, next.dayOfMonth)
    }
}
