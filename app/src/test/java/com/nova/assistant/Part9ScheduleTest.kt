package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/** v7.22 Part 9 (add-only): scheduled actions, real behaviour of ScheduledTaskRules + ScheduleRestoreRules. */
class Part9ScheduleTest {
    private fun p(s: String) = ScheduledTaskRules.parse(s, LocalTime.of(7, 0))

    @Test fun messageTextDoesNotContainRecipientOrChannelWords() {
        for (s in listOf("roz subah 8 baje mummy ko whatsapp pe hello bhejo", "roz subah 8 baje mummy ko whatsapp par hello bhejo")) {
            val sp = p(s); assertNotNull(s, sp)
            assertEquals(s, ScheduledTaskRules.Kind.SEND_MESSAGE, sp!!.kind)
            assertEquals(s, "mummy", sp.recipient); assertEquals(s, "hello", sp.message)
            assertEquals(s, 8, sp.hour); assertEquals(s, true, sp.repeatDaily); assertEquals(s, "whatsapp", sp.channel)
        }
    }

    @Test fun weatherReminderAndDays() {
        val w = p("roz shaam 6 baje weather batao")!!
        assertEquals(ScheduledTaskRules.Kind.WEATHER, w.kind); assertEquals(18, w.hour)
        val r = p("somvar budhvar subah 7 baje yaad dilao dawai lena")!!
        assertEquals(ScheduledTaskRules.Kind.REMINDER, r.kind); assertEquals(5, r.days); assertEquals(7, r.hour)
    }

    @Test fun plainTimersAndAlarmsAreNotSchedules() {
        assertNull(p("10 minute ka timer")); assertNull(p("alarm subah 6 baje"))
    }

    @Test fun encodeDecodeRoundTrip() {
        val sp = p("roz subah 8 baje mummy ko whatsapp pe hello bhejo")!!
        val back = ScheduledTaskRules.decode(ScheduledTaskRules.encode(sp))!!
        assertEquals(sp, back)
    }

    @Test fun restorePlansAfterRebootOrClockChange() {
        val now = 10_000_000L
        assertEquals(ScheduleRestoreRules.Plan.KEEP, ScheduleRestoreRules.planFor(now + 1, false, now))
        assertEquals(ScheduleRestoreRules.Plan.MOVE_NEXT, ScheduleRestoreRules.planFor(now - 1000, true, now))
        assertEquals(ScheduleRestoreRules.Plan.FIRE_NOW, ScheduleRestoreRules.planFor(now - 60_000, false, now))
        assertEquals(ScheduleRestoreRules.Plan.DROP_MISSED, ScheduleRestoreRules.planFor(now - 700_000, false, now))
    }

    @Test fun nextAtIsAlwaysInTheFutureAndOnAllowedDay() {
        val sp = p("somvar budhvar subah 7 baje yaad dilao dawai lena")!!
        val now = System.currentTimeMillis()
        val at = ScheduledTaskRules.nextAtMillis(sp, now)
        assert(at > now)
        val dow = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).dayOfWeek.value
        assert(dow == 1 || dow == 3)
    }
}
