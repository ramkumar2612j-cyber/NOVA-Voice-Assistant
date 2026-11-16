package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v7.12: the repo's OWN auto-reply gate (3 per person per hour, 10 min cooldown). SocialReplyRulesTest.twoMessagesPerPersonPerHour tests another line's number (2) and is kept as it was. */
class AutoReplyGateRepoTest {
    @Test fun threePerPersonPerHourWithCooldown() {
        val g = AutoReplyRules.Gate()
        val t0 = 1_000_000L
        val min = 60_000L
        assertNull(g.allow("Anand", t0))
        assertEquals("cooldown", g.allow("Anand", t0 + 5 * min))
        assertNull(g.allow("Anand", t0 + 11 * min))
        assertNull(g.allow("Anand", t0 + 22 * min))
        assertEquals("limit", g.allow("Anand", t0 + 33 * min))
        assertNull(g.allow("Anand", t0 + 75 * min))
    }

    @Test fun otherPersonIsNotBlocked() {
        val g = AutoReplyRules.Gate()
        assertNull(g.allow("Anand", 1_000_000L))
        assertNull(g.allow("Meera", 1_000_000L + 1_000L))
    }
}
