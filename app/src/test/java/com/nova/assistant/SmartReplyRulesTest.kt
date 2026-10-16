package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartReplyRulesTest {
    @Test fun plainChatMessagesMayGoToTheAi() {
        for (t in listOf("kal milte hain?", "bhai kya haal hai", "Are you free this weekend", "Happy birthday bro"))
            assertTrue(t, SmartReplyRules.allowedForAi(t))
    }

    @Test fun moneyOtpLinkAndLongNumbersNeverGoToTheAi() {
        for (t in listOf(
            "mera OTP 123456 hai", "send me the password", "UPI se 500 rupees bhej do", "pay Rs. 200", "₹500 transfer karo",
            "check https://example.com", "visit www.shop.in now", "my site abc.com", "call 9876543210", "loan chahiye", "aadhaar number bhejo", "KYC update karo"
        )) assertFalse(t, SmartReplyRules.allowedForAi(t))
    }

    @Test fun blankAndTooLongAreNotAllowed() {
        assertFalse(SmartReplyRules.allowedForAi(""))
        assertFalse(SmartReplyRules.allowedForAi("   "))
        assertFalse(SmartReplyRules.allowedForAi("a".repeat(SmartReplyRules.MAX_IN + 1)))
        assertTrue(SmartReplyRules.allowedForAi("a".repeat(SmartReplyRules.MAX_IN)))
    }

    @Test fun userPromptIsOneLineAndCapped() {
        val u = SmartReplyRules.userPrompt("Ra\nhul", "hello\r\nthere " + "x".repeat(500))
        assertFalse(u.contains('\n')); assertFalse(u.contains('\r'))
        assertTrue(u.startsWith("Message from Ra hul: hello there"))
        assertTrue(u.length <= "Message from ".length + 40 + 2 + SmartReplyRules.MAX_IN)
    }

    @Test fun systemPromptsForbidPromisesAndLinks() {
        assertTrue(SmartReplyRules.systemPrompt(true).contains("Never promise money"))
        assertTrue(SmartReplyRules.systemPrompt(false).contains("link"))
    }

    @Test fun sanitizeCleansQuotesAndNewlines() {
        assertEquals("Haan bhai, shaam ko baat karte hain.", SmartReplyRules.sanitize("\"Haan bhai, shaam ko\nbaat karte hain.\""))
    }

    @Test fun sanitizeRejectsUnusableAnswers() {
        for (r in listOf(null, "", "   ", "As an AI I cannot do that", "I can't help", "sorry, I do not know", "see https://x.y", "www.abc.xyz",
            "send 123456 now", "apna OTP bhejo", "bank me paise daalo", "x"))
            assertNull(r.toString(), SmartReplyRules.sanitize(r))
    }

    @Test fun sanitizeCutsLongAnswersAtASentenceEnd() {
        val long = "Haan bilkul, main kal subah aapse baat karunga. " + "Aur phir hum sab milkar kaam dekhenge aur sab theek karenge ".repeat(5)
        val s = SmartReplyRules.sanitize(long)
        assertNotNull(s)
        assertTrue(s!!.length <= SmartReplyRules.MAX_OUT)
        assertTrue(s.endsWith("."))
    }

    @Test fun chooseUsesFixedWhenAiIsUnusable() {
        assertEquals("fixed", SmartReplyRules.choose(null, "fixed"))
        assertEquals("fixed", SmartReplyRules.choose("As an AI model", "fixed"))
        assertEquals("Theek hai bhai", SmartReplyRules.choose("Theek hai bhai", "fixed"))
    }

    @Test fun pickSaysWhichOneWonAndIsNeverEmpty() {
        val ai = SmartReplyRules.pick("Theek hai bhai", "fixed")
        assertTrue(ai.ai); assertEquals("Theek hai bhai", ai.text)
        val fx = SmartReplyRules.pick(null, "fixed")
        assertFalse(fx.ai); assertEquals("fixed", fx.text)
        val blank = SmartReplyRules.pick("", "  ")
        assertFalse(blank.ai); assertEquals(AutoReplyRules.DEFAULT_TEXT, blank.text)
    }

    @Test fun useAiNeedsSwitchNoSocialReplyAndASafeMessage() {
        assertTrue(SmartReplyRules.useAi(true, null, "kal milte hain?"))
        assertFalse("switch OFF", SmartReplyRules.useAi(false, null, "kal milte hain?"))
        assertFalse("social thank-you stays fixed", SmartReplyRules.useAi(true, "Thank you 😊", "Nice reel"))
        assertFalse("unsafe text", SmartReplyRules.useAi(true, null, "mera OTP 123456"))
    }

    @Test fun historyNoteIsAiOrFixed() {
        assertEquals("AI", SmartReplyRules.note(true))
        assertEquals("fixed", SmartReplyRules.note(false))
    }

    @Test fun timeoutIsEightSecondsAndRateLimitsAreUnchanged() {
        assertEquals(8_000L, SmartReplyRules.TIMEOUT_MS)
        assertEquals(2, AutoReplyRules.SENDER_PER_HOUR)
        assertEquals(600_000L, AutoReplyRules.SENDER_COOLDOWN_MS)
        assertEquals(80, AutoReplyRules.DAY_CAP)
    }
}
