package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v6.76 (Part 6): verification levels, failure kinds, bounded recovery, sensitive actions and the one-line report. Pure rules, no phone involved. */
class ToolVerifyTest {
    private val L = ToolVerify.Level.values()

    @Test fun everyToolHasAnHonestLevel() {
        for (t in listOf("torch_on", "torch_off", "volume", "brightness_set", "brightness_step")) assertEquals(t, ToolVerify.Level.VERIFIED, ToolVerify.level(t))
        for (t in listOf("alarm", "alarm_any", "timer")) assertEquals(t, ToolVerify.Level.UNVERIFIABLE, ToolVerify.level(t))
        for (t in listOf("sms", "whatsapp", "calendar_event")) assertEquals(t, ToolVerify.Level.HANDOFF, ToolVerify.level(t))
        for (t in listOf("open_app", "call", "settings", "camera", "navigate")) assertEquals(t, ToolVerify.Level.PARTIAL, ToolVerify.level(t))
        assertEquals(ToolVerify.Level.PARTIAL, ToolVerify.level("media", "pause"))
        assertEquals(ToolVerify.Level.PARTIAL, ToolVerify.level("media", "play"))
        assertEquals(ToolVerify.Level.UNVERIFIABLE, ToolVerify.level("media", "next"))
        // an unknown tool is never "verified"
        assertEquals(ToolVerify.Level.UNVERIFIABLE, ToolVerify.level("some_new_tool"))
        assertTrue(ToolVerify.KNOWN_TOOLS.containsAll(listOf("open_app", "call", "sms", "whatsapp", "alarm", "timer", "torch", "volume", "brightness", "navigate", "settings", "media", "calendar_event", "search_web")))
        assertEquals(4, L.size)
    }

    @Test fun callAndMessageToolsAreSensitiveWhenConfirmationsAreOn() {
        assertEquals(ToolVerify.Sensitive.SPOKEN_YES, ToolVerify.sensitive("call"))
        assertEquals(ToolVerify.Sensitive.SPOKEN_YES, ToolVerify.sensitive("whatsapp"))
        assertEquals(ToolVerify.Sensitive.HANDOFF, ToolVerify.sensitive("sms"))
        assertEquals(ToolVerify.Sensitive.HANDOFF, ToolVerify.sensitive("calendar_event"))
        assertEquals(ToolVerify.Sensitive.NONE, ToolVerify.sensitive("torch"))
    }

    @Test fun sensitiveLabelsAreRecognised() {
        for (l in listOf("Send", "Send money", "Delete chat", "Post", "Submit", "Pay now", "Buy", "Transfer", "Order now", "Uninstall", "Enter password", "OTP", "Checkout", "bhejo", "hatao")) {
            assertTrue(l, ToolVerify.labelIsSensitive(l))
        }
        for (l in listOf("Search", "Photos", "Ask anything", "Back", "Camera")) assertFalse(l, ToolVerify.labelIsSensitive(l))
    }

    @Test fun failureKindsAreReadFromTheRealToolTexts() {
        assertEquals(ToolVerify.Fail.NO_ACCESSIBILITY, ToolVerify.classify("Pehle Accessibility permission on karo"))
        assertEquals(ToolVerify.Fail.NO_ACCESSIBILITY, ToolVerify.classify("Turn on the Accessibility permission first"))
        assertEquals(ToolVerify.Fail.NO_PERMISSION, ToolVerify.classify("Brightness needs a permission. Tap ALLOW BRIGHTNESS in the NOVA app"))
        assertEquals(ToolVerify.Fail.NO_PERMISSION, ToolVerify.classify("Contacts ki permission de do, phir dobara bolo"))
        assertEquals(ToolVerify.Fail.BLOCKED, ToolVerify.classify("I do not control this screen (security, payment or settings). Please do it yourself"))
        assertEquals(ToolVerify.Fail.BLOCKED, ToolVerify.classify("X nahi khul paya: phone ne background se app kholne se roka. Other permissions ON karo."))
        assertEquals(ToolVerify.Fail.BLOCKED, ToolVerify.classify("X did not open: the phone blocked opening apps from the background. Turn ON Other permissions."))
        assertEquals(ToolVerify.Fail.NOT_FOUND, ToolVerify.classify("zzz naam ka app nahi mila"))
        assertEquals(ToolVerify.Fail.NOT_FOUND, ToolVerify.classify("I could not find an app called zzz"))
        assertEquals(ToolVerify.Fail.NO_HANDLER, ToolVerify.classify("Alarm set karne wala app nahi mila"))
        assertEquals(ToolVerify.Fail.NO_HANDLER, ToolVerify.classify("Could not open Camera: no app can handle it"))
        assertEquals(ToolVerify.Fail.NOT_CONFIRMED, ToolVerify.classify("Torch badli ya nahi, confirm nahi hua. Kuch phones background mein torch rok dete hain"))
        assertEquals(ToolVerify.Fail.NOT_CONFIRMED, ToolVerify.classify("Android did not change the volume (it is 40 percent)"))
        assertEquals(ToolVerify.Fail.NOT_CONFIRMED, ToolVerify.classify("Android ne volume nahi badla (abhi 40 percent)"))
        assertEquals(ToolVerify.Fail.BAD_INPUT, ToolVerify.classify("Say a volume between 0 and 100"))
        assertEquals(ToolVerify.Fail.BAD_INPUT, ToolVerify.classify("Alarm ka time galat hai"))
        assertEquals(ToolVerify.Fail.OTHER, ToolVerify.classify("something strange"))
    }

    @Test fun retryIsOnceAndOnlyForReadBackTools() {
        val nc = ToolVerify.Fail.NOT_CONFIRMED
        assertEquals(ToolVerify.Step.RETRY_ONCE, ToolVerify.recovery("torch_on", nc, 0, true).step)
        assertEquals(ToolVerify.Step.RETRY_ONCE, ToolVerify.recovery("volume", nc, 0, false).step)
        assertEquals(ToolVerify.Step.RETRY_ONCE, ToolVerify.recovery("brightness_set", nc, 0, true).step)
        // never twice
        assertEquals(ToolVerify.Step.GUIDE, ToolVerify.recovery("torch_on", nc, 1, true).step)
        assertEquals(ToolVerify.Step.GUIDE, ToolVerify.recovery("torch_on", nc, 5, true).step)
        // never for tools whose repeat could do more than once (call, send, alarm, open app)
        for (t in listOf("call", "whatsapp", "sms", "alarm", "timer", "open_app", "media")) {
            assertFalse(t, ToolVerify.recovery(t, nc, 0, true).step == ToolVerify.Step.RETRY_ONCE)
        }
    }

    @Test fun guidesAreHonestAndBounded() {
        val nf = ToolVerify.recovery("open_app", ToolVerify.Fail.NOT_FOUND, 1, true)
        assertEquals(ToolVerify.Step.GUIDE, nf.step); assertTrue(nf.say.contains("Play Store"))
        assertEquals(ToolVerify.Step.STOP, ToolVerify.recovery("call", ToolVerify.Fail.NOT_FOUND, 1, true).step)
        val perm = ToolVerify.recovery("brightness_set", ToolVerify.Fail.NO_PERMISSION, 1, true)
        assertEquals(ToolVerify.Step.GUIDE, perm.step); assertTrue(perm.say.contains("setup check"))
        // a protected screen is never worked around
        assertEquals(ToolVerify.Step.STOP, ToolVerify.recovery("tap", ToolVerify.Fail.BLOCKED, 0, true).step)
        // the guide text really is a sentence ConfigReport answers
        assertTrue(ConfigReport.isQuery("setup check"))
    }

    @Test fun finishNeverFakesSuccess() {
        // an alarm text that does not say "not confirmed" gets the honest note
        val a = ToolVerify.finish("alarm", "", true, "Alarm bhej diya", false)
        assertTrue(a.contains("Verify nahi hua"))
        // texts that already say it are left alone
        val own = "Requested a 5 minutes timer. Android gives no confirmation, please check the clock app"
        assertEquals(own, ToolVerify.finish("timer", "", true, own, true))
        // verified tools are untouched
        assertEquals("Torch is on", ToolVerify.finish("torch_on", "", true, "Torch is on", true))
        // an unknown tool success is never presented as verified
        assertTrue(ToolVerify.finish("some_new_tool", "", true, "Done", true).contains("Not verified"))
        // failure gets the one guide, once
        val f = ToolVerify.finish("open_app", "zzz", false, "zzz naam ka app nahi mila", false)
        assertTrue(f.contains("Play Store"))
        assertEquals(1, Regex("Play Store").findAll(f).count())
        assertEquals("Camera nahi khul paya: koi app ye kaam nahi kar sakta", ToolVerify.finish("camera", "", false, "Camera nahi khul paya: koi app ye kaam nahi kar sakta", false))
    }

    @Test fun reportIsOneSentenceWithVerifyState() {
        val v = ToolVerify.report("Torch on", true, ToolVerify.Level.VERIFIED, "", true)
        assertEquals("Torch on (verified).", v)
        val u = ToolVerify.report("Alarm 7:00 requested", true, ToolVerify.Level.UNVERIFIABLE, "check the Clock app", true)
        assertTrue(u.contains("not verified")); assertTrue(u.contains("left: check the Clock app"))
        val h = ToolVerify.report("WhatsApp message ready", true, ToolVerify.Level.HANDOFF, "", false)
        assertTrue(h.contains("intezaar"))
        val f = ToolVerify.report("Brightness", false, ToolVerify.Level.VERIFIED, "allow brightness permission", true)
        assertTrue(f.contains("failed")); assertTrue(f.contains("not verified"))
        for (s in listOf(v, u, h, f, ToolVerify.report("Done. Really. Done", true, ToolVerify.Level.PARTIAL, "one. two", true))) {
            assertTrue(s, s.endsWith("."))
            assertFalse(s, s.dropLast(1).contains(". "))   // one sentence: no inner full stop
        }
    }
}
