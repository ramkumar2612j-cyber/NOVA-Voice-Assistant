package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class WhatsAppCommandRulesTest {
    @Test fun parsesRequestedTwoWordContactWithoutTreatingBiswanAsApp() {
        val r = WhatsAppCommandRules.parse("WhatsApp Aman Biswan kab aaoge")!!
        assertEquals("Aman Biswan", r.recipient)
        assertEquals("kab aaoge", r.message)
        assertNull(WhatsAppCommandRules.parse("Biswan kab aaoge"))
    }

    @Test fun explicitDelimiterSupportsNaturalCommand() {
        val r = WhatsAppCommandRules.parse("WhatsApp Aman Biswan ko message bhejo kab aaoge")!!
        assertEquals("Aman Biswan", r.recipient)
        assertEquals("kab aaoge", r.message)
    }

    @Test fun contactPickerNeverGuesses() {
        assertEquals(0, Logic.pickContact("Aman Biswan", listOf("Aman Biswan", "Aman Kumar")))
        assertEquals(0, Logic.pickContact("Aman", listOf("Aman", "Rahul")))
        assertEquals(-1, Logic.pickContact("Unknown", listOf("Aman Kumar", "Rahul")))
        assertEquals(-1, Logic.pickContact("Aman", listOf("Aman Kumar", "Aman Singh")))
    }

    @Test fun businessFlagKeepsRecipientCleanAndOriginalTextRestored() {
        val r = WhatsAppCommandRules.parse("WhatsApp Business Rahul ko message bhejo Kal aa jana, ok!")!!
        assertTrue(r.business)
        assertEquals("Rahul", r.recipient)
        assertEquals("Kal aa jana, ok!", r.message)
        assertFalse(WhatsAppCommandRules.parse("WhatsApp Rahul ko message bhejo hi")!!.business)
    }
}
