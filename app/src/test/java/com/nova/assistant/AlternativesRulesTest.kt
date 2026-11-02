package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class AlternativesRulesTest {
    private val gap = AlternativesRules.Gap.APP_MISSING

    private fun labels(kind: PlaybookRules.Kind) = AlternativesRules.routes(kind).map { it.label }

    @Test fun firstRouteIsListOrderWhenNothingIsKnown() {
        val n = AlternativesRules.next(PlaybookRules.Kind.VIDEO, emptyList(), emptyList(), gap)
        assertEquals("youtube", n!!.label)
        assertFalse(n.site)
    }

    @Test fun videoOrderIsAppThenSiteThenGoogle() {
        assertEquals(listOf("youtube", "youtube.com", "google.com"), labels(PlaybookRules.Kind.VIDEO))
        assertEquals(listOf("gmail", "mail.google.com"), labels(PlaybookRules.Kind.MAIL))
        assertEquals(listOf("whatsapp", "whatsapp business", "messages"), labels(PlaybookRules.Kind.MESSAGE))
        assertEquals("chrome", labels(PlaybookRules.Kind.SEARCH).first())
        assertEquals("google", labels(PlaybookRules.Kind.SEARCH).last())
    }

    @Test fun alreadyTriedRoutesAreSkippedAnySpelling() {
        val n = AlternativesRules.next(PlaybookRules.Kind.VIDEO, listOf(" YouTube "), emptyList(), gap)
        assertEquals("youtube.com", n!!.label)
        assertTrue(n.site)
        val m = AlternativesRules.next(PlaybookRules.Kind.VIDEO, listOf("youtube", "youtube.com"), emptyList(), gap)
        assertEquals("google.com", m!!.label)
        assertNull(AlternativesRules.next(PlaybookRules.Kind.VIDEO, listOf("youtube", "youtube.com", "google.com"), emptyList(), gap))
    }

    @Test fun playbookScoreOrdersGoodFirstAndOftenFailedLast() {
        val k = PlaybookRules.Kind.VIDEO
        var e = PlaybookRules.record(emptyList(), k, listOf("youtube.com"), true, 1L)
        e = PlaybookRules.record(e, k, listOf("youtube.com"), true, 2L)
        e = PlaybookRules.record(e, k, listOf("youtube"), false, 3L)
        e = PlaybookRules.record(e, k, listOf("youtube"), false, 4L)
        // youtube.com has a good record, google.com is unknown (0.5), youtube failed twice
        assertEquals("youtube.com", AlternativesRules.next(k, emptyList(), e, gap)!!.label)
        assertEquals("google.com", AlternativesRules.next(k, listOf("youtube.com"), e, gap)!!.label)
        assertEquals("youtube", AlternativesRules.next(k, listOf("youtube.com", "google.com"), e, gap)!!.label)
    }

    @Test fun neverMoreThanFourSwitchesPerJob() {
        assertEquals(4, AlternativesRules.MAX_SWITCHES)
        assertNotNull(AlternativesRules.next(PlaybookRules.Kind.PHOTO_AI, emptyList(), emptyList(), gap, 3))
        assertNull(AlternativesRules.next(PlaybookRules.Kind.PHOTO_AI, emptyList(), emptyList(), gap, 4))
        assertNull(AlternativesRules.next(PlaybookRules.Kind.PHOTO_AI, emptyList(), emptyList(), gap, 9))
    }

    @Test fun genericHasNoRoutesAndNeverSwitches() {
        assertFalse(AlternativesRules.hasRoutes(PlaybookRules.Kind.GENERIC))
        assertNull(AlternativesRules.next(PlaybookRules.Kind.GENERIC, emptyList(), emptyList(), gap))
    }

    @Test fun deniedAndProtectedLabelsAreNeverSuggested() {
        listOf("paytm", "phonepe", "google pay", "bank", "wallet", "upi", "settings", "play store", "nova", "pay", "order", "password", "otp", "checkout")
            .forEach { assertTrue(it, AlternativesRules.isDenied(it)) }
        listOf("youtube", "gmail", "whatsapp", "chrome", "youtube.com", "mail.google.com").forEach { assertFalse(it, AlternativesRules.isDenied(it)) }
        for (kind in PlaybookRules.Kind.values()) {
            for (c in AlternativesRules.routes(kind)) assertFalse(c.label, AlternativesRules.isDenied(c.label))
        }
    }

    @Test fun everyCandidateLabelSurvivesThePlaybookSanitiser() {
        for (kind in PlaybookRules.Kind.values()) {
            for (c in AlternativesRules.routes(kind)) assertEquals(c.label, PlaybookRules.cleanApp(c.label))
        }
    }

    @Test fun knownSiteOnlyForFixedHosts() {
        assertTrue(AlternativesRules.isKnownSite("youtube.com"))
        assertTrue(AlternativesRules.isKnownSite("mail.google.com"))
        assertFalse(AlternativesRules.isKnownSite("youtube"))
        assertFalse(AlternativesRules.isKnownSite("evil-site.com"))
        assertFalse(AlternativesRules.isKnownSite("https://youtube.com/x"))
    }

    @Test fun tapFailureTriggerIsThreeInARow() {
        assertFalse(AlternativesRules.tapGapReached(2))
        assertTrue(AlternativesRules.tapGapReached(3))
    }

    @Test fun deterministicForTheSameInput() {
        val a = AlternativesRules.next(PlaybookRules.Kind.MUSIC, listOf("gaana"), emptyList(), AlternativesRules.Gap.STUCK, 1)
        val b = AlternativesRules.next(PlaybookRules.Kind.MUSIC, listOf("gaana"), emptyList(), AlternativesRules.Gap.TAP_FAILED, 1)
        assertEquals(a, b)
        assertEquals("spotify", a!!.label)
    }

    @Test fun hintLinesAreShortAndHonest() {
        val c = AlternativesRules.Candidate("youtube.com", true)
        val h = AlternativesRules.hintLine("youtube", c, gap, 1)
        assertTrue(h.startsWith("ROUTE SWITCH: youtube did not work; try youtube.com"))
        assertTrue(h.length <= AlternativesRules.MAX_LINE_CHARS)
        assertTrue(AlternativesRules.noRouteLine("x").contains("Do NOT pretend"))
        assertEquals("", AlternativesRules.progressSuffix(0))
        assertEquals("; route switches 2", AlternativesRules.progressSuffix(2))
        assertEquals("; route switches 4", AlternativesRules.progressSuffix(99))
    }
}
