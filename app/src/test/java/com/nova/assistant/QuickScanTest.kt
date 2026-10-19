package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.81 PART 4 - the 10 second budget and the spoken start phrases of the live scan. */
class QuickScanTest {
    @Test fun budgetIsTenSecondsAndSmallerThanTheOldOnes() {
        assertEquals(10_000L, QuickScan.TOTAL_MS)
        assertTrue(QuickScan.FRAMES < ScreenVideoRules.FRAMES)
        assertTrue(QuickScan.GAP_MS < MovieFrameRules.GAP_MS)
        assertTrue(QuickScan.VISION_DEADLINE_MS < AnalysisPipelineRules.CALL_DEADLINE_MS)
        assertTrue(QuickScan.TEXT_DEADLINE_MS <= 9_000L)
    }

    @Test fun worstCaseCaptureFitsInTheBudget() {
        // 3 frames + 2 gaps + ~0.45 s per frame (hide laser 140 ms + screenshot) must leave room for the vision call
        val capture = QuickScan.FRAMES * 450L + (QuickScan.FRAMES - 1) * QuickScan.GAP_MS
        assertTrue(capture + 4_000L < QuickScan.TOTAL_MS)
    }

    @Test fun searchOnlyStartsEarly() {
        assertTrue(QuickScan.searchAllowed(0))
        assertTrue(QuickScan.searchAllowed(QuickScan.SEARCH_START_MS - 1))
        assertFalse(QuickScan.searchAllowed(QuickScan.SEARCH_START_MS))
        assertFalse(QuickScan.searchAllowed(-1))
        assertFalse(QuickScan.overBudget(10_000L))
        assertTrue(QuickScan.overBudget(10_001L))
    }

    @Test fun clockTextIsReal() {
        assertEquals("0.0 s / 10 s", QuickScan.clock(0, true))
        assertEquals("3.2 s / 10 s", QuickScan.clock(3_250, false))
        assertEquals("12.3 s / 10 s", QuickScan.clock(12_300, true))
        assertEquals("0.0 s / 10 s", QuickScan.clock(-5, true))
        assertEquals("3.2 s / 10 s", LaserMath.clock(3_250, true))
        assertEquals(0L, QuickScan.elapsed(500, 100))
    }

    @Test fun ownerSentencesStartTheScan() {
        for (s in listOf("nova dekho btao kya chal ra h", "nova dekho batao kya chal raha hai", "dekho kya chal rha hai", "ye dekho batao", "nova is screen ko dekho aur batao"))
            assertTrue(s, QuickScan.wantsLook(s))
    }

    @Test fun otherCommandsAreNotTakenOver() {
        for (s in listOf("battery dekho batao", "gaana kya chal raha hai dekho", "gallery dekho", "time batao", "dekho", "whatsapp kholo dekho batao", "kya chal raha hai", "dekho"))
            assertFalse(s, QuickScan.wantsLook(s))
    }
}
