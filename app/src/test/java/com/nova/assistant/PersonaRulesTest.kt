package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.48 Part 9: JARVIS persona. Speaking style only; never on errors, questions, confirmations or action kinds. */
class PersonaRulesTest {
    private val R = "Torch jala di."
    private val FIRST = Long.MAX_VALUE
    private fun d(
        reply: String = R, kind: String = "local_chat", owner: String = "Sonu", en: Boolean = false, hour: Int = 10, seed: Int = 0,
        gap: Long = FIRST, pending: Boolean = false, failed: Boolean = false, silent: Boolean = false, on: Boolean = true
    ) = PersonaRules.decorate(reply, kind, owner, en, hour, seed, gap, pending, failed, silent, on)

    @Test fun styleCycle() {
        assertEquals(0, PersonaRules.styleFor(0)); assertEquals(1, PersonaRules.styleFor(2)); assertEquals(2, PersonaRules.styleFor(4))
        assertEquals(3, PersonaRules.styleFor(1)); assertEquals(3, PersonaRules.styleFor(3)); assertEquals(3, PersonaRules.styleFor(5))
        assertEquals(3, PersonaRules.styleFor(-7))
    }

    @Test fun nameLineWitTailAndFeelingTail() {
        assertEquals("Ji Sonu. Torch jala di.", d(seed = 0))
        assertEquals("Torch jala di. Aur hukum?", d(seed = 2))
        assertEquals("Torch jala di. Itni raat ko jaag rahe ho? Main to jaagta hi hoon.", d(seed = 4, hour = 23))
        assertEquals("At your service, Sonu. Torch is on.", d(reply = "Torch is on.", en = true, seed = 0))
    }

    @Test fun noNameFallsBackToWitTail() { assertEquals("Torch jala di. Aur hukum?", d(owner = "", seed = 0)) }

    @Test fun offSwitchAndNoTouchTurnsKeepReplyExactly() {
        assertEquals(R, d(on = false, seed = 0))
        for (s in listOf(1, 3, 5)) assertEquals(R, d(seed = s))
    }

    @Test fun neverOnConfirmationErrorSilentOrActionKinds() {
        assertEquals(R, d(pending = true)); assertEquals(R, d(failed = true)); assertEquals(R, d(silent = true))
        for (k in listOf("whatsapp_send", "call", "rec", "video_rec", "screen", "agent", "publish", "site", "gmail", "connector", "image"))
            assertEquals(R, d(kind = k))
        assertEquals("", d(reply = ""))
    }

    @Test fun neverNextToProblemsQuestionsOrLongAnswers() {
        for (r in listOf("Permission nahi mili", "Kuch gadbad ho gayi", "Kaun sa gaana?", "Could not find it", "Haan ya nahi bolo"))
            assertEquals(r, d(reply = r))
        val long = "a".repeat(PersonaRules.MAX_REPLY + 1)
        assertEquals(long, d(reply = long))
    }

    @Test fun cooldownBetweenTouches() {
        assertEquals(R, d(gap = 10_000L)); assertEquals(R, d(gap = 0L))
        assertEquals("Ji Sonu. Torch jala di.", d(gap = PersonaRules.MIN_GAP_MS))
    }

    @Test fun originalReplyIsAlwaysKeptWhole() {
        for (s in 0..60) for (h in listOf(2, 9, 14, 19)) assertTrue(d(seed = s, hour = h).contains(R))
    }

    @Test fun nameIsCleanedAndCapped() {
        assertEquals("Sonu Kumar", PersonaRules.speakName("  Sonu  Kumar!! "))
        assertEquals(24, PersonaRules.speakName("a".repeat(40)).length)
        assertEquals("", PersonaRules.speakName("12345 !!"))
    }

    @Test fun daypartsAndSafeKinds() {
        assertEquals(0, PersonaRules.daypart(5)); assertEquals(0, PersonaRules.daypart(11)); assertEquals(1, PersonaRules.daypart(12))
        assertEquals(1, PersonaRules.daypart(16)); assertEquals(2, PersonaRules.daypart(17)); assertEquals(2, PersonaRules.daypart(21))
        assertEquals(3, PersonaRules.daypart(22)); assertEquals(3, PersonaRules.daypart(0)); assertEquals(3, PersonaRules.daypart(4))
        assertFalse("whatsapp_send" in PersonaRules.SAFE_KINDS)
        assertTrue(PersonaRules.looksLikeProblem("Done?"))
    }
}
