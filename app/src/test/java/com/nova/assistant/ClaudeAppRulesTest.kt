package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeAppRulesTest {
    @Test fun routeTriggers() {
        assertTrue(ClaudeAppRules.isRoute("jarvis claude se poochho ki solar panel kaise lagte hain"))
        assertTrue(ClaudeAppRules.isRoute("claude khol do"))
        assertFalse(ClaudeAppRules.isRoute("jarvis torch on karo"))
    }
    @Test fun questionIsExtracted() {
        assertEquals("ki solar panel kaise lagte hain", ClaudeAppRules.questionOf("jarvis claude se poochho ki solar panel kaise lagte hain"))
        assertEquals("", ClaudeAppRules.questionOf("claude khol do"))
    }
    @Test fun paymentQuestionsAreRefused() {
        assertTrue(ClaudeAppRules.isDenied("mera otp kaise bhejun"))
        assertFalse(ClaudeAppRules.isDenied("solar panel kaise lagte hain"))
    }
}
