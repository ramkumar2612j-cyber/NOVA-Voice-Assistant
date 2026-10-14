package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v6.75 (Part 5): VoiceDiagnosis (v6.74) gave no JUnit coverage of its own. These tests describe the state texts; none claims a feature "works" on a phone. */
class VoiceDiagnosisTest {
    private val none = VoiceDiagnosis.StartFailure.NONE

    @Test fun startFailureIsClassifiedFromTheException() {
        assertEquals(VoiceDiagnosis.StartFailure.BACKGROUND_NOT_ALLOWED,
            VoiceDiagnosis.classifyStartFailure("ForegroundServiceStartNotAllowedException", null, true))
        assertEquals(VoiceDiagnosis.StartFailure.MIC_PERMISSION,
            VoiceDiagnosis.classifyStartFailure("SecurityException", "Starting FGS with type microphone requires RECORD_AUDIO", true))
        assertEquals(VoiceDiagnosis.StartFailure.MIC_PERMISSION,
            VoiceDiagnosis.classifyStartFailure("IllegalStateException", "anything", false))
        assertEquals(VoiceDiagnosis.StartFailure.OTHER,
            VoiceDiagnosis.classifyStartFailure("RuntimeException", "boom", true))
    }

    @Test fun everyStartFailureHasAnEnglishAndHinglishText() {
        assertEquals("", VoiceDiagnosis.startFailureText(none, true))
        for (f in listOf(VoiceDiagnosis.StartFailure.MIC_PERMISSION, VoiceDiagnosis.StartFailure.BACKGROUND_NOT_ALLOWED, VoiceDiagnosis.StartFailure.OTHER)) {
            assertTrue(f.name, VoiceDiagnosis.startFailureText(f, true).isNotBlank())
            assertTrue(f.name, VoiceDiagnosis.startFailureText(f, false).isNotBlank())
        }
    }

    @Test fun wakeRowGivesTheRealReason() {
        val noMic = VoiceDiagnosis.wake(false, false, none, "", true)
        assertEquals("off", noMic.state); assertEquals("mic", noMic.fix)
        assertEquals("ok", VoiceDiagnosis.wake(true, true, none, "", true).state)
        val bg = VoiceDiagnosis.wake(true, false, VoiceDiagnosis.StartFailure.BACKGROUND_NOT_ALLOWED, "", true)
        assertEquals("off", bg.state); assertEquals("activate", bg.fix); assertTrue(bg.why.contains("background"))
        val fatal = VoiceDiagnosis.wake(true, false, none, "Mic is busy in another app", true)
        assertEquals("off", fatal.state); assertTrue(fatal.why.contains("Mic is busy"))
        // a running service has obviously recovered: an old fatal text is not shown
        assertEquals("ok", VoiceDiagnosis.wake(true, true, none, "Mic is busy in another app", true).state)
    }

    @Test fun ttsRowSeparatesEngineErrorFromMissingHindiVoice() {
        assertEquals("off", VoiceDiagnosis.tts(false, false, VoiceDiagnosis.TTS_PENDING, "unknown", true).state)
        val engine = VoiceDiagnosis.tts(true, false, -1, "unknown", true)
        assertEquals("off", engine.state); assertEquals("tts", engine.fix)
        assertEquals("warn", VoiceDiagnosis.tts(true, false, VoiceDiagnosis.TTS_PENDING, "unknown", true).state)
        assertEquals("warn", VoiceDiagnosis.tts(true, true, 0, "missing", true).state)
        assertEquals("ok", VoiceDiagnosis.tts(true, true, 0, "ok", true).state)
    }

    @Test fun batteryRowIsBrandAwareButNeverBlocksAnything() {
        assertEquals("ok", VoiceDiagnosis.battery(true, "samsung", true).state)
        val xiaomi = VoiceDiagnosis.battery(false, "Xiaomi", true)
        assertEquals("warn", xiaomi.state); assertEquals("battery", xiaomi.fix); assertTrue(xiaomi.why.contains("Autostart"))
        val samsung = VoiceDiagnosis.battery(false, "SAMSUNG", true)
        assertEquals("warn", samsung.state); assertTrue(samsung.why.contains("auto-start"))
        val google = VoiceDiagnosis.battery(false, "google", true)
        assertEquals("warn", google.state)
        assertFalse(google.why.contains("Autostart") || google.why.contains("auto-start"))
    }
}
