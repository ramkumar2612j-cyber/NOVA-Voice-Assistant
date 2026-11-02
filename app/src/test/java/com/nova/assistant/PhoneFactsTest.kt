package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v6.99 Part 4: phone / self facts text. Pure, no Android. Written, NOT RUN. */
class PhoneFactsTest {
    @Test fun missingAccessIsListedWithTapPath() {
        val t = PhoneFacts.describe("Redmi Test", 34, mapOf("mic" to true))
        assertTrue(t.contains("Redmi Test") && t.contains("API 34"))
        assertTrue(t.contains("Allowed for NOVA now: Microphone."))
        assertTrue(t.contains("Accessibility (tap and type in other apps) -> Phone Settings > Accessibility"))
        assertFalse(t.contains("Microphone ->"))
    }

    @Test fun everythingAllowedHasNoMissingList() {
        val all = PhoneFacts.ACCESS.associate { it.first to true }
        val t = PhoneFacts.describe("X", 30, all)
        assertFalse(t.contains("NOT allowed yet"))
    }

    @Test fun actFirstRuleAlwaysPresent() {
        assertTrue(PhoneFacts.describe("", 26, emptyMap()).contains("do it first and tell the result after"))
        assertTrue(PhoneFacts.describe("", 26, emptyMap()).contains("nothing yet"))
    }
}
