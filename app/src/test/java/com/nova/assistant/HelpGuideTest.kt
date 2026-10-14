package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class HelpGuideTest {
    @Test fun generalQuestionsAreHelpAsks() {
        for (s in listOf("tum kya kar sakte ho", "What can you do?", "help", "nova help", "kya kya kar sakte ho", "app kaise use karu", "mujhe madad chahiye"))
            assertTrue(s, HelpGuide.isAsk(s))
    }

    @Test fun ordinaryCommandsAndQuestionsAreNotHijacked() {
        for (s in listOf("nova mummy ko call karo", "aaj ka mausam batao", "website banao", "kya tum mausam bata sakte ho",
            "mujhe help karo ek email likhne mein", "isko professional banao", "nova torch jala do"))
            assertFalse(s, HelpGuide.isAsk(s))
    }

    @Test fun abilityQuestionFindsTheTopic() {
        assertEquals("website", HelpGuide.topicFor("kya tum website bana sakte ho")?.id)
        assertEquals("call", HelpGuide.topicFor("can you call my mom")?.id)
        assertEquals("whatsapp", HelpGuide.topicFor("kya tum whatsapp kar sakte ho")?.id)
        assertEquals("image", HelpGuide.topicFor("can you make a picture")?.id)
        assertEquals("memory", HelpGuide.topicFor("kya tum yaad rakh sakte ho")?.id)
        assertEquals("alarm", HelpGuide.topicFor("nova kya tum alarm laga sakte ho")?.id)
        assertEquals("camera", HelpGuide.topicFor("kya tum camera chala sakte ho")?.id)
    }

    @Test fun phoneNumberIsNeverAHelpAsk() {
        assertNull(HelpGuide.topicFor("kya tum 9876543210 ko call kar sakte ho"))
    }

    @Test fun answersAreShortPlainAndPointToMadad() {
        val g = HelpGuide.answer("tum kya kar sakte ho", false)
        assertTrue(g.contains("MADAD")); assertTrue(g.length < 700)
        val t = HelpGuide.answer("kya tum website bana sakte ho", false)
        assertTrue(t.startsWith("Haan!")); assertTrue(t.contains("nova ek restaurant ki website banao"))
        assertTrue(HelpGuide.answer("what can you do", true).contains("MADAD"))
    }

    @Test fun aiFactsListEveryTopicAndNoTemplateDollar() {
        val f = HelpGuide.aiFacts()
        for (tp in HelpGuide.TOPICS) assertTrue(tp.id, f.contains(tp.example))
        assertFalse(f.contains("$"))
    }
}
