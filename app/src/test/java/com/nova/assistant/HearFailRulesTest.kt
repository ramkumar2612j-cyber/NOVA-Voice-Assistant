package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.115: what NOVA does when the words after the wake word are unreadable. Written, NOT RUN here. */
class HearFailRulesTest {
    private val T0 = 10_000_000L

    @Test fun veryShortAudioIsAFalseWakeAndSleepsSilently() =
        assertEquals(HearFailRules.Action.SLEEP, HearFailRules.decide(0.6, 1, false, false, false, 0L, T0))

    @Test fun followUpIsAlwaysSilent() =
        assertEquals(HearFailRules.Action.SLEEP, HearFailRules.decide(4.0, 5, false, false, true, 0L, T0))

    @Test fun firstRealFailureAsksAgain() =
        assertEquals(HearFailRules.Action.RETRY, HearFailRules.decide(3.0, 1, false, false, false, 0L, T0))

    @Test fun secondFailureWithoutGroqOrHindiPackExplainsOnce() {
        assertEquals(HearFailRules.Action.SETUP_HELP, HearFailRules.decide(3.0, 2, false, false, false, 0L, T0))
        assertEquals(HearFailRules.Action.RETRY, HearFailRules.decide(3.0, 3, false, false, false, T0 - 60_000L, T0))
        assertEquals(HearFailRules.Action.SETUP_HELP, HearFailRules.decide(3.0, 3, false, false, false, T0 - HearFailRules.HELP_COOLDOWN_MS - 1, T0))
    }

    @Test fun withGroqOrHindiPackItNeverBlamesTheSetup() {
        assertEquals(HearFailRules.Action.RETRY, HearFailRules.decide(3.0, 9, true, false, false, 0L, T0))
        assertEquals(HearFailRules.Action.RETRY, HearFailRules.decide(3.0, 9, false, true, false, 0L, T0))
    }

    @Test fun helpTextMentionsTheTwoFixes() {
        assertTrue(HearFailRules.helpText(false).contains("Groq"))
        assertTrue(HearFailRules.helpText(false).contains("HINDI PACK"))
        assertTrue(HearFailRules.helpText(true).contains("Groq"))
    }
}
