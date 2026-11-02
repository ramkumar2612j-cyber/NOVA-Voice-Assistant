package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProgressTest {
    @Test fun progressLineHasNoTypedTextOrLabel() {
        val l = AgentTaskRules.progressLine(3, "type", "", true, true)
        assertEquals("step 3: type -> ok, screen changed", l)
        assertFalse(l.contains("'"))
    }

    @Test fun progressLineNamesTheAppOnlyForOpenApp() {
        assertTrue(AgentTaskRules.progressLine(1, "open_app", "WhatsApp", true, true).contains("WhatsApp"))
        assertFalse(AgentTaskRules.progressLine(2, "tap", "WhatsApp", true, false).contains("WhatsApp"))
        assertTrue(AgentTaskRules.progressLine(2, "tap", "", false, false).contains("FAILED"))
    }

    @Test fun progressLineActionIsSanitised() {
        val l = AgentTaskRules.progressLine(1, "ta p;\nrm -rf", "", true, true)
        assertFalse(l.contains("\n"))
        assertFalse(l.contains(";"))
    }

    @Test fun appendProgressKeepsNewestLinesAndBound() {
        var p = ""
        for (i in 1..40) p = AgentTaskRules.appendProgress(p, "step $i: tap -> ok, screen changed")
        val lines = p.split("\n")
        assertTrue(lines.size <= AgentTaskRules.PROGRESS_LINES)
        assertTrue(lines.last().startsWith("step 40:"))
        assertTrue(p.length <= AgentTaskRules.PROGRESS_CHARS)
    }

    @Test fun appendProgressIgnoresBlankAndRedacts() {
        assertEquals("step 1: tap -> ok", AgentTaskRules.appendProgress("", "step 1: tap -> ok"))
        assertEquals("keep", AgentTaskRules.appendProgress("keep", "   "))
        assertFalse(AgentTaskRules.appendProgress("", "mail a@b.com token=abcdef123").contains("a@b.com"))
    }

    @Test fun earlierActionsEmptyWhenShort() {
        assertEquals("", AgentRules.earlierActions((1..10).map { "step $it: tap" }))
    }

    @Test fun earlierActionsListsOlderOnesOnly() {
        val e = AgentRules.earlierActions((1..14).map { "step $it: tap -> ok" })
        assertTrue(e.contains("step 1:"))
        assertTrue(e.contains("step 4:"))
        assertFalse(e.contains("step 5:"))
    }

    @Test fun earlierActionsIsBoundedAndKeepsNewestOfTheOld() {
        val hist = (1..300).map { "step $it: " + "x".repeat(200) }
        val e = AgentRules.earlierActions(hist)
        assertTrue(e.length <= AgentRules.EARLIER_CHARS + 60)
        assertTrue(e.contains("older actions not listed"))
        assertTrue(e.contains("step 290:"))
        assertFalse(e.contains("step 291:"))
    }

    @Test fun promptShowsEarlierSectionOnlyWhenNeeded() {
        val few = AgentRules.userPrompt("t", 1, "pkg", emptyList(), (1..3).map { "step $it" })
        assertFalse(few.contains("EARLIER ACTIONS"))
        val many = AgentRules.userPrompt("t", 20, "pkg", emptyList(), (1..15).map { "step $it" })
        assertTrue(many.contains("EARLIER ACTIONS"))
        assertTrue(many.contains("MY RECENT ACTIONS"))
    }
}
