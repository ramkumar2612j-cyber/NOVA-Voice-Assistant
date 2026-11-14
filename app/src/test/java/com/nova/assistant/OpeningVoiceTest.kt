package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningVoiceTest {
    @Test fun quoteIsTheOwnersLine() {
        assertEquals("Keep your friends rich and your enemies rich, and wait to find out which is which.", OpeningVoice.QUOTE)
        assertTrue(OpeningVoice.QUOTE.length < 700)
    }
}
