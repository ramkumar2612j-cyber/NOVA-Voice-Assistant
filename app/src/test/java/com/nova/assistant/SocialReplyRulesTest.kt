package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialReplyRulesTest {
    private val ig = "com.instagram.android"
    private val wa = "com.whatsapp"

    @Test fun wishesAreWishes() {
        for (t in listOf("Happy birthday bhai ❤️", "HBD sir 🎂", "Janamdin mubarak ho", "Congratulations!!"))
            assertEquals(t, SocialReplyRules.Kind.WISH, SocialReplyRules.classify(t))
    }

    @Test fun niceCommentsAreNormal() {
        for (t in listOf("Nice reel 🔥", "Bahut badiya video", "❤️❤️", "Thank you"))
            assertEquals(t, SocialReplyRules.Kind.NORMAL, SocialReplyRules.classify(t))
    }

    @Test fun insultsAndShamingAreInsults() {
        for (t in listOf("Tum fraud ho, sharam karo", "shame on you", "chutiya hai tu", "fake page hai ye", "Happy birthday you idiot"))
            assertEquals(t, SocialReplyRules.Kind.INSULT, SocialReplyRules.classify(t))
    }

    @Test fun questionsAndUnknownStayWithTheOldRules() {
        for (t in listOf("Price kya hai?", "size available hai kya", "order kab aayega", "ok", "kal milte hain", "Birthday kab hai?", "nice but price?", ""))
            assertEquals(t, SocialReplyRules.Kind.OTHER, SocialReplyRules.classify(t))
    }

    @Test fun decisionPerApp() {
        val ins = SocialReplyRules.decide(ig, "shame on you")!!
        assertNull(ins.reply); assertTrue(ins.report)                     // Instagram: no reply, to report
        val wins = SocialReplyRules.decide(wa, "shame on you")!!
        assertNull(wins.reply); assertTrue(!wins.report)                  // WhatsApp: no reply, owner told, nothing to report
        assertEquals(SocialReplyRules.THANKS_WISH, SocialReplyRules.decide(ig, "Happy birthday 🎂")!!.reply)
        assertEquals(SocialReplyRules.THANKS_NORMAL, SocialReplyRules.decide(wa, "nice pic")!!.reply)
        assertNull(SocialReplyRules.decide(ig, "price kya hai?"))
        assertNull(SocialReplyRules.decide("com.telegram.messenger", "happy birthday"))   // other apps unchanged
    }

    @Test fun threeMessagesPerPersonPerHour() {   // limit is 3 per hour (AutoReplyRules.SENDER_PER_HOUR), same as AutoReplyRulesTest
        val g = AutoReplyRules.Gate()
        val t0 = 1_000_000L
        assertNull(g.allow("Anand", t0))
        assertNull(g.allow("Anand", t0 + 11 * 60_000L))
        assertNull(g.allow("Anand", t0 + 22 * 60_000L))
        assertEquals("limit", g.allow("Anand", t0 + 33 * 60_000L))
        assertNull(g.allow("Anand", t0 + 75 * 60_000L))
    }
}
