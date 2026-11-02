package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class ChatHonestyTest {
    @Test fun realBadReplyIsReplaced() {
        val bad = "Website ko professional banane ke liye 3D animations aur dark theme add kar raha hoon."
        assertTrue(ChatHonesty.claimsUnbackedAction(bad))
        assertEquals(ChatHonesty.honestLine(false), ChatHonesty.guard(bad, false))
    }

    @Test fun englishClaimIsReplacedWithEnglishLine() {
        val bad = "I am adding a dark theme to your website now."
        assertEquals(ChatHonesty.honestLine(true), ChatHonesty.guard(bad, true))
    }

    @Test fun normalAnswerIsUntouched() {
        val ok = "Website banane ke liye pehle domain aur hosting chahiye. Phir design chuno."
        assertFalse(ChatHonesty.claimsUnbackedAction(ok))
        assertEquals(ok, ChatHonesty.guard(ok, false))
        val ok2 = "Aaj ka mausam saaf rahega."
        assertEquals(ok2, ChatHonesty.guard(ok2, false))
    }

    @Test fun toolRanLeavesReplyUntouched() {
        val bad = "Website ko 3D animations ke saath add kar raha hoon."
        assertEquals(bad, ChatHonesty.guard(bad, false, toolRan = true))
    }
}
