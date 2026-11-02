package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.158 AUTOPILOT voice grammar + order-status rules (task H + G4). Pure Kotlin, no Android.
 */
class AutopilotVoiceRulesTest {

    // ---------------------------------------------------------------- grammar

    @Test fun onOffStatusGrammar() {
        assertEquals(AutopilotVoiceRules.K_AP_ON, AutopilotVoiceRules.parse("nova autopilot on karo")?.kind)
        assertEquals(AutopilotVoiceRules.K_AP_OFF, AutopilotVoiceRules.parse("nova autopilot band karo")?.kind)
        assertEquals(AutopilotVoiceRules.K_AP_OFF, AutopilotVoiceRules.parse("autopilot off")?.kind)
        assertEquals(AutopilotVoiceRules.K_AP_STATUS, AutopilotVoiceRules.parse("nova autopilot status")?.kind)
        assertEquals(AutopilotVoiceRules.K_AP_STATUS, AutopilotVoiceRules.parse("autopilot kaisa hai")?.kind)
        // negated never matches
        assertNull(AutopilotVoiceRules.parse("autopilot on mat karo"))
        assertNull(AutopilotVoiceRules.parse("nova autopilot nahi chalana"))
    }

    @Test fun todayOrdersNowStoryGrammar() {
        assertEquals(AutopilotVoiceRules.K_AP_TODAY, AutopilotVoiceRules.parse("nova aaj kya post hua")?.kind)
        assertEquals(AutopilotVoiceRules.K_AP_ORDERS, AutopilotVoiceRules.parse("nova orders dikhao")?.kind)
        assertEquals(AutopilotVoiceRules.K_AP_ORDERS, AutopilotVoiceRules.parse("nova order list batao")?.kind)
        assertEquals(AutopilotVoiceRules.K_AP_NOW, AutopilotVoiceRules.parse("nova autopilot isko abhi post karo")?.kind)
        assertEquals(AutopilotVoiceRules.K_AP_STORY, AutopilotVoiceRules.parse("nova story lagao")?.kind)
        assertEquals(AutopilotVoiceRules.K_AP_STORY, AutopilotVoiceRules.parse("nova autopilot story lagao")?.kind)
    }

    @Test fun offerCarriesTheText() {
        val c = AutopilotVoiceRules.parse("nova offer 20% off code SK20")
        assertNotNull(c)
        assertEquals(AutopilotVoiceRules.K_AP_OFFER, c!!.kind)
        assertTrue(c.arg.contains("20% off"))
        assertTrue(c.arg.contains("sk20"))
        assertNull(AutopilotVoiceRules.parse("nova offer"))   // too short = no capture (normal chat safe)
    }

    @Test fun setupAndOrderStatusGrammar() {
        assertEquals(AutopilotVoiceRules.K_AP_SETUP, AutopilotVoiceRules.parse("nova autopilot setup karo")?.kind)
        val c = AutopilotVoiceRules.parse("nova order Rahul dispatched hai 15 tak")
        assertNotNull(c)
        assertEquals(AutopilotVoiceRules.K_ORDER_STATUS, c!!.kind)
        assertTrue(c.arg.startsWith("rahul"))
    }

    @Test fun normalChatIsNeverCaptured() {
        assertNull(AutopilotVoiceRules.parse("aaj mausam kaisa hai"))
        assertNull(AutopilotVoiceRules.parse("instagram kholo"))
        assertNull(AutopilotVoiceRules.parse("order karo khana"))
        assertNull(AutopilotVoiceRules.parse("nova music bajao"))
        assertNull(AutopilotVoiceRules.parse(""))
    }

    // ---------------------------------------------------------------- replies

    @Test fun honestReplies() {
        assertTrue(AutopilotVoiceRules.replyOn(false, "", false).contains("band"))
        assertTrue(AutopilotVoiceRules.replyOn(true, "sab theek", false).contains("sab theek"))
        assertTrue(AutopilotVoiceRules.replyToday(emptyList(), false).contains("kuch post nahi"))
        val rt = AutopilotVoiceRules.replyToday(listOf(Pair("reel", "done"), Pair("photo", "done")), false)
        assertTrue(rt.contains("2 post") && rt.contains("reel"))
        assertTrue(AutopilotVoiceRules.replyOrders(emptyList(), false).contains("koi order nahi"))
        assertTrue(AutopilotVoiceRules.replyOrders(listOf(Pair("Rahul 2x kurta", true)), false).contains("confirm"))
        assertFalse(AutopilotVoiceRules.replyNow(false, false).contains("scan"))
        assertTrue(AutopilotVoiceRules.replyStory(123L, false).contains("story"))
    }

    // ---------------------------------------------------------------- order status (task G4)

    @Test fun orderStatusQuestionDetection() {
        assertTrue(AutopilotVoiceRules.isOrderStatusQuestion("mera order kab aayega"))
        assertTrue(AutopilotVoiceRules.isOrderStatusQuestion("order status batao"))
        assertTrue(AutopilotVoiceRules.isOrderStatusQuestion("parcel kahan pahuncha"))
        assertFalse(AutopilotVoiceRules.isOrderStatusQuestion("naya kurta dikhao"))
        assertFalse(AutopilotVoiceRules.isOrderStatusQuestion("order karna hai red kurta"))
    }

    @Test fun orderStatusReplyOnlyFromStoredNote() {
        // stored note -> the customer hears exactly that
        val with = AutopilotVoiceRules.orderStatusReply("dispatch ho gaya, 15 tak pahunch jayega", "red kurta", false)
        assertTrue(with.contains("dispatch"))
        assertTrue(with.contains("red kurta"))
        // nothing stored -> honest line, NO invented date
        val without = AutopilotVoiceRules.orderStatusReply("", "red kurta", false)
        assertFalse(without.contains("din"))
        assertFalse(without.contains("tarikh"))
        assertTrue(without.contains("owner") || without.contains("Owner"))
    }

    @Test fun ownerOrderStatusParsing() {
        val p = AutopilotVoiceRules.parseOwnerOrderStatus("Rahul dispatched 15 tarikh tak")
        assertNotNull(p)
        assertEquals("Rahul", p!!.first)
        assertTrue(p.second.contains("dispatched"))
        assertNull(AutopilotVoiceRules.parseOwnerOrderStatus("x"))
    }
}
