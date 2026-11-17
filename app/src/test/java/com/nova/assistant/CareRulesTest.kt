package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CareRulesTest {
    @Test fun securityScanIsRecognised() {
        assertEquals(CareRules.Kind.SECURITY, CareRules.parse("jarvis security scan karo"))
        assertEquals(CareRules.Kind.SECURITY, CareRules.parse("virus check karo"))
        assertEquals(CareRules.Kind.SECURITY, CareRules.parse("poora phone full scan karo"))
    }
    @Test fun screenScanIsNotSecurity() {
        assertNull(CareRules.parse("jarvis screen scan karo"))
        assertNull(CareRules.parse("reel ka full scan karo"))
    }
    @Test fun hangIsRecognised() {
        assertEquals(CareRules.Kind.HANG, CareRules.parse("mobile hang kyu kar raha hai"))
        assertEquals(CareRules.Kind.HANG, CareRules.parse("phone bahut slow hai"))
    }
    @Test fun deleteWordsNeverMeanAction() {
        assertEquals(CareRules.Kind.DELETE, CareRules.parse("faltu cheeze delete karo"))
        assertEquals(CareRules.Kind.CLEAN, CareRules.parse("phone ki faltu cheeze saaf karo"))
    }
    @Test fun unrelatedSentenceIsNull() {
        assertNull(CareRules.parse("papa ko call karo"))
        assertNull(CareRules.parse(""))
        assertNull(CareRules.parse(null))
    }
    @Test fun lowRamRule() {
        assertEquals(true, CareRules.ramIsLow(500, 8000))
        assertEquals(false, CareRules.ramIsLow(4000, 8000))
        assertEquals(false, CareRules.ramIsLow(10, 0))
    }
}
