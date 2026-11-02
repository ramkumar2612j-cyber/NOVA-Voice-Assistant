package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessHintRulesTest {
    @Test fun threeStates() {
        assertEquals(AccessHintRules.State.CONNECTED, AccessHintRules.state(true, true))
        assertEquals(AccessHintRules.State.CONNECTED, AccessHintRules.state(false, true))
        assertEquals(AccessHintRules.State.LISTED_NOT_CONNECTED, AccessHintRules.state(true, false))
        assertEquals(AccessHintRules.State.OFF, AccessHintRules.state(false, false))
        assertTrue(AccessHintRules.usable(AccessHintRules.State.CONNECTED))
        assertFalse(AccessHintRules.usable(AccessHintRules.State.OFF))
    }

    @Test fun alreadyOnNeverAsksForPermission() {
        val m = AccessHintRules.message(AccessHintRules.State.LISTED_NOT_CONNECTED, "screen nahi padh sakta", "I cannot read the screen", false)
        assertTrue(m.contains("OFF karke phir ON"))
        assertFalse(m.contains("permission"))
        val e = AccessHintRules.message(AccessHintRules.State.LISTED_NOT_CONNECTED, "x", "I cannot read the screen", true)
        assertTrue(e.contains("switch it OFF and ON"))
    }

    @Test fun offAndConnected() {
        assertTrue(AccessHintRules.message(AccessHintRules.State.OFF, "Screen padhne ke liye", "Reading the screen", false).contains("ON karo"))
        assertEquals("", AccessHintRules.message(AccessHintRules.State.CONNECTED, "a", "b", false))
    }
}
