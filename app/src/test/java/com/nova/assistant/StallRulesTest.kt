package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StallRulesTest {
    @Test fun stallStartsAtThreshold() {
        assertFalse(StallRules.isStall(StallRules.STALL_MS - 1))
        assertTrue(StallRules.isStall(StallRules.STALL_MS))
    }

    @Test fun frozenPhoneIsNotAStall() {
        assertFalse(StallRules.wasFrozen(1100))
        assertTrue(StallRules.wasFrozen(StallRules.CHECK_MS + StallRules.FROZEN_MS))
    }

    @Test fun topFramePrefersNovaCode() {
        val f = listOf("android.os.MessageQueue.nativePollOnce(Native Method)", "com.nova.assistant.NovaService.speak(NovaService.kt:641)")
        assertEquals("com.nova.assistant.NovaService.speak(NovaService.kt:641)", StallRules.topFrame(f))
        assertEquals("a.B.c(B.kt:1)", StallRules.topFrame(listOf("a.B.c(B.kt:1)")))
        assertEquals("unknown", StallRules.topFrame(emptyList()))
    }

    @Test fun shortNameKeepsClassAndMethod() {
        assertEquals("NovaService.speak", StallRules.shortName("com.nova.assistant.NovaService.speak(NovaService.kt:641)"))
    }

    @Test fun lineRoundTripsAndBadLinesAreDropped() {
        val e = StallRules.Entry(1000L, 4200L, "x.Y.z(Y.kt:1)|odd")
        val back = StallRules.decode(StallRules.encode(e))!!
        assertEquals(1000L, back.at)
        assertEquals(4200L, back.ms)
        assertNull(StallRules.decode("garbage"))
        assertNull(StallRules.decode("a|b|c"))
        assertEquals(1, StallRules.decodeAll("junk\n" + StallRules.encode(e)).size)
    }

    @Test fun ringKeepsOnlyTheNewestTen() {
        var l = emptyList<StallRules.Entry>()
        for (i in 1..14) l = StallRules.append(l, StallRules.Entry(i.toLong(), 4000L, "a.B.c(B.kt:$i)"))
        assertEquals(10, l.size)
        assertEquals(5L, l.first().at)
        assertEquals(10, StallRules.decodeAll(StallRules.encodeAll(l)).size)
    }

    @Test fun replyIsHonestWhenEmptyAndCountsWhenNot() {
        assertTrue(StallRules.reply(emptyList(), false).contains("koi atakna record nahi"))
        assertTrue(StallRules.reply(emptyList(), true).contains("No app freeze"))
        val l = listOf(StallRules.Entry(1, 4000, "a.B.c(B.kt:1)"), StallRules.Entry(2, 9000, "a.B.c(B.kt:2)"), StallRules.Entry(3, 5000, "a.D.e(D.kt:3)"))
        val r = StallRules.reply(l, true)
        assertTrue(r.contains("3 freezes"))
        assertTrue(r.contains("B.c"))
        assertTrue(r.contains("9 seconds"))
    }
}
