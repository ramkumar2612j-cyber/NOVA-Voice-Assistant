package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v6.98 lock guard: which notification is an important alert, what is said, and the quiet rule. Pure rules only. */
class LockGuardTest {
    private val ring = listOf("Answer", "Decline")

    private fun c(pkg: String, cat: String?, title: String, text: String, acts: List<String> = emptyList(), group: Boolean = false, silent: Boolean = true) =
        LockGuard.classify(pkg, cat, title, text, acts, group, silent)

    @Test fun familyNames() {
        assertEquals("Papa", LockGuard.familyOf("Papa"))
        assertEquals("Papa", LockGuard.familyOf("Papa Jio"))
        assertEquals("Papa", LockGuard.familyOf("पापा"))
        assertEquals("Mummy", LockGuard.familyOf("Mummy ❤️"))
        assertEquals("Mummy", LockGuard.familyOf("Mom"))
        assertNull(LockGuard.familyOf("Rahul"))
        assertNull(LockGuard.familyOf("Mama Ji Sweet Shop Delhi"))   // too many words: a shop, not the family contact
        assertNull(LockGuard.familyOf(""))
    }

    @Test fun silentPhoneAnyCallIsAnAlert() {
        val a = c("com.google.android.dialer", "call", "Rahul", "Incoming call", ring)
        assertNotNull(a); assertEquals(LockGuard.Kind.CALL_RINGING, a!!.kind); assertNull(a.family)
        val w = c("com.whatsapp", "call", "Rahul", "Incoming voice call", ring)
        assertEquals("WhatsApp", w!!.app)
        val i = c("com.instagram.android", "call", "riya", "Incoming video call", ring)
        assertTrue(i!!.video)
        assertNotNull(c("org.telegram.messenger", "call", "Amit", "Incoming call", ring))
    }

    @Test fun ringingPhoneNormalCallIsNotAlertedButFamilyIs() {
        assertNull(c("com.google.android.dialer", "call", "Rahul", "Incoming call", ring, silent = false))
        val p = c("com.google.android.dialer", "call", "Papa", "Incoming call", ring, silent = false)
        assertEquals("Papa", p!!.family)
    }

    @Test fun answeredCallIsNotAlerted() {
        assertNull(c("com.google.android.dialer", "call", "Rahul", "Ongoing call", listOf("Hang up")))
        assertNull(c("com.whatsapp", "call", "Rahul", "Ongoing voice call", listOf("Hang up")))
    }

    @Test fun dialerWithNameInText() {
        val a = c("com.android.incallui", "call", "Incoming call", "Mummy", ring)
        assertEquals("Mummy", a!!.family)
    }

    @Test fun missedCall() {
        val a = c("com.google.android.dialer", "missed_call", "Papa", "Missed call")
        assertEquals(LockGuard.Kind.CALL_MISSED, a!!.kind); assertEquals("Papa", a.family)
        val w = c("com.whatsapp", "msg", "Rahul", "Missed voice call")
        assertEquals(LockGuard.Kind.CALL_MISSED, w!!.kind)
        // a long chat line that only mentions a missed call is a message, not a missed call
        assertNull(c("com.whatsapp", "msg", "Rahul", "yaar sorry I missed your call, was in a meeting, will call back soon ok"))
    }

    @Test fun normalMessagesAreIgnored() {
        assertNull(c("com.whatsapp", "msg", "Rahul", "bhai kal milte hain"))
        assertNull(c("com.whatsapp", "msg", "Rahul", "call me when free"))          // says "call" but is a message
        assertNull(c("com.google.android.apps.messaging", "msg", "BANK-ALERT", "OTP 123456"))
    }

    @Test fun familyMessageIsAnAlertWithoutText() {
        val a = c("com.whatsapp", "msg", "Papa", "kahan ho beta, jaldi aao", silent = false)
        assertEquals(LockGuard.Kind.MESSAGE, a!!.kind)
        val line = LockGuard.text(a, false, false)
        assertFalse(line.contains("jaldi"))
        assertEquals("Papa ka WhatsApp par message aaya hai.", line.replace("  ", " "))
    }

    @Test fun familyGroupIsNotFamily() {
        assertNull(c("com.whatsapp", "msg", "Papa", "hello", group = true))
        assertNull(c("com.whatsapp", "msg", "Family Papa Mummy", "hello"))        // 3 tokens but group title: no
    }

    @Test fun speechTexts() {
        val a = c("com.google.android.dialer", "call", "Papa", "Incoming call", ring)!!
        assertEquals("Papa ki call aa rahi hai. Phone silent hai.", LockGuard.text(a, true, false))
        val m = c("com.google.android.dialer", "missed_call", "Mummy", "Missed call")!!
        assertEquals("Mummy ki call aayi thi. Phone silent tha.", LockGuard.text(m, true, false))
        val o = c("com.whatsapp", "call", "Rahul", "Incoming voice call", ring)!!
        assertEquals("Rahul ki WhatsApp par call aa rahi hai. Phone silent hai.", LockGuard.text(o, true, false))
        assertEquals("Rahul is calling you on WhatsApp (call). The phone is silent.", LockGuard.text(o, true, true))
    }

    @Test fun gateAndMissedAfterRing() {
        val g = LockGuard.Gate()
        val a = c("com.google.android.dialer", "call", "Papa", "Incoming call", ring)!!
        assertTrue(g.allow(a, 1000)); g.record(a, 1000)
        assertFalse(g.allow(a, 20_000))                               // the same ringing call posted again
        assertTrue(g.allow(a, 1000 + LockGuard.DEDUPE_MS + 1))
        val m = c("com.google.android.dialer", "missed_call", "Papa", "Missed call")!!
        assertTrue(g.lastRing(m) > 0)
        assertFalse(LockGuard.shouldAlert(m, true, true, 30_000, g.lastRing(m)))      // already told while it rang
        assertTrue(LockGuard.shouldAlert(m, true, true, 10 * 60_000, g.lastRing(m)))  // much later: tell
        val other = c("com.google.android.dialer", "missed_call", "Rahul", "Missed call")!!
        assertTrue(LockGuard.shouldAlert(other, true, true, 30_000, g.lastRing(other)))
    }

    @Test fun onlyWhenLockedAndOn() {
        val a = c("com.google.android.dialer", "call", "Rahul", "Incoming call", ring)!!
        assertFalse(LockGuard.shouldAlert(a, true, false, 1, 0))
        assertFalse(LockGuard.shouldAlert(a, false, true, 1, 0))
        assertTrue(LockGuard.shouldAlert(a, true, true, 1, 0))
    }

    @Test fun quietRule() {
        assertTrue(LockGuard.quietNow(true, true, false))
        assertFalse(LockGuard.quietNow(true, false, false))
        assertFalse(LockGuard.quietNow(false, true, false))
        assertFalse(LockGuard.quietNow(true, true, true))             // driving mode stays hands-free
    }

    @Test fun silentAndVolume() {
        assertTrue(LockGuard.isSilent(false, 5)); assertTrue(LockGuard.isSilent(true, 0)); assertFalse(LockGuard.isSilent(true, 3))
        assertEquals(9, LockGuard.targetAlarmVolume(2, 15)); assertEquals(12, LockGuard.targetAlarmVolume(12, 15)); assertEquals(3, LockGuard.targetAlarmVolume(1, 5))
        assertTrue(LockGuard.isStopKey(24)); assertTrue(LockGuard.isStopKey(25)); assertFalse(LockGuard.isStopKey(26))
    }

    @Test fun adaptiveSilenceForLongOrUnfinishedSentences() {
        val base = 1800
        assertEquals(base, CaptureRules.adaptiveSilence("", base))
        assertEquals(base, CaptureRules.adaptiveSilence("torch on karo", base))
        assertEquals(base + CaptureRules.OPEN_END_EXTRA_MS, CaptureRules.adaptiveSilence("papa ko message bhejo ki", base))
        assertEquals(base + CaptureRules.OPEN_END_EXTRA_MS, CaptureRules.adaptiveSilence("whatsapp kholo aur", base))
        val long = "mere liye kal subah saat baje ka alarm lagao aur phir mummy ko message bhejo"
        assertEquals(base + CaptureRules.LONG_EXTRA_MS, CaptureRules.adaptiveSilence(long, base))
        assertTrue(CaptureRules.adaptiveSilence(long + " ki", base) <= CaptureRules.MAX_SILENCE_MS)
    }
}
