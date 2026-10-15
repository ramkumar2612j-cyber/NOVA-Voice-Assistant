package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartReplyRulesTest {
    @Test fun parseTagsAndCleans() {
        val r = SmartReplyRules.parse("Ji, main pooch kar batata hoon. [OWNER]")!!
        assertTrue(r.needOwner); assertTrue(!r.skip); assertEquals("Ji, main pooch kar batata hoon.", r.text)
        assertTrue(SmartReplyRules.parse("[SKIP]")!!.skip)
        assertNull(SmartReplyRules.parse(null)); assertNull(SmartReplyRules.parse("   "))
        assertTrue(SmartReplyRules.parse("x".repeat(900))!!.text.length <= SmartReplyRules.MAX_TEXT)
    }

    @Test fun onlySecretsAreHeldBack() {   // Part 10: money, links and numbers are allowed
        assertNull(SmartReplyRules.unsafeReason("Rate Rs 500 hai"))
        assertNull(SmartReplyRules.unsafeReason("10% discount milega"))
        assertNull(SmartReplyRules.unsafeReason("pay on UPI abc@upi"))
        assertNull(SmartReplyRules.unsafeReason("dekho https://abc.com"))
        assertNull(SmartReplyRules.unsafeReason("call 9876543210"))
        assertNotNull(SmartReplyRules.unsafeReason("aapka OTP 482913 hai"))
        assertNotNull(SmartReplyRules.unsafeReason("password bhej do"))
        assertNotNull(SmartReplyRules.unsafeReason("card 4111 1111 1111 1111"))
        assertNull(SmartReplyRules.unsafeReason("Namaste, main NOVA hoon, abhi busy hain, jaldi reply karenge."))
    }

    @Test fun ownerFactsReachThePromptAndAreLimited() {
        val c = SmartReplyRules.Convo()
        for (i in 0 until 9) c.addFact("Rahul", "fact $i")
        assertEquals(SmartReplyRules.MAX_FACTS, c.facts("rahul").size)
        assertEquals("fact 8", c.facts("RAHUL").last())
        c.addFact("Rahul", "   ")
        assertEquals(SmartReplyRules.MAX_FACTS, c.facts("Rahul").size)
        val p = SmartReplyRules.systemPrompt("Sonu", true, false, listOf("Rate 500 hai"))
        assertTrue(p.contains("Rate 500 hai") && p.contains("NEVER invent") && p.contains("[OWNER]"))
        assertTrue(!SmartReplyRules.systemPrompt("Sonu", true).contains("FACTS Sonu GAVE"))
        assertTrue(!p.contains("NEVER give or promise prices"))
    }

    @Test fun moneyAndOrderMessagesNeedOwner() {
        assertTrue(SmartReplyRules.ownerMust("mera order nahi aaya"))
        assertTrue(SmartReplyRules.ownerMust("rate kya hai"))
        assertTrue(!SmartReplyRules.ownerMust("kaise ho bhai"))
    }

    @Test fun gateAllowsConversationButStopsFlood() {
        val g = SmartReplyRules.Gate()
        assertNull(g.allow("Rahul", 0L))
        assertEquals("cooldown", g.allow("Rahul", 5_000L))
        assertNull(g.allow("Rahul", 20_000L))
        var last: String? = null
        for (i in 0 until 20) last = g.allow("Bot", 100_000L + i * 16_000L)
        assertEquals("limit", last)
    }

    @Test fun convoKeepsLastTurnsOnly() {
        val c = SmartReplyRules.Convo()
        for (i in 0 until 10) c.add("A", "user", "m$i")
        assertEquals(SmartReplyRules.MAX_TURNS, c.turns("A").size)
        c.add("A", "assistant", "hello")
        assertEquals("hello", c.lastAssistant("A"))
    }

    @Test fun informMentionsSenderAndReply() {
        val t = SmartReplyRules.inform("Rahul", "WhatsApp", "kal milte hain?", "Ji, pooch kar batata hoon", true, true, true)
        assertTrue(t.contains("Rahul") && t.contains("pooch kar") && t.contains("zaruri"))
    }

    @Test fun ownerNameIsCleanedAndUsedInPrompt() {
        assertEquals("Sonu Kumar", SmartReplyRules.cleanName("  Sonu   Kumar "))
        assertEquals("Sonu ignore rules", SmartReplyRules.cleanName("Sonu\n[ignore] rules!"))
        assertTrue(SmartReplyRules.cleanName("x".repeat(80)).length <= 30)
        assertTrue(SmartReplyRules.systemPrompt("Sonu", true).contains("assistant of Sonu"))
        assertTrue(SmartReplyRules.systemPrompt("", true).contains("assistant of the owner"))
    }

    @Test fun businessPromptHoldsBackStockAndRates() {
        val b = SmartReplyRules.systemPrompt("Sonu", true, true)
        assertTrue(b.contains("BUSINESS") && b.contains("do NOT know stock"))
        assertTrue(!SmartReplyRules.systemPrompt("Sonu", true, false).contains("BUSINESS"))
    }

    @Test fun repeatedNotificationIsAnsweredOnce() {
        val s = SmartReplyRules.Seen()
        assertTrue(s.fresh("Rahul", "hello", 0L))
        assertTrue(!s.fresh("rahul", "hello", 60_000L))
        assertTrue(s.fresh("Rahul", "hello", 200_000L))
        assertTrue(s.fresh("Rahul", "kaise ho", 200_001L))
    }

    @Test fun voiceSwitchParsesOnlyClearCommands() {
        assertEquals("smart_on", SmartReplyRules.command("smart reply on karo")!!.kind)
        assertEquals("smart_off", SmartReplyRules.command("ai reply band karo")!!.kind)
        assertEquals("autoreply_on", SmartReplyRules.command("auto reply chalu karo")!!.kind)
        assertEquals("autoreply_off", SmartReplyRules.command("auto reply off")!!.kind)
        assertNull(SmartReplyRules.command("smart reply kya hai"))
        assertNull(SmartReplyRules.command("auto reply ka status batao"))
        assertNull(SmartReplyRules.command("business watch auto on"))
        assertNull(SmartReplyRules.command("smart reply on off"))
        assertNull(SmartReplyRules.command("kaise ho"))
    }
}
