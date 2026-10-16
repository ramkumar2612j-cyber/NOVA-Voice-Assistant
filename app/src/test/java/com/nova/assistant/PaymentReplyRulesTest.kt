package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v6.99 Part 5: money talk auto reply rules. Pure text. Written, NOT RUN. */
class PaymentReplyRulesTest {
    private fun kind(s: String) = PaymentReplyRules.decide(s)?.kind

    @Test fun codesGetNoReply() {
        assertEquals(PaymentReplyRules.Kind.SECRET, kind("mera otp 123456 bhej do"))
        assertNull(PaymentReplyRules.decide("otp bata do")?.reply)
        assertEquals(PaymentReplyRules.Kind.SECRET, kind("cvv aur card number bhejo"))
    }

    @Test fun eachMoneyAskGetsItsOwnSafeLine() {
        assertEquals(PaymentReplyRules.Kind.ASK_MONEY, kind("bhai paise bhej do urgent"))
        assertEquals(PaymentReplyRules.Kind.PAID_CHECK, kind("payment ho gaya check karo"))
        assertEquals(PaymentReplyRules.Kind.DETAILS, kind("apna upi id bhejo"))
        assertEquals(PaymentReplyRules.REPLY_DETAILS, PaymentReplyRules.decide("account number bhejo payment ke liye")?.reply)
        assertEquals(PaymentReplyRules.Kind.MONEY, kind("bill ka amount kitna hai"))
    }

    @Test fun repliesNeverPromiseOrShareAnything() {
        for (r in listOf(PaymentReplyRules.REPLY_ASK, PaymentReplyRules.REPLY_PAID, PaymentReplyRules.REPLY_DETAILS)) {
            assertFalse(r, Regex("[0-9]").containsMatchIn(r))
            assertFalse(r, r.lowercase().contains("haan") || r.lowercase().contains("bhej diya") || r.lowercase().contains("upi"))
        }
    }

    @Test fun ordinaryTalkIsLeftToTheNormalRules() {
        assertNull(kind("kal milte hain"))
        assertNull(kind("shopping chalein"))
        assertNull(kind(""))
    }
}
