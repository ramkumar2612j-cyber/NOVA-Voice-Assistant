package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.187 BARGE-IN COMMAND: pure text rules. Written, NOT RUN. */
class BargeInCommandRulesTest {
    @Test fun quietOrLowConfidenceIsIgnored() {
        assertEquals(BargeInCommandRules.Result.NONE, BargeInCommandRules.decide("mummy ko call karo", "kuch jawab", 0.5f, 2000.0))
        assertEquals(BargeInCommandRules.Result.NONE, BargeInCommandRules.decide("mummy ko call karo", "kuch jawab", 0.9f, 10.0))
    }

    @Test fun oneWordIsIgnored() {
        assertEquals(BargeInCommandRules.Result.NONE, BargeInCommandRules.decide("haan", "kuch jawab", 0.95f, 2000.0))
    }

    @Test fun ownEchoedSpeechIsIgnored() {
        val spoken = "aaj mausam bahut accha hai aur dhoop nikli hui hai"
        assertEquals(BargeInCommandRules.Result.NONE, BargeInCommandRules.decide("mausam bahut accha hai", spoken, 0.95f, 2000.0))
    }

    @Test fun realInterruptionIsCaptured() {
        val spoken = "aaj mausam bahut accha hai aur dhoop nikli hui hai"
        assertEquals(BargeInCommandRules.Result.CAPTURE, BargeInCommandRules.decide("mummy ko call karo", spoken, 0.9f, 2000.0))
    }
}
