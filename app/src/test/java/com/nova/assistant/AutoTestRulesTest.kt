package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTestRulesTest {
    @Test fun runsOnlyWhenReadyAndStateChanged() {
        assertFalse(AutoTestRules.shouldRun(false, "a", "", 100_000, 0, false))          // no key / no mic permission yet
        assertTrue(AutoTestRules.shouldRun(true, "a", "", 100_000, 0, false))            // first time ready
        assertFalse(AutoTestRules.shouldRun(true, "a", "a", 500_000, 100_000, false))    // nothing changed
        assertTrue(AutoTestRules.shouldRun(true, "b", "a", 500_000, 100_000, false))     // permission/key changed
        assertFalse(AutoTestRules.shouldRun(true, "b", "a", 130_000, 100_000, false))    // too soon
        assertFalse(AutoTestRules.shouldRun(true, "b", "a", 500_000, 100_000, true))     // already running
    }

    @Test fun summaries() {
        assertEquals("AUTO TEST: 40 pass, koi FAIL nahi, 3 WARN (band features)", AutoTestRules.summary("x\nSUMMARY pass=40 fail=0 warn=3 skip=1"))
        assertTrue(AutoTestRules.summary("SUMMARY pass=30 fail=2 warn=0 skip=0").contains("2 FAIL"))
        assertTrue(AutoTestRules.summary("garbage").contains("report nahi"))
    }

    @Test fun waBizSelfCheckAllPass() {
        for ((ok, name, _) in WaBiz.selfCheck()) assertTrue(name, ok)
    }

    @Test fun hardwareChecksRunOncePerInstalledVersion() {
        assertTrue(AutoTestRules.needsFull("", "2031"))        // first run after install
        assertTrue(AutoTestRules.needsFull("2031", "2032"))    // after an update
        assertFalse(AutoTestRules.needsFull("2032", "2032"))   // same version: quiet run only
        assertFalse(AutoTestRules.needsFull("", ""))           // unknown version: never loop
    }
}
