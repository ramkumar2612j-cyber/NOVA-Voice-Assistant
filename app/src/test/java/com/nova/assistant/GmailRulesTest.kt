package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class GmailRulesTest {
    @Test fun sendToSavedName() {
        val c = GmailRules.parse("mail bhejo mom ko subject hello message kal aa jana") as GmailRules.Cmd.Send
        assertEquals("mom", c.to)
        assertEquals("hello", c.subject)
        assertEquals("kal aa jana", c.body)
    }
    @Test fun sendToRealAddress() {
        val c = GmailRules.parse("send mail to rahul@gmail.com subject invoice message please check") as GmailRules.Cmd.Send
        assertEquals("rahul@gmail.com", c.to)
        assertEquals("invoice", c.subject)
        assertEquals("please check", c.body)
    }
    @Test fun saveAddress() {
        val c = GmailRules.parse("mom ka email abc@gmail.com save karo") as GmailRules.Cmd.AliasAdd
        assertEquals("mom", c.name)
        assertEquals("abc@gmail.com", c.email)
    }
    @Test fun watchMonthly() {
        val c = GmailRules.parse("jio bill mail har mahine watch karo") as GmailRules.Cmd.WatchAdd
        assertEquals("jio bill", c.name)
        assertEquals(GmailRules.MONTHLY, c.period)
    }
    @Test fun watchWeeklyNatural() {
        val c = GmailRules.parse("nova gst notice ka mail har hafte aaye to batana") as GmailRules.Cmd.WatchAdd
        assertEquals("gst notice", c.name)
        assertEquals(GmailRules.WEEKLY, c.period)
    }
    @Test fun listAndRemove() {
        assertTrue(GmailRules.parse("mail watch list dikhao") is GmailRules.Cmd.WatchList)
        val r = GmailRules.parse("jio bill ka mail watch hatao") as GmailRules.Cmd.WatchRemove
        assertEquals("jio bill", r.name)
    }
    @Test fun checkAndConnect() {
        assertTrue(GmailRules.parse("koi important mail aaya") is GmailRules.Cmd.Check)
        assertTrue(GmailRules.parse("gmail connect karo") is GmailRules.Cmd.Connect)
    }
    @Test fun ordinarySentencesAreUntouched() {
        assertNull(GmailRules.parse("battery kitni hai"))
        assertNull(GmailRules.parse("weather batao"))
        assertNull(GmailRules.parse("mail"))
    }
    @Test fun importanceAndSpam() {
        assertTrue(GmailRules.isImportant("Jio", "Your bill is due"))
        assertFalse(GmailRules.isImportant("Shop", "Big SALE 50% off today"))
        assertFalse(GmailRules.isImportant("Friend", "hello"))
    }
    @Test fun watchMatching() {
        assertTrue(GmailRules.matchesWatch("jio bill", "Jio Fiber", "Your bill is ready"))
        assertFalse(GmailRules.matchesWatch("jio bill", "Amazon", "Your order"))
    }
    @Test fun numbersAreMasked() {
        assertEquals("code ... ok", GmailRules.maskNumbers("code 123456 ok"))
    }
}
