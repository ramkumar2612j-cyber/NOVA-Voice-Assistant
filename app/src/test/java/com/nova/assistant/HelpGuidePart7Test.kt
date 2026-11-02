package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.20 Part 7: the six topics added after the Skills-vs-UI audit are found by plain questions and do not steal older topics. */
class HelpGuidePart7Test {
    @Test fun newTopicsExist() {
        val ids = HelpGuide.TOPICS.map { it.id }
        for (id in listOf("photoedit", "sitemedia", "chathistory", "batterysos", "waitsec", "musicplay")) assertTrue(id, id in ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun newTopicsAreFound() {
        assertEquals("photoedit", HelpGuide.topicFor("kya tum photo edit kar sakte ho")?.id)
        assertEquals("sitemedia", HelpGuide.topicFor("can you use website media")?.id)
        assertEquals("chathistory", HelpGuide.topicFor("kya tum meri chats dikha sakte ho")?.id)
        assertEquals("batterysos", HelpGuide.topicFor("kya tum battery sos kar sakte ho")?.id)
        assertEquals("musicplay", HelpGuide.topicFor("kya tum spotify chala sakte ho")?.id)
    }

    @Test fun olderTopicsStillWin() {
        assertEquals("image", HelpGuide.topicFor("kya tum photo bana sakte ho")?.id)
        assertEquals("website", HelpGuide.topicFor("kya tum website bana sakte ho")?.id)
        assertEquals("download", HelpGuide.topicFor("kya tum whatsapp download kar sakte ho")?.id)
    }
}
