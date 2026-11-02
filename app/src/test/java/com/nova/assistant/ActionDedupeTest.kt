package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class ActionDedupeTest {
    @Test fun secondClaimInsideWindowIsRejected() {
        val seen = HashMap<String, Long>()
        assertTrue(ActionDedupe.claim("call|123", 1000L, seen = seen))
        assertFalse(ActionDedupe.claim("call|123", 1000L + 7999L, seen = seen))
    }

    @Test fun claimAllowedAgainAfterWindow() {
        val seen = HashMap<String, Long>()
        assertTrue(ActionDedupe.claim("call|123", 1000L, seen = seen))
        assertTrue(ActionDedupe.claim("call|123", 1000L + ActionDedupe.WINDOW_MS, seen = seen))
    }

    @Test fun blankKeyAndDifferentKeys() {
        val seen = HashMap<String, Long>()
        assertFalse(ActionDedupe.claim("", 1L, seen = seen))
        assertTrue(ActionDedupe.claim("a", 1L, seen = seen))
        assertTrue(ActionDedupe.claim("b", 1L, seen = seen))
    }
}
