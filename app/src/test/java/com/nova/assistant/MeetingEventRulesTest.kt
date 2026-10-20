package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class MeetingEventRulesTest {
    private val now = LocalDateTime.of(2026, 10, 20, 14, 58)

    @Test fun ownerPhrase_makesRssMeetingAt1530() {
        val s = MeetingEventRules.parse("nova 3 30 ka abhi ka schedule banao ki hume rss meeting me jana hai", now)!!
        assertEquals("RSS Meeting", s.title); assertEquals(15, s.hour); assertEquals(30, s.minute); assertEquals(0, s.dayOffset)
    }
    @Test fun colonTime() { val s = MeetingEventRules.parse("3:30 ka schedule banao rss meeting", now)!!; assertEquals(15, s.hour); assertEquals(30, s.minute) }
    @Test fun time24h() { val s = MeetingEventRules.parse("15:30 pe rss meeting schedule karo", now)!!; assertEquals(15, s.hour) }
    @Test fun tomorrowMorning() { val s = MeetingEventRules.parse("kal subah 10 baje doctor appointment add karo", now)!!; assertEquals(10, s.hour); assertEquals(1, s.dayOffset); assertEquals("Doctor Appointment", s.title) }
    @Test fun saade() { val s = MeetingEventRules.parse("saade teen baje client meeting set karo", now)!!; assertEquals(15, s.hour); assertEquals(30, s.minute) }
    @Test fun notMatched() {
        for (p in listOf("aaj ka schedule", "nova torch jala do", "meeting record karo 3 30", "calendar kholo", "meri schedule list", "2 3 log meeting ke liye add karo"))
            assertNull(p, MeetingEventRules.parse(p, now))
    }
    @Test fun roundTrip() { val s = MeetingEventRules.parse("3:30 ka schedule banao rss meeting", now)!!; assertEquals(s, MeetingEventRules.decode(MeetingEventRules.encode(s))) }
}

class FeatureFixRulesTest {
    @Test fun matches() {
        for (p in listOf("band features set karo", "jo permission baaki hai wo set karo", "sab permission chalu karo", "setup poora karo", "nova band features chalu kar do"))
            assertTrue(p, FeatureFixRules.matches(p))
    }
    @Test fun doesNotMatch() {
        for (p in listOf("torch on karo", "permission kya hai", "camera kholo", "volume badhao", "3 baje meeting schedule karo"))
            assertFalse(p, FeatureFixRules.matches(p))
    }
    @Test fun replyNeverClaimsDone() {
        assertTrue(FeatureFixRules.reply(listOf("access", "listener"), "access").contains("switch ON karo"))
        assertTrue(FeatureFixRules.reply(emptyList(), null).contains("baaki nahi"))
    }
}
