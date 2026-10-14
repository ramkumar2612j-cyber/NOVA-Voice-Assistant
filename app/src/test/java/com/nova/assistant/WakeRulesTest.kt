package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeRulesTest {
    @Test fun greetingWindow() {
        assertFalse(WakeRules.inGreetingWindow(5 * 60 + 59))
        assertTrue(WakeRules.inGreetingWindow(6 * 60))
        assertTrue(WakeRules.inGreetingWindow(11 * 60 + 59))
        assertFalse(WakeRules.inGreetingWindow(12 * 60))
    }
    @Test fun alarmOnlyInMorningAndWhileAsleep() {
        assertTrue(WakeRules.alarmAllowed(6 * 60 + 5, 6 * 60 + 30, false))
        assertFalse(WakeRules.alarmAllowed(6 * 60 + 5, 6 * 60 + 30, true))      // already awake: only normal reminders
        assertFalse(WakeRules.alarmAllowed(13 * 60, 13 * 60 + 25, false))       // afternoon: never
        assertFalse(WakeRules.alarmAllowed(12 * 60, 11 * 60 + 59, false))       // noon reached: never
        assertFalse(WakeRules.alarmAllowed(2 * 60, 6 * 60 + 30, false))         // night: never
        assertFalse(WakeRules.alarmAllowed(5 * 60 + 45, 6 * 60 + 10, false))    // before 06:00: never
        assertTrue(WakeRules.alarmAllowed(6 * 60, 6 * 60 + 10, false))          // 06:00 sharp: allowed
        assertTrue(WakeRules.alarmAllowed(11 * 60 + 59, 11 * 60 + 59, false))   // last minute before noon
        assertFalse(WakeRules.alarmAllowed(7 * 60, 15 * 60, false))             // afternoon job: no wake alarm
    }
    @Test fun greetingChangesEveryDay() {
        assertNotEquals(WakeRules.greeting(20000), WakeRules.greeting(20001))
        assertEquals(WakeRules.greeting(20000), WakeRules.greeting(20000))
    }
    @Test fun todayTextSortedWithPriority() {
        val t = WakeRules.todayText(listOf(
            WakeRules.Item(9 * 60, "meeting", 0, ""),
            WakeRules.Item(7 * 60, "dawai", 2, "doctor ne kaha")))
        assertTrue(t.indexOf("dawai") < t.indexOf("meeting"))
        assertTrue(t.contains("bahut zaruri"))
        assertTrue(t.contains("doctor ne kaha"))
    }
    @Test fun priorityParsedFromSpeech() {
        val s = ScheduledTaskRules.parse("roz subah 7 baje dawai yaad dilao bahut zaruri kyunki doctor ne kaha")
        assertNotNull(s)
        assertEquals(2, s!!.prio)
        assertEquals("doctor ne kaha", s.note)
        assertEquals("dawai", s.label)
        assertEquals(1, ScheduledTaskRules.parse("roz subah 8 baje gym yaad dilao zaruri")!!.prio)
        assertEquals(0, ScheduledTaskRules.parse("roz subah 8 baje gym yaad dilao")!!.prio)
    }
    @Test fun roundTripKeepsPriority() {
        val s = ScheduledTaskRules.Spec(7, 0, true, ScheduledTaskRules.Kind.REMINDER, "x", days = 3, prio = 2, note = "kyun")
        val d = ScheduledTaskRules.decode(ScheduledTaskRules.encode(s))!!
        assertEquals(2, d.prio); assertEquals("kyun", d.note); assertEquals(3, d.days)
    }
    @Test fun wakeAndPriorityCommands() {
        assertEquals("wake_off", ScheduledTaskRules.command("alarm band karo")!!.kind)
        assertEquals("wake_snooze", ScheduledTaskRules.command("dismiss")!!.kind)
        assertEquals("gym|2|", ScheduledTaskRules.command("schedule bahut zaruri gym")!!.arg)
        assertEquals("gym|-1|dawai ke baad", ScheduledTaskRules.command("schedule note gym dawai ke baad")!!.arg)
        assertNull(ScheduledTaskRules.command("kya haal hai"))
    }
}
