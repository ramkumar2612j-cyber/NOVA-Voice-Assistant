package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyPresenceRulesTest {
    private object V { val SEND = ReplyPresenceRules.Verdict.SEND; val SEEN = ReplyPresenceRules.Verdict.SEEN; val OWNER = ReplyPresenceRules.Verdict.OWNER; val REPLACED = ReplyPresenceRules.Verdict.REPLACED }

    @Test fun ownSenderNeedsAnExplicitName() {
        assertTrue(ReplyPresenceRules.isOwnSender("You", null))
        assertTrue(ReplyPresenceRules.isOwnSender("Ram Kumar", "ram kumar"))
        assertFalse(ReplyPresenceRules.isOwnSender("", "Ram"))
        assertFalse(ReplyPresenceRules.isOwnSender(null, "Ram"))
        assertFalse(ReplyPresenceRules.isOwnSender("Rahul", "Ram"))
    }

    @Test fun ownerSpokeLastOnlyWhenNewestLineIsOwn() {
        val a = listOf(ReplyPresenceRules.Line(false, "hi"), ReplyPresenceRules.Line(true, "haan bolo"))
        val b = listOf(ReplyPresenceRules.Line(true, "haan bolo"), ReplyPresenceRules.Line(false, "kab aaoge"))
        assertTrue(ReplyPresenceRules.ownerSpokeLast(a))
        assertFalse(ReplyPresenceRules.ownerSpokeLast(b))
        assertFalse(ReplyPresenceRules.ownerSpokeLast(emptyList()))
    }

    @Test fun contextLabelsOwnerAndDropsSecrets() {
        val c = ReplyPresenceRules.context(listOf(
            ReplyPresenceRules.Line(false, "kal milte hain?"),
            ReplyPresenceRules.Line(true, "haan 5 baje"),
            ReplyPresenceRules.Line(false, "mera otp 4821 hai")))
        assertEquals(listOf("PERSON: kal milte hain?", "OWNER: haan 5 baje"), c)
        val many = (1..20).map { ReplyPresenceRules.Line(false, "m$it") }
        assertEquals(ReplyPresenceRules.MAX_CONTEXT, ReplyPresenceRules.context(many).size)
    }

    @Test fun promptCarriesTheRecentChat() {
        val p = SmartReplyRules.systemPrompt("Ram", true, false, emptyList(), listOf("OWNER: haan 5 baje"))
        assertTrue(p.contains("RECENT CHAT") && p.contains("OWNER: haan 5 baje"))
        assertFalse(SmartReplyRules.systemPrompt("Ram", true).contains("RECENT CHAT"))
    }

    @Test fun waitSendsWhenNothingHappens() {
        val p = ReplyPresenceRules.Pending(); p.start("k", 7L)
        var slept = 0L
        val v = ReplyPresenceRules.waitVerdict(p, "k", 7L, 2000L, 500L) { slept += it }
        assertEquals(V.SEND, v); assertEquals(2000L, slept)
    }

    @Test fun seenStopsTheWaitEarly() {
        val p = ReplyPresenceRules.Pending(); p.start("k", 7L)
        var n = 0
        val v = ReplyPresenceRules.waitVerdict(p, "k", 7L, 5000L, 500L) { if (++n == 3) p.cancel("k", ReplyPresenceRules.CODE_SEEN) }
        assertEquals(V.SEEN, v); assertTrue(n < 10)
    }

    @Test fun ownerReplyAndNewerMessageStopTheWait() {
        val p = ReplyPresenceRules.Pending(); p.start("k", 1L)
        p.cancel("k", ReplyPresenceRules.CODE_OWNER)
        assertEquals(V.OWNER, p.state("k", 1L))
        p.start("k", 1L); p.start("k", 2L)
        assertEquals(V.REPLACED, p.state("k", 1L))
        assertEquals(V.SEND, p.state("k", 2L))
        p.done("k", 2L)
        assertEquals(V.REPLACED, p.state("k", 2L))
    }

    @Test fun cancelIgnoresChatsWeAreNotAnswering() {
        val p = ReplyPresenceRules.Pending()
        p.cancel("nobody", ReplyPresenceRules.CODE_SEEN)
        p.start("nobody", 5L)
        assertEquals(V.SEND, p.state("nobody", 5L))
    }

    @Test fun takeoverHoldsThenExpires() {
        val t = ReplyPresenceRules.Takeover()
        t.mark("Rahul", 1000L, 60_000L)
        assertTrue(t.active("rahul", 2000L))
        assertFalse(t.active("rahul", 62_000L))
        assertFalse(t.active("Amit", 2000L))
        t.mark("Rahul", 1000L, 10_000L)   // a shorter mark never shortens the hold
        assertTrue(t.active("Rahul", 50_000L))
    }

    @Test fun ownEchoIsRecognisedForTenSeconds() {
        val s = ReplyPresenceRules.Sent()
        s.mark("k", 1000L)
        assertTrue(s.recent("k", 5000L))
        assertFalse(s.recent("k", 20_000L))
        assertFalse(s.recent("other", 2000L))
    }

    @Test fun skipTextsAreFilled() {
        for (v in listOf(V.SEEN, V.OWNER, V.REPLACED)) { assertTrue(ReplyPresenceRules.skipText(v, true).isNotBlank()); assertTrue(ReplyPresenceRules.skipText(v, false).isNotBlank()) }
    }
}
