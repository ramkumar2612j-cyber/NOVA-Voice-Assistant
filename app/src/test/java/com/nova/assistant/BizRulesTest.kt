package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class BizRulesTest {
    private val full = BizRules.Profile("Cotton kurta", 800, 30, "sharma@okhdfc", "Sharma Store", "3 din", "Noida", "boutique", 8, true)

    @Test fun parseSetAndStatus() {
        assertEquals("price|799", BizRules.parse("nova biz price 799")?.arg)
        assertEquals(BizRules.K_BIZ_SET, BizRules.parse("Nova business product Cotton kurta")?.kind)
        assertEquals(BizRules.K_BIZ_STATUS, BizRules.parse("nova biz status")?.kind)
        assertEquals("on", BizRules.parse("nova biz leads on")?.arg)
        assertEquals(BizRules.K_BIZ_LEADS_NOW, BizRules.parse("nova biz leads now")?.kind)
        assertEquals(BizRules.K_BIZ_KEY, BizRules.parse("nova biz placeskey AIzaSyA1234567890abcdefghijk")?.kind)
        assertNull(BizRules.parse("mera business kaisa chal raha hai"))
        assertNull(BizRules.parse("nova biz price"))
        assertNull(BizRules.parse("nova biz colour red"))
    }

    @Test fun applyValidates() {
        val p = BizRules.Profile()
        assertEquals(799, BizRules.apply(p, "price|Rs 799").first.price)
        assertEquals(0, BizRules.apply(p, "price|abc").first.price)
        assertEquals(30, BizRules.apply(p, "advance|30 percent").first.advancePct)
        assertEquals(0, BizRules.apply(p, "advance|150").first.advancePct)
        assertEquals("ab@okhdfc", BizRules.apply(p, "upi|ab@okhdfc").first.upi)
        assertEquals("", BizRules.apply(p, "upi|not an upi").first.upi)
        assertEquals(10, BizRules.apply(p, "leads|50").first.perDay)
        assertEquals(5, BizRules.apply(p, "leads|2").first.perDay)
        assertEquals("", BizRules.apply(p, "delivery|jaldi").first.deliveryText)
        assertEquals("3 din", BizRules.apply(p, "delivery|3 din").first.deliveryText)
    }

    @Test fun amountsAndPayment() {
        assertEquals(1600, BizRules.total(full, 2))
        assertEquals(480, BizRules.advanceAmount(full, 2))
        val m = BizRules.paymentMessage(full, 2, "o1abc", false)!!
        assertTrue(m.contains("Rs 480")); assertTrue(m.contains("sharma@okhdfc")); assertTrue(m.contains("upi://pay?pa=sharma@okhdfc"))
        assertTrue(m.contains("am=480")); assertTrue(m.contains("3 din"))
        assertNull(BizRules.paymentMessage(full.copy(advancePct = 0), 2, "o1", false))
        assertNull(BizRules.paymentMessage(full.copy(upi = ""), 2, "o1", false))
        assertNull(BizRules.paymentMessage(full.copy(price = 0), 2, "o1", false))
        assertNull(BizRules.paymentMessage(full.copy(upi = "bad id"), 2, "o1", false))
    }

    @Test fun upiLinkEncodes() {
        val l = BizRules.upiLink(full, 480, "Order o1")
        assertTrue(l.contains("pn=Sharma%20Store")); assertTrue(l.contains("tn=Order%20o1")); assertTrue(l.endsWith("tn=Order%20o1"))
    }

    @Test fun deliveryTime() {
        val day = 24L * 60 * 60_000
        assertEquals(3 * day, BizRules.deliveryMs("3 din"))
        assertEquals(2 * 3600_000L, BizRules.deliveryMs("2 ghante"))
        assertEquals(day, BizRules.deliveryMs("kal"))
        assertEquals(7 * day, BizRules.deliveryMs("1 week"))
        assertNull(BizRules.deliveryMs("jaldi"))
        assertNull(BizRules.deliveryMs("0 din"))
        assertEquals(1000L + 3 * day, BizRules.deliveryDue(full, 1000L))
        assertNull(BizRules.deliveryDue(full.copy(deliveryText = ""), 1000L))
    }

    @Test fun missingAndStatus() {
        assertEquals(listOf("product", "city", "category"), BizRules.missingForLeads(BizRules.Profile()))
        assertTrue(BizRules.missingForLeads(full).isEmpty())
        assertTrue(BizRules.missingForPayment(full.copy(upi = "")).contains("upi"))
        assertTrue(BizRules.statusLine(full).contains("leads 8/din ON"))
        assertTrue(BizRules.statusLine(BizRules.Profile()).contains("Baaki"))
    }

    @Test fun pitchIsHonest() {
        val h = BizRules.pitch(full, "Ravi\nBoutique")
        assertFalse(h.contains("\n")); assertTrue(h.contains("STOP")); assertTrue(h.contains("Rs 800"))
        assertEquals("boutique in Noida", BizRules.leadQuery(full))
    }
}
