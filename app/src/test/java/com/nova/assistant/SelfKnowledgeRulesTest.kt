package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfKnowledgeRulesTest {
    @Test fun askPhrases() {
        assertTrue(SelfKnowledgeRules.isAsk("jarvis apne baare me batao"))
        assertTrue(SelfKnowledgeRules.isAsk("tum kaun ho"))
        assertFalse(SelfKnowledgeRules.isAsk("aaj ka mausam"))
        assertFalse(SelfKnowledgeRules.isAsk(""))
    }
    @Test fun sectionPicking() {
        assertEquals(SelfKnowledgeRules.LAPTOP, SelfKnowledgeRules.section("apne baare me laptop"))
        assertEquals(SelfKnowledgeRules.BUSINESS, SelfKnowledgeRules.section("apne baare me business"))
        assertEquals(SelfKnowledgeRules.LIMITS, SelfKnowledgeRules.section("tumhari limits"))
        assertEquals(SelfKnowledgeRules.OVERVIEW, SelfKnowledgeRules.section("tum kaun ho"))
    }
    @Test fun everyAreaIsHonestAboutTesting() {
        listOf(SelfKnowledgeRules.PHONE, SelfKnowledgeRules.LAPTOP, SelfKnowledgeRules.BUSINESS).forEach {
            assertTrue(SelfKnowledgeRules.text(it, "7", "").contains("NOT TESTED"))
        }
    }
    @Test fun limitsNeverPromiseSecurityBypass() {
        val t = SelfKnowledgeRules.text(SelfKnowledgeRules.LIMITS, "7", "")
        assertTrue(t.contains("bypass nahi karta") && t.contains("HAAN"))
    }
    @Test fun overviewShowsVersionAndLiveLine() {
        val t = SelfKnowledgeRules.text(SelfKnowledgeRules.OVERVIEW, "7.183", "Jaanch me koi FAIL nahi.")
        assertTrue(t.contains("7.183") && t.contains("Jaanch me koi FAIL nahi."))
    }
}
