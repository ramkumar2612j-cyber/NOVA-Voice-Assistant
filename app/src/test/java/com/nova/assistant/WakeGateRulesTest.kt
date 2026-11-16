package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.119: wake gate (v7.117 behaviour) as pure rules. Written, NOT RUN here (no kotlinc in the sandbox). */
class WakeGateRulesTest {
    @Test fun quietRoomIsNotVoice() = assertFalse(WakeGateRules.voice(250.0, 100.0))
    @Test fun floorIs300() { assertFalse(WakeGateRules.voice(300.0, 50.0)); assertTrue(WakeGateRules.voice(301.0, 50.0)) }
    @Test fun noisyRoomNeedsLouder() { assertFalse(WakeGateRules.voice(400.0, 300.0)); assertTrue(WakeGateRules.voice(541.0, 300.0)) }

    @Test fun holdWindowIsTwoSeconds() {
        val u = WakeGateRules.extend(10_000L, 0L, true)
        assertEquals(12_000L, u)
        assertTrue(WakeGateRules.isOpen(11_999L, u))
        assertFalse(WakeGateRules.isOpen(12_000L, u))
    }

    @Test fun silenceNeverExtends() = assertEquals(5_000L, WakeGateRules.extend(9_000L, 5_000L, false))

    @Test fun replayOnlyWhenTheGateOpens() {
        assertEquals(4, WakeGateRules.replayCount(5, false, true))    // newest chunk is fed normally
        assertEquals(0, WakeGateRules.replayCount(5, true, true))     // already open: nothing to replay
        assertEquals(0, WakeGateRules.replayCount(5, false, false))   // no voice
        assertEquals(0, WakeGateRules.replayCount(1, false, true))
        assertEquals(0, WakeGateRules.replayCount(0, false, true))
    }

    @Test fun resetOnlyOnTheClosingChunk() {
        assertTrue(WakeGateRules.closing(false, true))
        assertFalse(WakeGateRules.closing(false, false))
        assertFalse(WakeGateRules.closing(true, true))
        assertFalse(WakeGateRules.closing(true, false))
    }

    @Test fun simulatedSentenceKeepsTheStartOfTheWakeWord() {
        // chunks of 100 ms: 5 silent, then voice for 3 chunks, then silence for 30 chunks
        var until = 0L; var wasOpen = false; var fed = 0; var replayed = 0; var resets = 0
        val rms = List(5) { 100.0 } + List(3) { 900.0 } + List(30) { 100.0 }
        var now = 0L
        for ((i, r) in rms.withIndex()) {
            now += 100
            var open = WakeGateRules.isOpen(now, until)
            val v = WakeGateRules.voice(r, 100.0)
            if (v) { replayed += WakeGateRules.replayCount(minOf(i + 1, 5), open, true); until = WakeGateRules.extend(now, until, true); open = true }
            if (WakeGateRules.closing(open, wasOpen)) resets++
            wasOpen = open
            if (open) fed++
        }
        assertEquals(4, replayed)          // 0.4 s of pre-roll was replayed once
        assertEquals(1, resets)            // exactly one reset after the 2 s hold ended
        assertTrue(fed in 21..24)          // 3 voice chunks + ~2 s hold, not all 38 chunks
    }
}
