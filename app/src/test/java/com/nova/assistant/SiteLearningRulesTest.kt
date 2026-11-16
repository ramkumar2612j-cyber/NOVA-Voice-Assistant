package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class SiteLearningRulesTest {
    private val K = PlaybookRules.Kind.MAIL

    @Test fun siteLabelIsHostOnly() {
        assertEquals("amazon.in", SiteLearningRules.siteLabel("www.Amazon.in"))
        assertEquals("", SiteLearningRules.siteLabel("amazon.in/orders?id=5"))
        assertEquals("", SiteLearningRules.siteLabel("a-very-long-host-name-for-a-shop.example.com"))
        assertEquals("", SiteLearningRules.siteLabel("localhost"))
        assertEquals("", SiteLearningRules.siteLabel("user@mail.com"))
    }

    @Test fun bankAndPaymentHostsAreNeverStored() {
        assertEquals("", SiteLearningRules.siteKey("sbi.bank.in"))
        assertEquals("", SiteLearningRules.siteKey("paytm.com"))
        val notes = SiteLearningRules.record(emptyList(), listOf("paytm.com", "example.com"), K, SiteLearningRules.Outcome.SUCCEEDED, null, 0, 100L)
        assertTrue(notes.none { it.key.contains("paytm") })
        assertTrue(notes.any { it.key == "site:example.com" })
    }

    @Test fun recordCountsOkAndBlockersPerKindAndSite() {
        var n = SiteLearningRules.record(emptyList(), listOf("example.com"), K, SiteLearningRules.Outcome.SUCCEEDED, null, 1, 100L)
        n = SiteLearningRules.record(n, listOf("example.com"), K, SiteLearningRules.Outcome.BLOCKED, SiteLearningRules.Blocker.PASSWORD, 0, 200L)
        n = SiteLearningRules.record(n, listOf("example.com"), K, SiteLearningRules.Outcome.BLOCKED, SiteLearningRules.Blocker.PASSWORD, 2, 300L)
        val site = n.first { it.key == "site:example.com" }
        assertEquals(1, site.ok)
        assertEquals(2, site.blocked[SiteLearningRules.Blocker.PASSWORD])
        assertEquals(3, site.switches)
        assertEquals(300L, site.lastAtMs)
        assertEquals(2, n.size)
        assertTrue(n.any { it.key == "kind:mail" })
    }

    @Test fun failureWithoutKnownBlockerIsOther() {
        val n = SiteLearningRules.record(emptyList(), emptyList(), K, SiteLearningRules.Outcome.FAILED, null, 0, 100L)
        assertEquals(1, n.single().blocked[SiteLearningRules.Blocker.OTHER])
    }

    @Test fun atMostTwoSitesPerJobAndBoundedEntries() {
        val n = SiteLearningRules.record(emptyList(), listOf("a.com", "b.com", "c.com"), K, SiteLearningRules.Outcome.SUCCEEDED, null, 0, 100L)
        assertEquals(3, n.size) // kind + 2 sites
        var all = emptyList<SiteLearningRules.Note>()
        for (i in 1..40) all = SiteLearningRules.record(all, listOf("site$i.com"), K, SiteLearningRules.Outcome.SUCCEEDED, null, 0, i.toLong())
        assertTrue(all.size <= SiteLearningRules.MAX_ENTRIES)
    }

    @Test fun countersDecayAndStayCapped() {
        var n = emptyList<SiteLearningRules.Note>()
        for (i in 1..60) n = SiteLearningRules.record(n, listOf("example.com"), K, SiteLearningRules.Outcome.SUCCEEDED, null, 0, i.toLong())
        val site = n.first { it.key == "site:example.com" }
        assertTrue(site.ok <= SiteLearningRules.DECAY_AT)
        assertTrue(site.ok >= 1)
    }

    @Test fun hintWarnsEarlyAboutRepeatedWall() {
        var n = SiteLearningRules.record(emptyList(), listOf("example.com"), K, SiteLearningRules.Outcome.BLOCKED, SiteLearningRules.Blocker.CAPTCHA, 0, 100L)
        n = SiteLearningRules.record(n, listOf("example.com"), K, SiteLearningRules.Outcome.BLOCKED, SiteLearningRules.Blocker.CAPTCHA, 0, 200L)
        val h = SiteLearningRules.hint(n, listOf("example.com"), K, en = true)
        assertTrue(h.contains("example.com"))
        assertTrue(h.contains("CAPTCHA"))
        assertTrue(h.contains("never overrides"))
        assertTrue(h.length <= SiteLearningRules.MAX_HINT_CHARS)
    }

    @Test fun hintIsQuietWhenSiteMostlyWorks() {
        var n = emptyList<SiteLearningRules.Note>()
        for (i in 1..5) n = SiteLearningRules.record(n, listOf("example.com"), PlaybookRules.Kind.SEARCH, SiteLearningRules.Outcome.SUCCEEDED, null, 0, i.toLong())
        n = SiteLearningRules.record(n, listOf("example.com"), PlaybookRules.Kind.SEARCH, SiteLearningRules.Outcome.BLOCKED, SiteLearningRules.Blocker.PASSWORD, 0, 10L)
        val h = SiteLearningRules.hint(n, listOf("example.com"), PlaybookRules.Kind.SEARCH, en = true)
        assertFalse(h.contains("wall stopped"))
    }

    @Test fun hintEmptyWithNoHistoryAndForOtherSites() {
        assertEquals("", SiteLearningRules.hint(emptyList(), listOf("example.com"), K))
        val n = SiteLearningRules.record(emptyList(), listOf("example.com"), K, SiteLearningRules.Outcome.BLOCKED, SiteLearningRules.Blocker.PASSWORD, 0, 100L)
        assertFalse(SiteLearningRules.hint(n, listOf("other.com"), PlaybookRules.Kind.MUSIC, en = true).contains("example.com"))
    }

    @Test fun missingAppAndSwitchHintsComeFromTheKind() {
        var n = SiteLearningRules.record(emptyList(), emptyList(), PlaybookRules.Kind.MUSIC, SiteLearningRules.Outcome.FAILED, SiteLearningRules.Blocker.APP_MISSING, 2, 100L)
        n = SiteLearningRules.record(n, emptyList(), PlaybookRules.Kind.MUSIC, SiteLearningRules.Outcome.FAILED, SiteLearningRules.Blocker.APP_MISSING, 1, 200L)
        val h = SiteLearningRules.hint(n, emptyList(), PlaybookRules.Kind.MUSIC, en = true)
        assertTrue(h.contains("missing app"))
        assertTrue(h.contains("never install automatically"))
        assertTrue(h.contains("route switches"))
    }

    @Test fun blockerFromLoginState() {
        assertEquals(SiteLearningRules.Blocker.PASSWORD, SiteLearningRules.blockerFor(LoginRules.State.PASSWORD))
        assertEquals(SiteLearningRules.Blocker.CODE, SiteLearningRules.blockerFor(LoginRules.State.CODE))
        assertEquals(SiteLearningRules.Blocker.CAPTCHA, SiteLearningRules.blockerFor(LoginRules.State.CAPTCHA))
        assertNull(SiteLearningRules.blockerFor(LoginRules.State.CHOOSER))
        assertNull(SiteLearningRules.blockerFor(LoginRules.State.NONE))
    }

    @Test fun storedKeysMustStayClean() {
        assertEquals("site:example.com", SiteLearningRules.cleanKey("site:example.com"))
        assertEquals("kind:mail", SiteLearningRules.cleanKey("kind:mail"))
        assertEquals("", SiteLearningRules.cleanKey("site:example.com/path?q=1"))
        assertEquals("", SiteLearningRules.cleanKey("kind:secret_task_text"))
        assertEquals("", SiteLearningRules.cleanKey("random"))
    }

    @Test fun summaryHasNoRawText() {
        assertEquals("Abhi koi site ki seekh save nahi hai.", SiteLearningRules.summary(emptyList()))
        val n = SiteLearningRules.record(emptyList(), listOf("example.com"), K, SiteLearningRules.Outcome.BLOCKED, SiteLearningRules.Blocker.CODE, 0, 100L)
        val s = SiteLearningRules.summary(n, en = true)
        assertTrue(s.contains("site:example.com: ok 0, code 1"))
        assertTrue(s.length <= 700)
    }

    @Test fun loginStopMessagesBecomeLoginGap() {
        assertEquals(CapabilityLearningRules.Gap.LOGIN, CapabilityLearningRules.classify(LoginRules.ownerMessage(LoginRules.State.PASSWORD, false)))
        assertEquals(CapabilityLearningRules.Gap.LOGIN, CapabilityLearningRules.classify(LoginRules.ownerMessage(LoginRules.State.CODE, true)))
        assertEquals(CapabilityLearningRules.Gap.LOGIN, CapabilityLearningRules.classify(LoginRules.ownerMessage(LoginRules.State.CAPTCHA, false)))
        assertEquals(CapabilityLearningRules.Gap.STUCK, CapabilityLearningRules.classify("Screen par kuch badal nahi raha, atak gaya"))
        val g = CapabilityLearningRules.guidance(CapabilityLearningRules.Gap.LOGIN, true)
        assertTrue(g.contains("Never type"))
    }
}
