package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** v7.34 PART 5: pure tests for AutoTrace. NOT RUN in the sandbox (no kotlinc): the CI run is the first real run. */
class AutoTraceTest {
    @Before fun reset() { AutoTrace.clear(); ErrLog.clear() }

    @Test fun emptyTraceSaysSo() { assertTrue(AutoTrace.summary(0L).contains("koi auto decision nahi")) }

    @Test fun addShowsKindSubjectRouteResult() {
        AutoTrace.add("SITE", "gym", "siteBegin", "started", 1000L)
        val s = AutoTrace.summary(6000L)
        assertTrue(s.contains("SITE")); assertTrue(s.contains("gym")); assertTrue(s.contains("siteBegin")); assertTrue(s.contains("started")); assertTrue(s.contains("5s ago"))
    }

    @Test fun ringKeepsOnlyLast30() {
        for (i in 1..45) AutoTrace.add("IMAGE", "s$i", "imageBegin", "started", i.toLong())
        assertEquals(AutoTrace.MAX, AutoTrace.count())
        assertFalse(AutoTrace.summary(100L).contains("s1 "))
        assertTrue(AutoTrace.summary(100L).contains("s45"))
    }

    @Test fun subjectIsShortAndMasked() {
        val c = AutoTrace.cleanSubject("ek bahut lamba sentence jo poora nahi dikhna chahiye kabhi bhi")
        assertTrue(c.split(" ").size <= 5)
        assertTrue(AutoTrace.cleanSubject("a".repeat(80)).length <= AutoTrace.SUBJECT_LEN)
        assertFalse(AutoTrace.cleanSubject("mail me abc@xyz.com 9876543210").contains("9876543210"))
        assertFalse(AutoTrace.cleanSubject("mail me abc@xyz.com").contains("abc@xyz.com"))
    }

    @Test fun uiLineRateCappedPerTag() {
        assertEquals("ok", AutoTrace.addUi("p3", "card shown", 1000L))
        assertEquals("rate", AutoTrace.addUi("p3", "card shown", 5000L))
        assertEquals("ok", AutoTrace.addUi("p4", "core running", 5000L))
        assertEquals("ok", AutoTrace.addUi("p3", "card shown", 12000L))
    }

    @Test fun unknownTagOrEmptyMsgSkipped() {
        assertEquals("skip", AutoTrace.addUi("hack", "x", 1L))
        assertEquals("skip", AutoTrace.addUi("p3", "   ", 1L))
    }

    @Test fun jsErrorAlsoReachesErrLog() {
        assertEquals("ok", AutoTrace.addUi("js_error", "boom happened", 1L))
        assertEquals(1, ErrLog.count())
    }

    @Test fun askDetection() {
        assertTrue(AutoTrace.isAsk("auto kya samjha"))
        assertTrue(AutoTrace.isAsk("last auto decision batao"))
        assertFalse(AutoTrace.isAsk("auto reply kya samjha"))
        assertFalse(AutoTrace.isAsk("mujhe ek logo chahiye"))
        assertFalse(AutoTrace.isAsk("kya samjha"))
    }
}
