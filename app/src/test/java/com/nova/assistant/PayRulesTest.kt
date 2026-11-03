package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.170 PAYMENT QR + CHECK (written in a sandbox without a Kotlin compiler: NOT RUN yet - run in GitHub Actions). */
class PayRulesTest {
    @Test fun paise() {
        assertEquals(50000L, PayRules.toPaise("500")); assertEquals(49950L, PayRules.toPaise("499.50")); assertEquals(120000L, PayRules.toPaise("1,200"))
        assertNull(PayRules.toPaise("0")); assertNull(PayRules.toPaise("abc")); assertNull(PayRules.toPaise("99999999"))
        assertEquals("500", PayRules.rupeeText(50000)); assertEquals("499.50", PayRules.rupeeText(49950))
    }
    @Test fun link() {
        val l = PayRules.upiLink("sharma@okhdfc", "Sharma Store", 50000, "Order 12")
        assertEquals("upi://pay?pa=sharma@okhdfc&pn=Sharma%20Store&am=500.00&cu=INR&tn=Order%2012", l)
        assertFalse(PayRules.upiLink("a1@bank", "", 0, "").contains("am="))
    }
    @Test fun voice() {
        assertEquals(Logic.Cmd(PayRules.K_PAY_QR, "sharma@okhdfc|50000"), PayRules.parse("nova qr banao sharma@okhdfc 500"))
        assertEquals(Logic.Cmd(PayRules.K_PAY_QR, "|20000"), PayRules.parse("nova 200 ka qr banao"))
        assertEquals(Logic.Cmd(PayRules.K_PAY_QR, "sharma@okhdfc|0"), PayRules.parse("nova qr banao sharma at the rate okhdfc"))
        assertEquals(PayRules.K_PAY_STATUS, PayRules.parse("nova payment aaya kya")!!.kind)
        assertEquals(Logic.Cmd(PayRules.K_PAY_WATCH, "off"), PayRules.parse("nova payment check band karo"))
        assertEquals(PayRules.K_PAY_SCREEN, PayRules.parse("nova payment screen kholo")!!.kind)
        assertNull(PayRules.parse("nova wifi ka qr banao")); assertNull(PayRules.parse("payment kaise karun")); assertNull(PayRules.parse("nova biz upi a@b"))
    }
    @Test fun credits() {
        assertEquals(PayRules.Credit(50000, "Rahul Sharma"), PayRules.parseCredit("Rahul Sharma paid you ₹500.00"))
        assertEquals(50000L, PayRules.parseCredit("Rs.500.00 credited to A/c XX1234 by UPI. Avl Bal Rs 12000.50")!!.paise)
        assertNull(PayRules.parseCredit("You paid ₹500 to Rahul")); assertNull(PayRules.parseCredit("Rs 500 debited from A/c"))
        assertNull(PayRules.parseCredit("Collect request of ₹500 from Rahul")); assertNull(PayRules.parseCredit("OTP 123456 for credit of Rs 500"))
        assertNull(PayRules.parseCredit("You received ₹10 cashback"))
    }
    @Test fun sources() {
        assertEquals("PhonePe", PayRules.sourceOf("com.phonepe.app")!!.name); assertTrue(PayRules.sourceOf("com.google.android.apps.messaging")!!.sms)
        assertNull(PayRules.sourceOf("com.whatsapp"))
    }
    @Test fun matching() {
        val now = 1_000_000L
        val l = listOf(PayRules.Pending("a", 50000, 100, now + 5000, PayRules.WAITING, 0, ""), PayRules.Pending("b", 0, 50, now + 5000, PayRules.WAITING, 0, ""))
        assertEquals(1, PayRules.matchIndex(l, PayRules.Credit(70000, ""), now))      // any-amount QR takes it
        assertEquals(1, PayRules.matchIndex(l, PayRules.Credit(50000, ""), now))      // oldest waiting first
        assertEquals(-1, PayRules.matchIndex(l, PayRules.Credit(50000, ""), now + 9000)) // expired
    }
}
