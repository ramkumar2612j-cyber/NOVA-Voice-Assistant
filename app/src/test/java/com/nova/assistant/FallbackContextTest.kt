package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackContextTest {
    private fun p(r: String, t: String) = Pair(r, t)

    @Test fun modelBecomesAssistantAndStartsWithUser() {
        val t = FallbackContext.turns(listOf(p("model", "stray"), p("user", "hi"), p("model", "hello")))
        assertEquals(listOf("user", "assistant"), t.map { it.role })
        assertEquals("hi", t.first().text)
    }

    @Test fun turnsAlternateAndBlankIsDropped() {
        val t = FallbackContext.turns(listOf(p("user", "a"), p("user", "b"), p("model", ""), p("model", "c"), p("user", "d")))
        assertEquals(listOf("b", "c", "d"), t.map { it.text })
        assertEquals(listOf("user", "assistant", "user"), t.map { it.role })
    }

    @Test fun boundedByCountAndLength() {
        val many = (1..20).flatMap { listOf(p("user", "q$it"), p("model", "a$it")) }
        assertTrue(FallbackContext.turns(many).size <= FallbackContext.MAX_MSGS)
        val long = FallbackContext.turns(listOf(p("user", "x".repeat(1000))))
        assertEquals(FallbackContext.MAX_MSG_CHARS, long.first().text.length)
    }

    @Test fun unknownRolesAreIgnored() {
        assertTrue(FallbackContext.turns(listOf(p("system", "x"), p("tool", "y"))).isEmpty())
    }

    @Test fun taskLineOnlyForFreshTaskAndIsRedacted() {
        val line = FallbackContext.taskLine("whatsapp pe rahul ko msg", "RUNNING", "", 60_000L, false)
        assertTrue(line.contains("whatsapp pe rahul ko msg"))
        assertTrue(line.contains("chal raha hai"))
        assertEquals("", FallbackContext.taskLine("old task", "FAILED", "", FallbackContext.TASK_FRESH_MS + 1L, false))
        assertEquals("", FallbackContext.taskLine("  ", "RUNNING", "", 10L, true))
        val red = FallbackContext.taskLine("mail rahul@example.com token=abc12345", "RUNNING", "", 10L, true)
        assertFalse(red.contains("rahul@example.com"))
        assertFalse(red.contains("abc12345"))
    }

    @Test fun unverifiedTaskIsNeverCalledFinishedAndVerified() {
        val line = FallbackContext.taskLine("x", "REPORTED_COMPLETE", "", 10L, true)
        assertTrue(line.contains("NOT verified"))
    }

    @Test fun systemWithAddsOnlyWhenThereIsALine() {
        assertEquals("base", FallbackContext.systemWith("base", ""))
        assertEquals("base\nline", FallbackContext.systemWith("base", "line"))
    }

    @Test fun localEarlierIsShort() {
        val turns = (1..6).map { FallbackContext.Turn(if (it % 2 == 1) "user" else "assistant", "m".repeat(500) + it) }
        val e = FallbackContext.localEarlier(turns)
        assertEquals(FallbackContext.LOCAL_MSGS, e.size)
        assertTrue(e.all { it.length <= FallbackContext.LOCAL_CHARS + 6 })
        assertTrue(e.last().startsWith("nova: "))
    }

    // ---- v6.71 full chat window
    private fun chat(n: Int) = (1..n).flatMap { listOf(Pair("me", "question $it"), Pair("ai", "answer $it")) }

    @Test fun fullWindowKeepsEveryMessageThatFits() {
        val w = FallbackContext.fullWindow(chat(40), FallbackContext.FULL_GEMINI_CHARS)
        assertEquals(80, w.turns.size)
        assertFalse(w.cut)
        assertEquals("question 1", w.turns.first().text)
        assertEquals("answer 40", w.turns.last().text)
    }

    @Test fun fullWindowEndsWithAssistantAndStartsWithUser() {
        val raw = chat(3) + listOf(Pair("me", "unanswered"))
        val w = FallbackContext.fullWindow(raw, 10_000)
        assertEquals("user", w.turns.first().role)
        assertEquals("assistant", w.turns.last().role)
        assertFalse(w.turns.any { it.text == "unanswered" })
    }

    @Test fun fullWindowJoinsSameSideInsteadOfDroppingText() {
        val w = FallbackContext.fullWindow(listOf(Pair("me", "a"), Pair("me", "b"), Pair("ai", "c")), 1000)
        assertEquals(2, w.turns.size)
        assertEquals("a\nb", w.turns[0].text)
    }

    @Test fun fullWindowDropsOldestFirstAndSaysSo() {
        val raw = chat(100)
        val w = FallbackContext.fullWindow(raw, 300)
        assertTrue(w.cut)
        assertTrue(w.turns.sumOf { it.text.length } <= 300)
        assertEquals("answer 100", w.turns.last().text)
        assertEquals("user", w.turns.first().role)
        assertTrue(FallbackContext.cutNote(true).contains("oldest"))
    }

    @Test fun fullWindowOverBudgetNewestAnswerIsReportedAsCut() {
        val w = FallbackContext.fullWindow(listOf(Pair("me", "q"), Pair("ai", "x".repeat(1400))), 10)
        assertTrue(w.cut)   // the window must start with the user, so nothing is sent and the cut is reported
        assertTrue(w.turns.isEmpty())
    }

    @Test fun fullWindowLimitsEachMessage() {
        val w = FallbackContext.fullWindow(listOf(Pair("me", "y".repeat(9000)), Pair("ai", "ok")), 100_000)
        assertTrue(w.turns.first().text.length <= FallbackContext.FULL_MSG_CHARS)
    }

    @Test fun fullWindowIgnoresStatusRoles() {
        val w = FallbackContext.fullWindow(listOf(Pair("sys", "step"), Pair("me", "hi"), Pair("mode", "x"), Pair("ai", "hello")), 1000)
        assertEquals(listOf("hi", "hello"), w.turns.map { it.text })
    }
}
