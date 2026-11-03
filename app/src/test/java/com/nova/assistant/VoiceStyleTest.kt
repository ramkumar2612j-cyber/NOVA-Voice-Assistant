package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStyleTest {
    private fun tone(n: Int, rate: Int): ShortArray =
        ShortArray(n) { (8000 * Math.sin(2 * Math.PI * 180.0 * it / rate)).toInt().toShort() }

    @Test fun normalizeFallsBackToUltron() {
        assertEquals("ultron", VoiceStyle.normalize(null))
        assertEquals("ultron", VoiceStyle.normalize("xyz"))
        assertEquals("jarvis", VoiceStyle.normalize("jarvis"))
    }

    @Test fun nextToggles() {
        assertEquals("jarvis", VoiceStyle.next("ultron"))
        assertEquals("ultron", VoiceStyle.next("jarvis"))
    }

    @Test fun jarvisKeepsNormalPitchUltronGoesDeeper() {
        assertTrue(VoiceStyle.pitch("jarvis") > VoiceStyle.pitch("ultron"))
    }

    @Test fun jarvisProcessMakesLongerValidAudioAndEmptyStaysEmpty() {
        val src = tone(8000, 16000)
        val out = JarvisFx.process(src, 16000, 0.75f)
        assertTrue(out.size > src.size)
        assertTrue(out.any { it.toInt() != 0 })
        assertFalse(out.contentEquals(src))
        assertEquals(0, JarvisFx.process(ShortArray(0), 16000, 0.75f).size)
    }

    @Test fun styleProcessPicksTheRightEffect() {
        val src = tone(4000, 16000)
        assertTrue(VoiceStyle.process("jarvis", src, 16000, 0.75f).contentEquals(JarvisFx.process(src, 16000, 0.75f)))
        assertTrue(VoiceStyle.process("ultron", src, 16000, 0.75f).contentEquals(UltronFx.process(src, 16000, 0.75f)))
    }
}
