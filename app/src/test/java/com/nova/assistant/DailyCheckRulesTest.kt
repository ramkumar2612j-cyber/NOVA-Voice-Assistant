package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCheckRulesTest {
    @Test fun reportQuestionAndApproval() {
        assertTrue(DailyCheckRules.isReportQuestion("jarvis roz ki report do"))
        assertTrue(DailyCheckRules.isReportQuestion("daily report"))
        assertFalse(DailyCheckRules.isReportQuestion("gaana chalao"))
        assertTrue(DailyCheckRules.isFixApproval("haan fix karo"))
        assertFalse(DailyCheckRules.isFixApproval("nahi abhi mat karo"))
    }

    @Test fun reportShowsBrokenFeatureAndAsksBeforeFix() {
        val p = listOf(
            DailyCheckRules.Probe("tts", "Awaaz", true, "ok", ""),
            DailyCheckRules.Probe("mic", "Mic", false, "permission nahi", "Mic permission do"))
        val r = DailyCheckRules.report(p, listOf("Chain feature"), emptyList(), false)
        assertTrue(r.startsWith("Daily check: 2 me se 1 feature theek"))
        assertTrue(r.contains("Mic") && r.contains("haan fix karo"))
        assertTrue(r.contains("Chain feature"))
    }

    @Test fun noBrokenMeansNoFixAsk() {
        val p = listOf(DailyCheckRules.Probe("tts", "Awaaz", true, "ok", ""))
        val r = DailyCheckRules.report(p, emptyList(), emptyList(), true)
        assertTrue(r.contains("No broken feature found"))
        assertFalse(r.contains("Should I fix"))
        assertTrue(DailyCheckRules.fixPlan(p, true).isEmpty())
    }

    @Test fun fixPlanIsCapped() {
        val many = List(9) { DailyCheckRules.Probe("f$it", "F$it", false, "x", "y") }
        assertEquals(DailyCheckRules.MAX_FIXES_PER_DAY, DailyCheckRules.fixPlan(many, false).size)
    }
}
