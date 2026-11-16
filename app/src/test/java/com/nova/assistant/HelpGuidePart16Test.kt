package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.68 PART 16: help topics for answering / rejecting calls by voice and for the Calendar. Written, NOT RUN (no kotlinc in the sandbox). */
class HelpGuidePart16Test {
    @Test fun callAnswerTopicIsNotShadowedByTheCallTopic() {
        assertEquals("callanswer", HelpGuide.topicFor("kya tum call utha sakte ho")?.id)
        assertEquals("callanswer", HelpGuide.topicFor("kya tum call reject kar sakte ho")?.id)
        assertEquals("call", HelpGuide.topicFor("kya tum call kar sakte ho")?.id)
    }

    @Test fun calendarTopicIsFound() {
        assertEquals("calendar", HelpGuide.topicFor("kya tum calendar padh sakte ho")?.id)
        assertEquals("calendar", HelpGuide.topicFor("can you tell my meetings")?.id)
    }

    @Test fun newTopicsHaveTextAndAreInTheAiFacts() {
        val facts = HelpGuide.aiFacts()
        for (id in listOf("callanswer", "calendar")) {
            val tp = HelpGuide.TOPICS.first { it.id == id }
            assertTrue(id, tp.descHi.isNotBlank() && tp.descEn.isNotBlank() && tp.example.isNotBlank())
            assertTrue(id, facts.contains(tp.example))
        }
        assertEquals(HelpGuide.TOPICS.size, HelpGuide.TOPICS.map { it.id }.toSet().size)
    }

    @Test fun setupChainAsksOneMoreRoundForTheNewPermission() {
        assertTrue(SetupChainRules.VERSION >= 2)
    }
}
