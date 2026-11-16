package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.69 PART 17: help topic for the 3 Settings switches, and STOP stays soft unless the owner turns the switch ON. Written, NOT RUN until the compile check finishes. */
class HelpGuidePart17Test {
    @Test fun switchesTopicIsFound() {
        assertEquals("switches", HelpGuide.topicFor("kya tum stop switch badal sakte ho")?.id)
        assertEquals("switches", HelpGuide.topicFor("kya graphics quality badal sakte ho")?.id)
    }

    @Test fun switchesTopicHasTextAndIsInTheAiFacts() {
        val tp = HelpGuide.TOPICS.first { it.id == "switches" }
        assertTrue(tp.descHi.isNotBlank() && tp.descEn.isNotBlank() && tp.example.isNotBlank())
        assertTrue(HelpGuide.aiFacts().contains(tp.example))
        assertEquals(HelpGuide.TOPICS.size, HelpGuide.TOPICS.map { it.id }.toSet().size)
    }

    @Test fun stopIsSoftByDefaultAndPowersOffOnlyWhenSwitchedOn() {
        assertEquals(SafeStop.HudStop.SOFT, SafeStop.hudStop(false))
        assertEquals(SafeStop.HudStop.POWER_OFF, SafeStop.hudStop(true))
    }
}
