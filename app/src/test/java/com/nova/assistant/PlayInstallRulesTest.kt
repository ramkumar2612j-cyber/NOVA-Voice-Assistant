package com.nova.assistant

import com.nova.assistant.PlayInstallRules.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v6.97 wip14 Part 3: Play Store install decision rules. Pure text, no Android. */
class PlayInstallRulesTest {
    private val page = listOf("WhatsApp Messenger", "WhatsApp LLC", "4.3 star", "Install", "Add to wishlist", "About this app")

    // ---- armed window
    @Test fun armedOnlyForThatPackageAndOnly60Seconds() {
        val st = HashMap<String, Long>()
        PlayInstallRules.arm("com.whatsapp", 1_000L, st)
        assertTrue(PlayInstallRules.armedFor("com.whatsapp", 1_000L, st))
        assertTrue(PlayInstallRules.armedFor("com.whatsapp", 61_000L, st))
        assertFalse(PlayInstallRules.armedFor("com.whatsapp", 61_001L, st))
        assertFalse(PlayInstallRules.armedFor("com.instagram.android", 2_000L, st))
        assertFalse(PlayInstallRules.armedFor("", 2_000L, st))
    }

    @Test fun neverArmedMeansNever() {
        assertFalse(PlayInstallRules.armedFor("com.nobody", 5L, HashMap()))
        assertFalse(PlayInstallRules.armedFor("com.whatsapp", 500L, hashMapOf("com.whatsapp" to 1_000L)))   // clock went backwards
    }

    @Test fun disarmClosesTheWindow() {
        val st = HashMap<String, Long>()
        PlayInstallRules.arm("com.whatsapp", 1_000L, st)
        PlayInstallRules.disarm("com.whatsapp", st)
        assertFalse(PlayInstallRules.armedFor("com.whatsapp", 1_500L, st))
    }

    // ---- page title
    @Test fun titleMatchesExactOrHarmlessSuffix() {
        assertTrue(PlayInstallRules.pageMatches(page, "WhatsApp"))
        assertTrue(PlayInstallRules.pageMatches(listOf("Instagram", "Install"), "Instagram"))
        assertTrue(PlayInstallRules.pageMatches(listOf("  instagram  "), "Instagram"))
    }

    @Test fun lookalikeAppsDoNotMatch() {
        assertFalse(PlayInstallRules.pageMatches(listOf("WhatsApp Business", "Install"), "WhatsApp"))
        assertFalse(PlayInstallRules.pageMatches(listOf("YouTube Music", "Install"), "YouTube"))
        assertFalse(PlayInstallRules.pageMatches(listOf("Instagram Lite", "Install"), "Instagram"))
        assertFalse(PlayInstallRules.pageMatches(listOf("Install"), "WhatsApp"))
        assertFalse(PlayInstallRules.pageMatches(page, ""))
    }

    // ---- search result for an unknown app
    @Test fun searchResultMustBeTheSpokenNameExactly() {
        assertTrue(PlayInstallRules.searchResultMatches("Snapseed", "snapseed"))
        assertTrue(PlayInstallRules.searchResultMatches("Snapseed App", "snapseed"))
        assertTrue(PlayInstallRules.searchResultMatches("Google Keep", "google keep"))
        assertFalse(PlayInstallRules.searchResultMatches("Snapseed Pro Editor", "snapseed"))
        assertFalse(PlayInstallRules.searchResultMatches("Snap", "snapseed"))
        assertFalse(PlayInstallRules.searchResultMatches("", "snapseed"))
        assertFalse(PlayInstallRules.searchResultMatches("Snapseed", ""))
    }

    // ---- decide BEFORE the press
    @Test fun exactInstallGivesTap() {
        assertEquals(Decision.TAP, PlayInstallRules.decide(page, afterTap = false))
    }

    @Test fun alreadyInstalledIsNotPressed() {
        assertEquals(Decision.INSTALLED, PlayInstallRules.decide(listOf("WhatsApp Messenger", "Open", "Uninstall"), false))
        assertEquals(Decision.INSTALLED, PlayInstallRules.decide(listOf("WhatsApp Messenger", "Open", "Update"), false))
    }

    @Test fun paidAppIsNeverPressed() {
        assertEquals(Decision.PAID, PlayInstallRules.decide(listOf("Some App", "\u20B9149.00", "Add to wishlist"), false))
        assertEquals(Decision.PAID, PlayInstallRules.decide(listOf("Some App", "$4.99"), false))
        assertEquals(Decision.PAID, PlayInstallRules.decide(listOf("Some App", "Buy"), false))
    }

    @Test fun signInIsNeverPressed() {
        assertEquals(Decision.NEED_LOGIN, PlayInstallRules.decide(listOf("Sign in"), false))
        assertEquals(Decision.NEED_LOGIN, PlayInstallRules.decide(listOf("Add account"), false))
    }

    @Test fun installAnywayAndUpdateNeverGiveTap() {
        assertEquals(Decision.WAIT, PlayInstallRules.decide(listOf("Install anyway", "Cancel"), false))
        assertEquals(Decision.WAIT, PlayInstallRules.decide(listOf("Install anyway", "Install"), false))
        assertEquals(Decision.WAIT, PlayInstallRules.decide(listOf("Some App", "Update"), false))
    }

    @Test fun longDescriptionTextIsNotAButton() {
        val txt = "Tap Install to download this app and enjoy messaging with friends"
        assertEquals(Decision.WAIT, PlayInstallRules.decide(listOf("WhatsApp Messenger", txt), false))
        assertEquals(Decision.WAIT, PlayInstallRules.decide(emptyList(), false))
    }

    @Test fun installLabelMustBeExactNotJustContainingTheWord() {
        assertEquals(Decision.WAIT, PlayInstallRules.decide(listOf("Installs: 5B+"), false))
        assertEquals(Decision.WAIT, PlayInstallRules.decide(listOf("Install on more devices"), false))
        assertEquals(Decision.TAP, PlayInstallRules.decide(listOf(" INSTALL "), false))
    }

    // ---- decide AFTER the press
    @Test fun progressWordsMeanStarted() {
        assertEquals(Decision.STARTED, PlayInstallRules.decide(listOf("WhatsApp Messenger", "Cancel", "Downloading"), true))
        assertEquals(Decision.STARTED, PlayInstallRules.decide(listOf("Pending"), true))
        assertEquals(Decision.STARTED, PlayInstallRules.decide(listOf("Installing\u2026"), true))
        assertEquals(Decision.STARTED, PlayInstallRules.decide(listOf("Cancel"), true))
    }

    @Test fun finishedAtOnceCountsAsStarted() {
        assertEquals(Decision.STARTED, PlayInstallRules.decide(listOf("Open", "Uninstall"), true))
    }

    @Test fun installStillThereAfterTheTapIsNotSuccess() {
        assertEquals(Decision.WAIT, PlayInstallRules.decide(page, true))
        assertEquals(Decision.WAIT, PlayInstallRules.decide(listOf("Install", "Cancel"), true))
    }

    @Test fun warningDialogAfterTheTapIsNotSuccess() {
        assertEquals(Decision.WAIT, PlayInstallRules.decide(listOf("Install anyway", "Cancel"), true))
    }

    // ---- the single gate
    @Test fun mayTapNeedsArmedAndRightPageAndInstall() {
        val st = HashMap<String, Long>()
        assertFalse(PlayInstallRules.mayTap("com.whatsapp", "WhatsApp", page, 1_000L, st))          // not armed
        PlayInstallRules.arm("com.whatsapp", 1_000L, st)
        assertTrue(PlayInstallRules.mayTap("com.whatsapp", "WhatsApp", page, 2_000L, st))
        assertFalse(PlayInstallRules.mayTap("com.whatsapp", "WhatsApp", page, 70_000L, st))         // window over
        assertFalse(PlayInstallRules.mayTap("com.whatsapp", "Instagram", page, 2_000L, st))         // wrong page
        assertFalse(PlayInstallRules.mayTap("com.whatsapp", "WhatsApp", listOf("WhatsApp Messenger", "\u20B9149.00"), 2_000L, st))
        assertFalse(PlayInstallRules.mayTap("com.instagram.android", "WhatsApp", page, 2_000L, st)) // other package not armed
    }
}
