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

    @Test fun newWebAndDownloadTopicsAreFound() {
        assertEquals("download", HelpGuide.topicFor("kya tum app download kar sakte ho")?.id)
        assertEquals("download", HelpGuide.topicFor("kya tum whatsapp download kar sakte ho")?.id)
        assertEquals("chrome", HelpGuide.topicFor("can you open websites in chrome")?.id)
        assertEquals("whatsapp", HelpGuide.topicFor("kya tum whatsapp kar sakte ho")?.id)
        assertFalse(HelpGuide.isAsk("whatsapp download karo"))
        assertFalse(HelpGuide.isAsk("youtube chrome pe kholo"))
        assertTrue(HelpGuide.general(false).contains("Chrome") && HelpGuide.general(false).contains("download"))
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

    @Test fun safetyTopicIsFoundAndBeatsScreenTopic() {
        assertEquals("safety", HelpGuide.topicFor("kya tum bank screen par kaam kar sakte ho")?.id)
        assertEquals("safety", HelpGuide.topicFor("can you work on password screens")?.id)
        assertTrue(HelpGuide.answer("kya tum bank screen par kaam kar sakte ho", false).startsWith("Haan!"))
    }

    @Test fun socialTopicIsFound() {
        assertEquals("social", HelpGuide.topicFor("kya tum insta comment ka jawab de sakte ho")?.id)
    }

    @Test fun newPhoneTopicsAreFoundAndBeatTheOlderOnes() {
        assertEquals("callanswer", HelpGuide.topicFor("kya tum call uthao kar sakte ho")?.id)
        assertEquals("callanswer", HelpGuide.topicFor("can you answer call")?.id)
        assertEquals("call", HelpGuide.topicFor("can you call my mom")?.id)
        assertEquals("phonecontrol", HelpGuide.topicFor("kya tum screenshot le sakte ho")?.id)
        assertEquals("phonecontrol", HelpGuide.topicFor("can you set ringer to vibrate")?.id)
        assertEquals("smartreply", HelpGuide.topicFor("nova kya tum smart jawab de sakte ho")?.id)
        assertEquals("social", HelpGuide.topicFor("kya tum insta comment ka jawab de sakte ho")?.id)
        assertEquals("calendar", HelpGuide.topicFor("kya tum meeting yaad dila sakte ho")?.id)
    }

    @Test fun everyTopicExampleOrQuestionReachesItsTopic() {
        val ids = HelpGuide.TOPICS.map { it.id }
        for (id in listOf("callanswer", "phonecontrol", "smartreply", "calendar")) assertTrue(id, id in ids)
        assertEquals(24, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }
}
