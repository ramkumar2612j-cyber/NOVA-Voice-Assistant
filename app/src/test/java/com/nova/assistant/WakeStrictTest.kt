package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeStrictTest {
    @Test fun singleGlitchNeverWakes() {
        var hits = Logic.nextWakeHits(0, true)
        assertFalse(Logic.wakeConfirmed(hits, false))
        hits = Logic.nextWakeHits(hits, false)       // next chunk has no wake word: counter restarts
        assertEquals(0, hits)
        assertFalse(Logic.wakeConfirmed(hits, false))
    }

    @Test fun repeatedPartialWakes() {
        var hits = 0
        repeat(3) { hits = Logic.nextWakeHits(hits, true) }
        assertFalse(Logic.wakeConfirmed(hits, false))     // v6.55: 3 partial hits are not enough any more
        hits = Logic.nextWakeHits(hits, true)
        assertTrue(Logic.wakeConfirmed(hits, false))
    }

    @Test fun wakeWordMustOpenTheSentence() {
        assertTrue(Logic.heardWake("""{"text": "jarvis torch on"}""", "jarvis"))
        assertTrue(Logic.heardWake("""{"text": "hey jarvis"}""", "jarvis"))
        assertTrue(Logic.heardWake("""{"partial": "[unk] jarvis"}""", "jarvis"))
        assertFalse(Logic.heardWake("""{"text": "[unk] [unk] jarvis [unk]"}""", "jarvis"))   // "jarvis" in the middle of other talk (TV) never wakes
        assertFalse(Logic.heardWake("""{"text": "the show is jarvis"}""", "jarvis"))
    }

    @Test fun finalResultWakesAtOnce() {
        assertTrue(Logic.wakeConfirmed(1, true))
        assertFalse(Logic.wakeConfirmed(0, true))     // a final result without the wake word is never a wake
    }

    @Test fun nothingHeardIsFalseWake() {
        assertTrue(Logic.isFalseWake(false, "", false, false))
        assertTrue(Logic.isFalseWake(false, "", true, true))
    }

    @Test fun noiseOnlyIsFalseWakeOffline() {
        assertTrue(Logic.isFalseWake(true, "the", false, false))
        assertTrue(Logic.isFalseWake(true, "", false, false))
        assertFalse(Logic.isFalseWake(true, "torch on", false, false))
    }

    @Test fun cloudHearingAndAnswersAreNotDropped() {
        assertFalse(Logic.isFalseWake(true, "", false, true))     // Groq may still understand the audio
        assertFalse(Logic.isFalseWake(true, "the", true, false))  // a yes/no answer goes to the safe "unclear = no" path
    }

    @Test fun unkJarvisNeedsFinalResult() {
        assertFalse(Logic.heardWake("""{"partial": "[unk] jarvis"}""", "jarvis", false))
        assertTrue(Logic.heardWake("""{"text": "[unk] jarvis"}""", "jarvis", true))
        assertTrue(Logic.heardWake("""{"partial": "jarvis"}""", "jarvis", false))
        assertTrue(Logic.heardWake("""{"partial": "hey jarvis"}""", "jarvis", false))
        assertFalse(Logic.heardWake("""{"text": "the show is jarvis"}""", "jarvis", true))
    }

    @Test fun gemini3FlashGetsMinimalThinkingLevel() {
        assertTrue(GeminiSpeed.supportsLevel("gemini-3.5-flash"))
        assertFalse(GeminiSpeed.supportsLevel("gemini-3.5-flash-lite"))
        assertFalse(GeminiSpeed.supportsLevel("gemini-3.1-pro"))
        assertFalse(GeminiSpeed.supportsLevel("gemini-2.5-flash"))
        val out = GeminiSpeed.withFastThinking("gemini-3.5-flash", """{"contents":[]}""")
        assertTrue(out.contains("thinkingLevel") && out.contains("minimal") && !out.contains("thinkingBudget"))
        assertEquals("""{"contents":[]}""", GeminiSpeed.withFastThinking("gemini-3.5-flash-lite", """{"contents":[]}"""))
    }
}
