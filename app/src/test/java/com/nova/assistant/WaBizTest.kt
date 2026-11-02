package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaBizTest {
    private val t0 = 1_000_000L

    private fun fresh(auto: Boolean = false, hours: Int = 10) {
        WaBiz.catalogue = mapOf("rate" to "Kurti Rs 499 se shuru hai.", "size" to "S, M, L, XL available.")
        WaBiz.start(t0, hours, auto)
    }

    @Test fun classify() {
        assertEquals(WaBiz.Cat.IGNORE, WaBiz.classify("hi"))
        assertEquals(WaBiz.Cat.IGNORE, WaBiz.classify("Thank you"))
        assertEquals(WaBiz.Cat.FAQ, WaBiz.classify("is kurti ka rate kya hai"))
        assertEquals(WaBiz.Cat.URGENT, WaBiz.classify("mera order abhi tak nahi aaya"))
        assertEquals(WaBiz.Cat.URGENT, WaBiz.classify("rate kam karo last price batao"))   // money beats FAQ
        assertEquals(WaBiz.Cat.SENSITIVE, WaBiz.classify("my otp is 482913"))
    }

    @Test fun windowIsCappedAndExpires() {
        fresh(hours = 99)
        assertEquals(t0 + 12 * 3_600_000L, WaBiz.untilMs)
        assertTrue(WaBiz.active(t0 + 1))
        assertFalse(WaBiz.active(t0 + 12 * 3_600_000L))
        WaBiz.stop()
        assertFalse(WaBiz.active(t0 + 1))
    }

    @Test fun offByDefaultAndApprovalModeNeverSends() {
        WaBiz.stop()
        assertNull(WaBiz.onMessage("Ravi", "rate kya hai", t0, true))
        fresh(auto = false)
        val d = WaBiz.onMessage("Ravi", "is kurti ka rate kya hai", t0 + 10, true)!!
        assertFalse(d.sendNow)
        assertEquals("Kurti Rs 499 se shuru hai.", d.item.draft)
    }

    @Test fun autoSendsOnlyCatalogueFaq() {
        fresh(auto = true)
        val d = WaBiz.onMessage("Ravi", "size kaun kaun se hain", t0 + 10, true, seed = 7)!!
        assertTrue(d.sendNow)
        assertTrue(d.delayS in WaBiz.MIN_DELAY_S..WaBiz.MAX_DELAY_S)
        // FAQ the owner never wrote an answer for -> goes to owner, not guessed
        val g = WaBiz.onMessage("Sita", "delivery kitne din me hogi", t0 + 20, true)!!
        assertFalse(g.sendNow)
        assertTrue(g.tellOwner)
        assertEquals(WaBiz.Cat.URGENT, g.item.cat)
    }

    @Test fun moneyAndComplaintNeverAutoReply() {
        fresh(auto = true)
        for (m in listOf("payment kar diya screenshot bheja", "kapda kharab nikla refund chahiye", "discount milega kya")) {
            val d = WaBiz.onMessage("X$m", m, t0 + 5, true)!!
            assertFalse(m, d.sendNow)
            assertTrue(m, d.tellOwner)
        }
    }

    @Test fun otpIsNeverStoredOrAnswered() {
        fresh(auto = true)
        assertNull(WaBiz.onMessage("Bank", "your otp is 123456 do not share", t0 + 5, true))
        assertTrue(WaBiz.snapshot().all { it.text.isEmpty() })
    }

    @Test fun rateLimits() {
        fresh(auto = true)
        assertTrue(WaBiz.onMessage("Ravi", "rate kya hai", t0 + 1_000, true)!!.sendNow)
        assertFalse(WaBiz.onMessage("Ravi", "size kya hai", t0 + 2_000, true)!!.sendNow)          // cooldown
        assertTrue(WaBiz.onMessage("Ravi", "size available hai kya", t0 + 70_000, true)!!.sendNow)
        assertTrue(WaBiz.onMessage("Ravi", "rate kya hai", t0 + 140_000, true)!!.sendNow)
        assertFalse(WaBiz.onMessage("Ravi", "rate kya hai", t0 + 300_000, true)!!.sendNow)        // 3 per sender cap
    }

    @Test fun cannotReplyMeansNoSend() {
        fresh(auto = true)
        assertFalse(WaBiz.onMessage("Ravi", "rate kya hai", t0 + 5, false)!!.sendNow)
    }

    @Test fun summaryAndClear() {
        fresh(auto = true)
        WaBiz.onMessage("Ravi", "rate kya hai", t0 + 5, true)
        WaBiz.onMessage("Sita", "mera order nahi aaya", t0 + 6, true)
        val s = WaBiz.summary()
        assertTrue(s, s.contains("2 message"))
        assertTrue(s, s.contains("1 zaruri"))
        WaBiz.stop(); WaBiz.clear()
        assertTrue(WaBiz.snapshot().isEmpty())
    }

    @Test fun voiceCommands() {
        assertEquals("biz_on", WaBiz.command("business watch 10 ghante")!!.kind)
        assertEquals("10", WaBiz.command("business watch 10 ghante")!!.arg)
        assertEquals("12", WaBiz.command("business watch 40 ghante")!!.arg)
        assertEquals("biz_off", WaBiz.command("business watch band karo")!!.kind)
        assertEquals("biz_auto_on", WaBiz.command("business watch auto on")!!.kind)
        assertEquals("biz_summary", WaBiz.command("business watch summary")!!.kind)
        assertNull(WaBiz.command("battery kitni hai"))
        assertNotNull(WaBiz.command("whatsapp watch"))
    }

    @Test fun duplicateAndSummaryNotificationsIgnored() {
        fresh(auto = true)
        assertNull(WaBiz.onMessage("Ravi", "2 new messages", t0 + 5, true))
        assertNull(WaBiz.onMessage("Ravi", "Checking for new messages", t0 + 5, true))
        assertNotNull(WaBiz.onMessage("Ravi", "mera order nahi aaya", t0 + 10, true))
        assertNull(WaBiz.onMessage("Ravi", "mera order nahi aaya", t0 + 20, true))               // same notification re-posted
        assertNotNull(WaBiz.onMessage("Ravi", "mera order nahi aaya", t0 + 200_000, true))        // genuinely sent again later
    }
}
