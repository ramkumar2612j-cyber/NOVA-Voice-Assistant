package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFixRulesTest {
    @Test fun onlyRedBuildsGetFixed() {
        assertTrue(AutoFixRules.decide(SelfBuildRules.RED, 0, 0, false, true).go)
        assertFalse(AutoFixRules.decide(SelfBuildRules.GREEN, 0, 0, false, true).go)
        assertFalse(AutoFixRules.decide(SelfBuildRules.RUNNING, 0, 0, false, true).go)
    }

    @Test fun capsStopTheLoop() {
        assertFalse(AutoFixRules.decide(SelfBuildRules.RED, AutoFixRules.MAX_STREAK, 0, false, true).go)
        assertFalse(AutoFixRules.decide(SelfBuildRules.RED, 0, AutoFixRules.MAX_PER_DAY, false, true).go)
        assertFalse(AutoFixRules.decide(SelfBuildRules.RED, 0, 0, true, true).go)
        assertFalse(AutoFixRules.decide(SelfBuildRules.RED, 0, 0, false, false).go)
    }

    @Test fun failedStepsAreNamed() {
        val j = """{"jobs":[{"name":"build","conclusion":"failure","steps":[{"name":"Set up JDK","conclusion":"success"},{"name":"Run unit tests","conclusion":"failure"}]},{"name":"lint","conclusion":"success","steps":[]}]}"""
        assertEquals(listOf("build: Run unit tests"), AutoFixRules.failedSteps(j))
        assertEquals(emptyList<String>(), AutoFixRules.failedSteps("not json"))
    }

    @Test fun wishNeverEmpty() {
        assertTrue(AutoFixRules.wishFor(emptyList()).contains("Actions log"))
        assertTrue(AutoFixRules.wishFor(listOf("a: b")).contains("a: b"))
    }
}
