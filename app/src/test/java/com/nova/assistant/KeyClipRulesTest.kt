package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class KeyClipRulesTest {
    @Test fun recognisesEachShape() {
        assertEquals(KeyClipRules.GROQ, KeyClipRules.kind("gsk_" + "a".repeat(48)))
        assertEquals(KeyClipRules.OPENROUTER, KeyClipRules.kind("sk-or-v1-" + "b".repeat(50)))
        assertEquals(KeyClipRules.GEMINI, KeyClipRules.kind("AIza" + "c".repeat(35)))
    }
    @Test fun ordinaryTextIsNotAKey() {
        assertNull(KeyClipRules.kind(null))
        assertNull(KeyClipRules.kind("hello nova kaise ho"))
        assertNull(KeyClipRules.kind("gsk_short"))
        assertNull(KeyClipRules.kind("https://console.groq.com/keys"))
        assertNull(KeyClipRules.kind("gsk_" + "a".repeat(20) + " " + "b".repeat(20)))
    }
}
