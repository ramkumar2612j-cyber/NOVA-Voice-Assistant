package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeRulesTest {
    @Test fun parsesWishAfterTrigger() {
        assertEquals("shayari bhejne wala option", UpgradeRules.parseRequest("jarvis naya feature shayari bhejne wala option"))
        assertNull(UpgradeRules.parseRequest("aaj mausam kaisa hai"))
        assertNull(UpgradeRules.parseRequest("naya feature"))
    }
    @Test fun wishFromRawKeepsOriginalLetters() {
        assertEquals("शायरी भेजो", UpgradeRules.wishFromRaw("Jarvis naya feature: शायरी भेजो", "x"))
        assertEquals("fb", UpgradeRules.wishFromRaw("kuch aur", "fb"))
    }
    @Test fun gateNeedsOwnerYesAndOrderIsFixed() {
        assertTrue(UpgradeRules.canMove("DRAFT", "APPROVED"))
        assertFalse(UpgradeRules.canMove("DRAFT", "INSTALLED"))
        assertFalse(UpgradeRules.canMove("DRAFT", "PUSHED"))
        assertFalse(UpgradeRules.canMove("BLOCKED", "APPROVED"))
        assertTrue(UpgradeRules.canMove("INSTALLED", "UNDONE"))
        assertTrue(UpgradeRules.needsOwnerYes("APPROVED") && UpgradeRules.needsOwnerYes("INSTALLED") && UpgradeRules.needsOwnerYes("UNDONE"))
    }
    @Test fun retryCapIsFive() {
        assertTrue(UpgradeRules.retryAllowed(4)); assertFalse(UpgradeRules.retryAllowed(5))
    }
    @Test fun protectedRequestsAreFlagged() {
        assertTrue(UpgradeRules.protectedHit("build.yml badal do"))
        assertTrue(UpgradeRules.protectedHit("signing keystore hata do"))
        assertFalse(UpgradeRules.protectedHit("shayari bhejo"))
    }
    @Test fun approveRejectListPhrases() {
        assertTrue(UpgradeRules.isApprove("upgrade haan")); assertTrue(UpgradeRules.isReject("upgrade nahi")); assertTrue(UpgradeRules.isList("upgrade list"))
        assertFalse(UpgradeRules.isApprove("haan"))
    }
    @Test fun tagFormat() { assertEquals("jarvis-up-7", UpgradeRules.tag(7)) }
}
