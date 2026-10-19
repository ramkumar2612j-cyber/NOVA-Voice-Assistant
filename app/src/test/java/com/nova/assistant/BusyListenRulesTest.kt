package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.73: voice while busy. Pure rules. Written, NOT RUN here (no kotlinc); GitHub Actions runs it. */
class BusyListenRulesTest {
    @Test fun listensWhenBusyAndSilent() {
        assertTrue(BusyListenRules.mayListen(true, true, false, false, false))
    }

    @Test fun neverListensWhileNovaSpeaks() {
        assertFalse(BusyListenRules.mayListen(true, true, true, false, false))
    }

    @Test fun lockedPhoneNeverListens() {
        assertFalse(BusyListenRules.mayListen(true, true, false, true, false))
    }

    @Test fun switchOffOrNotBusyOrWaitingForAnswerDoesNotListen() {
        assertFalse(BusyListenRules.mayListen(false, true, false, false, false))
        assertFalse(BusyListenRules.mayListen(true, false, false, false, false))
        assertFalse(BusyListenRules.mayListen(true, true, false, false, true))
    }

    @Test fun decideQueueStopEmptyNone() {
        assertEquals(BusyListenRules.Action.QUEUE, BusyListenRules.decide(true, false, "torch jala do", false))
        assertEquals(BusyListenRules.Action.STOP, BusyListenRules.decide(true, false, "ruko", true))
        assertEquals(BusyListenRules.Action.EMPTY, BusyListenRules.decide(true, false, "   ", false))
        assertEquals(BusyListenRules.Action.NONE, BusyListenRules.decide(false, false, "torch jala do", false))
        assertEquals(BusyListenRules.Action.NONE, BusyListenRules.decide(true, true, "haan", false))
    }

    @Test fun voiceItemsKeepVoiceFlagInQueue() {
        val q = CmdQueue.Queue()
        q.addAll(CmdQueue.split("volume badha do"), { false }, true)
        assertTrue(q.snapshot()[0].voice)
    }
}
