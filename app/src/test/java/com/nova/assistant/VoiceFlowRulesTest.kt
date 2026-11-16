package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFlowRulesTest {
    private val chunk = 100
    private val lim = CaptureRules.SILENCE_MS / chunk   // v6.36: 18 chunks of silence end a command (was 30)

    /** Feeds a pattern of chunks ("s" = speech, "." = silence); returns total ms when the capture ends, or -1. */
    private fun run(pattern: String, maxMs: Int = CaptureRules.MAX_MS): Int {
        var heard = false; var silent = 0; var total = 0
        for (c in pattern) {
            total += chunk
            val speaking = c == 's'
            if (speaking) heard = true
            silent = CaptureRules.nextSilentMs(silent, heard, speaking, chunk)
            if (CaptureRules.shouldEnd(heard, silent, total, CaptureRules.SILENCE_MS, maxMs, 8000)) return total
        }
        return -1
    }

    @Test fun continuousSpeechNeverEnds() = assertEquals(-1, run("s".repeat(300)))

    @Test fun pauseUnderTheLimitKeepsOneCommand() =
        assertEquals(-1, run("s".repeat(10) + ".".repeat(lim - 1) + "s".repeat(10)))   // 1 s speech, pause just under the limit, speech again

    @Test fun speechResumeResetsTimer() {
        val end = run("s".repeat(10) + ".".repeat(lim - 3) + "s" + ".".repeat(lim))
        assertEquals((10 + (lim - 3) + 1 + lim) * chunk, end)   // counted from the LAST speech chunk
    }

    @Test fun exactlyTheLimitEnds() = assertEquals((10 + lim) * chunk, run("s".repeat(10) + ".".repeat(lim)))

    @Test fun justUnderTheLimitDoesNotEnd() = assertEquals(-1, run("s".repeat(10) + ".".repeat(lim - 1)))

    @Test fun silenceBeforeSpeechDoesNotCountAsEndSilence() {
        var silent = 0
        repeat(40) { silent = CaptureRules.nextSilentMs(silent, false, false, chunk) }
        assertEquals(0, silent)
    }

    @Test fun sixtySecondEmergencyCap() = assertEquals(CaptureRules.MAX_MS, run("s".repeat(700)))

    @Test fun capIsOnlyEmergencyNotNormalStop() = assertTrue(run("s".repeat(10) + ".".repeat(lim)) < CaptureRules.MAX_MS)

    @Test fun constantsMatchLogic() {
        assertEquals(Logic.END_SILENCE_MS, CaptureRules.SILENCE_MS)
        assertEquals(Logic.MAX_COMMAND_MS, CaptureRules.MAX_MS)
    }

    // ---- battery warning
    @Test fun batteryWarningDeferredDuringVoiceWork() {
        assertFalse(AlertGate.maySpeak(false, true, false, false))    // command recording
        assertFalse(AlertGate.maySpeak(true, false, false, false))    // thinking
        assertFalse(AlertGate.maySpeak(false, false, true, false))
        assertFalse(AlertGate.maySpeak(false, false, false, true))
    }

    @Test fun batteryWarningSpeaksWhenIdle() =
        assertTrue(AlertGate.maySpeak(false, false, false, false))
}
