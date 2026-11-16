package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.35: pure tests for FeatureVerify (the Android parts need a phone). NOT RUN in the sandbox (no kotlinc): the CI run is the first real run. */
class FeatureVerifyTest {
    @Test fun allProbesPass() {
        val lines = ArrayList<String>()
        FeatureVerify.probes { tag, name, detail -> lines.add(tag + " " + name + " " + detail) }
        assertTrue(lines.size >= 10)
        assertEquals(lines.filter { it.startsWith("FAIL") }.joinToString("\n"), 0, lines.count { it.startsWith("FAIL") })
    }

    @Test fun pageChecksReportMissingNames() {
        val lines = ArrayList<String>()
        FeatureVerify.pageChecks("{\"bridge\":3,\"cb\":2,\"missingBridge\":[\"foo\"],\"missingCb\":[],\"missingIds\":[],\"missingScripts\":[]}") { tag, name, detail -> lines.add("$tag $name $detail") }
        assertEquals(4, lines.size)
        assertTrue(lines[0].startsWith("FAIL") && lines[0].contains("foo"))
        assertTrue(lines[1].startsWith("PASS"))
    }

    @Test fun noPageAnswerIsFail() {
        val lines = ArrayList<String>()
        FeatureVerify.pageChecks(null) { tag, name, _ -> lines.add("$tag $name") }
        assertTrue(lines.single().startsWith("FAIL"))
    }

    @Test fun summaryWording() {
        assertTrue(FeatureVerify.summaryOf(listOf("PASS a", "PASS b")).contains("koi FAIL nahi"))
        assertTrue(FeatureVerify.summaryOf(listOf("PASS a", "FAIL b")).contains("1 FAIL"))
    }

    @Test fun askDetection() {
        assertTrue(FeatureVerify.isAsk("auto verify status"))
        assertTrue(FeatureVerify.isAsk("sab features check"))
        assertFalse(FeatureVerify.isAsk("mujhe ek logo chahiye"))
        assertFalse(FeatureVerify.isAsk("auto kya samjha"))
    }

    @Test fun healthLinesBeforeFirstRun() { assertTrue(FeatureVerify.healthLines().contains("AUTO VERIFY")) }
}
