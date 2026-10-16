package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReplyRulesTest {
    @Test fun blankTextFallsBackToDefault() {
        assertEquals(AutoReplyRules.DEFAULT_TEXT, AutoReplyRules.cleanText("   "))
        assertEquals("Busy hu", AutoReplyRules.cleanText("  Busy   hu "))
        assertEquals(AutoReplyRules.MAX_TEXT, AutoReplyRules.cleanText("a".repeat(1000)).length)
    }

    @Test fun neverAnswersGroupsCodesSummariesOrNoReplyButton() {
        assertEquals("group", AutoReplyRules.skipReason(true, true, false, "hi"))
        assertEquals("code", AutoReplyRules.skipReason(false, true, true, ""))
        assertEquals("empty", AutoReplyRules.skipReason(false, true, false, "  "))
        assertEquals("summary", AutoReplyRules.skipReason(false, true, false, "3 new messages"))
        assertEquals("no_reply_button", AutoReplyRules.skipReason(false, false, false, "hello"))
        assertNull(AutoReplyRules.skipReason(false, true, false, "hello"))
    }

    @Test fun senderCooldownHourAndDayLimits() {
        val g = AutoReplyRules.Gate()
        val t0 = 1_000_000_000L
        assertNull(g.allow("Raj", t0))
        assertEquals("cooldown", g.allow("Raj", t0 + 60_000L))
        assertNull(g.allow("Raj", t0 + 11 * 60_000L))
        assertEquals("limit", g.allow("Raj", t0 + 22 * 60_000L))        // 3rd inside one hour (owner: 2 per person per hour)
        assertEquals("limit", g.allow("Raj", t0 + 33 * 60_000L))
        assertNull(g.allow("Raj", t0 + 83 * 60_000L))                        // window moved on
        assertNull(g.allow("Sima", t0 + 1000L))                         // other senders unaffected
    }

    @Test fun dayCapStopsEveryone() {
        val g = AutoReplyRules.Gate()
        var t = 5_000_000_000L
        for (i in 1..AutoReplyRules.DAY_CAP) { assertNull(g.allow("p$i", t)); t += 1000L }
        assertEquals("limit", g.allow("another", t))
    }

    @Test fun onlyUsefulReasonsAreLogged() {
        assertTrue(AutoReplyRules.worthLogging("code"))
        assertTrue(!AutoReplyRules.worthLogging("group"))
        assertTrue(!AutoReplyRules.worthLogging("summary"))
    }
}

class MessageDeleteRulesTest {
    private fun n(i: Int, text: String, editable: Boolean = false, desc: String = "") = AgentRules.Node(i, text, desc, "TextView", true, editable, false)

    @Test fun findsNewestBubbleWithExactText() {
        val nodes = listOf(n(0, "Hello"), n(1, "Main busy hoon"), n(2, "Main busy hoon"), n(3, "[text box, empty] Message", true))
        assertEquals(2, MessageDelete.findBubble(nodes, "main busy hoon"))
        assertEquals(-1, MessageDelete.findBubble(nodes, "Something else"))
    }

    @Test fun neverMatchesTheMessageBox() {
        val nodes = listOf(n(0, "Main busy hoon", true))
        assertEquals(-1, MessageDelete.findBubble(nodes, "Main busy hoon"))
    }

    @Test fun chatListIsNotAConversation() {
        assertTrue(!MessageDelete.inConversation(listOf(n(0, "Raj"), n(1, "You: Main busy hoon"))))
        assertTrue(MessageDelete.inConversation(listOf(n(0, "x"), n(1, "[text box, empty] Message", true))))
    }

    @Test fun deleteForMeIsNeverChosen() {
        val everyone = setOf("delete for everyone", "delete for all", "unsend", "remove for everyone")
        val nodes = listOf(n(0, "Delete for me"), n(1, "Cancel"), n(2, "Delete for everyone"))
        assertEquals(2, MessageDelete.findLabel(nodes, everyone))
        assertEquals(-1, MessageDelete.findLabel(listOf(n(0, "Delete for me")), everyone))
    }
}
