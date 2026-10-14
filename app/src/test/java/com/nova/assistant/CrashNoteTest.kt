package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashNoteTest {
    @Test fun crashTextHasTimeThreadClassAndFrames() {
        val e = IllegalStateException("boom")
        val t = CrashNote.format("main", e, 1234L)
        assertTrue(t.startsWith("at=1234 thread=main\n"))
        assertTrue(t.contains("java.lang.IllegalStateException: boom"))
        assertTrue(t.contains("  at "))
        assertTrue(t.lines().size <= 14)
    }

    @Test fun keyLikeTokensAreMaskedInTheCrashMessage() {
        val secret = "TEST_GEMINI_KEY_PLACEHOLDER_1234567890"
        val t = CrashNote.format("main", RuntimeException("bad key " + secret), 1L)
        assertFalse(t.contains(secret))
        assertTrue(t.contains("***"))
    }

    @Test fun eventListKeepsOnlyTheNewestLines() {
        var s = ""
        for (i in 1..12) s = CrashNote.trimLines(s, "$i connected")
        val lines = s.trim().split("\n")
        assertEquals(CrashNote.MAX_EVENTS, lines.size)
        assertEquals("12 connected", lines.last())
        assertEquals("5 connected", lines.first())
    }
}
