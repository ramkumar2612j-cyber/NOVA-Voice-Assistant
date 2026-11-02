package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.22 Part 8: ADDED next to the old tests (which are kept). These pin the behaviour the repo really has. */
class Part8RulesTest {
    // ---- AutoReplyRules.Gate: repo rule = 3 replies per sender per hour, 10 min cooldown, 80 per day
    @Test fun gateCooldownThenThreePerHourThenFreeAgain() {
        val g = AutoReplyRules.Gate(); val t0 = 1_000_000L; val m = 60_000L
        assertNull(g.allow("Anand", t0))
        assertEquals("cooldown", g.allow("Anand", t0 + 5 * m))
        assertNull(g.allow("Anand", t0 + 11 * m))
        assertNull(g.allow("Anand", t0 + 22 * m))
        assertEquals("limit", g.allow("Anand", t0 + 33 * m))
        assertNull(g.allow("Anand", t0 + 75 * m))      // the two oldest are over an hour old by now
    }

    @Test fun gateIsPerSenderAndCaseInsensitive() {
        val g = AutoReplyRules.Gate(); val t0 = 5_000_000L
        assertNull(g.allow("Anand", t0))
        assertEquals("cooldown", g.allow("ANAND", t0 + 1000))
        assertNull(g.allow("Meena", t0 + 1000))
    }

    @Test fun gateResetClearsEverything() {
        val g = AutoReplyRules.Gate(); val t0 = 9_000_000L
        assertNull(g.allow("Anand", t0)); g.reset()
        assertNull(g.allow("Anand", t0 + 1000))
    }

    // ---- GeminiSpeed (needs the real org.json: it passes in Gradle CI and in tools/local_compile_check.sh with JSON_JAR)
    @Test fun fastThinkingOnlyForFlash25() {
        assertTrue(GeminiSpeed.supports("gemini-2.5-flash"))
        assertFalse(GeminiSpeed.supports("gemini-2.5-pro"))
        assertFalse(GeminiSpeed.supports("gemini-1.5-flash"))
        val j = "{\"contents\":[]}"
        assertEquals(j, GeminiSpeed.withFastThinking("gemini-2.5-pro", j))
        assertEquals("not json", GeminiSpeed.withFastThinking("gemini-2.5-flash", "not json"))
    }

    @Test fun fastThinkingKeepsAnExistingThinkingConfig() {
        val j = "{\"generationConfig\":{\"thinkingConfig\":{\"thinkingBudget\":128}}}"
        assertEquals(j, GeminiSpeed.withFastThinking("gemini-2.5-flash", j))
    }

    // ---- voice: take a screenshot (Android's own action), never mixed up with send / delete / analyse
    @Test fun screenshotVoiceTakesTheScreenshotAction() {
        for (s in listOf("screenshot lo", "take screenshot", "screenshot le lo", "screen shot lo", "nova screenshot lo")) {
            val c = Logic.classify(s)
            assertNotNull(s, c); assertEquals(s, "global", c?.kind); assertEquals(s, "screenshot", c?.arg)
        }
        assertEquals("screenshot", Understand.classify("screenshot lo")?.arg)
    }

    @Test fun screenshotWordsAboutOtherThingsAreNotTheAction() {
        for (s in listOf("screenshot delete karo", "screenshot bhejo", "screenshot analyze karo", "screenshot mat lo", "last screenshot dikhao"))
            assertFalse(s, Logic.classify(s)?.arg == "screenshot")
    }

    // ---- the repo's own wording for ringer / rotation (the old PhoneVoiceRulesTest expects the other line's words and is kept)
    @Test fun repoRingerRotationWording() {
        assertEquals("auto_off", Logic.classify("rotation off karo")?.arg)
        assertEquals("auto_on", Logic.classify("auto rotate on karo")?.arg)
        assertEquals("silent", Logic.classify("ringer silent karo")?.arg)
        assertEquals("volume", Understand.classify("awaaz band karo")?.kind)
        assertEquals("volume", Logic.classify("volume mute")?.kind)
        assertEquals("quiet_on", Logic.classify("silent replies on")?.kind)
    }
}
