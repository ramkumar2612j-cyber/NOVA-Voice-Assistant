package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSessionRulesTest {
    private fun m(r: String, t: String, at: Long = 0L) = ChatSessionRules.Msg(r, t, at)

    @Test fun idsDifferEvenInTheSameMillisecond() {
        assertNotEquals(ChatSessionRules.newId(1000L, 1), ChatSessionRules.newId(1000L, 2))
        assertTrue(ChatSessionRules.newId(1000L, -5).startsWith("c"))
    }

    @Test fun onlyMeAndAiAreStored() {
        assertTrue(ChatSessionRules.isStorable("me", "torch jala do"))
        assertTrue(ChatSessionRules.isStorable("ai", "Torch on"))
        assertFalse(ChatSessionRules.isStorable("sys", "step 3: tap"))
        assertFalse(ChatSessionRules.isStorable("mode", "quiet"))
        assertFalse(ChatSessionRules.isStorable("me", "   "))
        assertFalse(ChatSessionRules.isStorable("me", "\uD83C\uDFA4 "))
    }

    @Test fun micPrefixIsRemovedAndTextIsCapped() {
        assertEquals("torch on", ChatSessionRules.cleanText("\uD83C\uDFA4 torch on"))
        assertEquals(ChatSessionRules.MAX_TEXT, ChatSessionRules.cleanText("x".repeat(5000)).length)
    }

    @Test fun titleIsShortAndOneLine() {
        assertEquals("Nayi chat", ChatSessionRules.titleFrom("  "))
        assertEquals("torch jala do", ChatSessionRules.titleFrom("\uD83C\uDFA4 torch   jala\ndo"))
        val t = ChatSessionRules.titleFrom("a".repeat(100))
        assertEquals(ChatSessionRules.TITLE_MAX, t.length)
        assertTrue(t.endsWith("\u2026"))
    }

    @Test fun newestKeepsTheEnd() {
        assertEquals(listOf(3, 4, 5), ChatSessionRules.newest(listOf(1, 2, 3, 4, 5), 3))
        assertEquals(listOf(1, 2), ChatSessionRules.newest(listOf(1, 2), 3))
    }

    @Test fun contextNeverStartsWithAnAnswer() {
        val all = listOf(m("me", "a"), m("ai", "b"), m("me", "c"), m("ai", "d"))
        val ctx = ChatSessionRules.contextTurns(all.takeLast(3))
        assertEquals("me", ctx.first().role)
        assertEquals(listOf("c", "d"), ctx.map { it.text })
    }

    @Test fun contextIsBoundedByCountAndCharacters() {
        val many = (1..40).flatMap { listOf(m("me", "q$it"), m("ai", "a$it")) }
        assertTrue(ChatSessionRules.contextTurns(many).size <= ChatSessionRules.CONTEXT_MSGS)
        val big = listOf(m("me", "x".repeat(2000)), m("ai", "y".repeat(2000)), m("me", "z"), m("ai", "w"))
        val ctx = ChatSessionRules.contextTurns(big)
        assertTrue(ctx.sumOf { it.text.length } <= ChatSessionRules.CONTEXT_CHARS)
        assertEquals("me", ctx.first().role)
    }

    @Test fun emptyChatGivesEmptyContext() {
        assertTrue(ChatSessionRules.contextTurns(emptyList()).isEmpty())
        assertTrue(ChatSessionRules.contextTurns(listOf(m("ai", "hello"))).isEmpty())
    }

    @Test fun countLabel() {
        assertEquals("1 message", ChatSessionRules.countLabel(1))
        assertEquals("0 messages", ChatSessionRules.countLabel(0))
        assertEquals("12 messages", ChatSessionRules.countLabel(12))
    }
}
