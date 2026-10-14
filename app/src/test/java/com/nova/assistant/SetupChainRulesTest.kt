package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChainRulesTest {
    @Test fun runsOnlyWhileSomethingIsMissingAndBelowTheLimit() {
        assertFalse(SetupChainRules.shouldRun(0, 0))
        assertTrue(SetupChainRules.shouldRun(3, 0))
        assertTrue(SetupChainRules.shouldRun(1, SetupChainRules.MAX_RUNS - 1))
        assertFalse(SetupChainRules.shouldRun(5, SetupChainRules.MAX_RUNS))
    }

    @Test fun opensMissingPagesInOrderOncePerRun() {
        val missing = setOf("access", "overlay", "battery")
        val done = HashSet<String>()
        val seen = ArrayList<String>()
        while (true) { val s = SetupChainRules.nextStep(missing, done, emptyMap()) ?: break; seen.add(s); done.add(s) }
        assertEquals(listOf("overlay", "battery", "access"), seen)
    }

    @Test fun anItemOpenedTooOftenIsLeftAlone() {
        val opens = mapOf("overlay" to SetupChainRules.MAX_OPENS)
        assertEquals("access", SetupChainRules.nextStep(setOf("overlay", "access"), emptySet(), opens))
        assertNull(SetupChainRules.nextStep(setOf("overlay"), emptySet(), opens))
    }

    @Test fun unknownOrAlreadyGrantedItemsAreNeverOpened() {
        assertNull(SetupChainRules.nextStep(emptySet(), emptySet(), emptyMap()))
        assertNull(SetupChainRules.nextStep(setOf("something_else"), emptySet(), emptyMap()))
    }

    @Test fun brandAutostartScreenIsOfferedOnlyOnce() {
        assertEquals("autostart", SetupChainRules.nextStep(setOf("autostart"), emptySet(), emptyMap()))
        assertNull(SetupChainRules.nextStep(setOf("autostart"), emptySet(), mapOf("autostart" to 1)))
        assertEquals(1, SetupChainRules.maxOpens("autostart"))
        assertEquals(SetupChainRules.MAX_OPENS, SetupChainRules.maxOpens("overlay"))
    }
}
