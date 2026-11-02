package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.51 Part 12. Expected values worked out by hand. */
class FrameRulesTest {
    private val BIG = 1_000_000_000L   // plenty of heap

    @Test fun frameCountDefaultsAndCaps() {
        assertEquals(6, FrameRules.frameCount(null, 60_000, BIG))
        assertEquals(6, FrameRules.frameCount(0, 60_000, BIG))
        assertEquals(12, FrameRules.frameCount(50, 600_000, BIG))
        assertEquals(3, FrameRules.frameCount(3, 60_000, BIG))
    }

    @Test fun shortClipsGetFewerFrames() {
        assertEquals(1, FrameRules.frameCount(null, 800, BIG))
        assertEquals(2, FrameRules.frameCount(null, 1_000, BIG))   // 1000 / 500 = 2
        assertEquals(4, FrameRules.frameCount(null, 2_300, BIG))   // 2300 / 500 = 4
        assertEquals(6, FrameRules.frameCount(null, 0, BIG))       // unknown duration keeps the default
    }

    @Test fun lowMemoryMeansFewerOrNone() {
        assertEquals(0, FrameRules.frameCount(null, 60_000, FrameRules.FRAME_COST * 3))            // quarter of 3 costs < 1
        assertEquals(1, FrameRules.frameCount(null, 60_000, FrameRules.FRAME_COST * 4))            // exactly 1
        assertEquals(2, FrameRules.frameCount(null, 60_000, FrameRules.FRAME_COST * 8))
        assertEquals(0, FrameRules.frameCount(null, 60_000, 0))
    }

    @Test fun timePoints() {
        assertEquals(emptyList<Long>(), FrameRules.timePointsMs(10_000, 0))
        assertEquals(listOf(5_000L), FrameRules.timePointsMs(10_000, 1))
        assertEquals(listOf(0L), FrameRules.timePointsMs(0, 6))
        // 10 s, margin = min(500, 1000) = 500 -> last 9500; 3 points: 0, 4750, 9500
        assertEquals(listOf(0L, 4_750L, 9_500L), FrameRules.timePointsMs(10_000, 3))
        // 100 s: margin 500 -> last 99500; 6 points equally spaced
        assertEquals(listOf(0L, 19_900L, 39_800L, 59_700L, 79_600L, 99_500L), FrameRules.timePointsMs(100_000, 6))
        // 2 s: margin = min(500, 200) = 200 -> last 1800
        assertEquals(listOf(0L, 1_800L), FrameRules.timePointsMs(2_000, 2))
    }

    @Test fun timePointsAreSortedAndInside() {
        for (n in 1..12) for (d in longArrayOf(1, 300, 999, 5_000, 61_000, 3_600_000)) {
            val p = FrameRules.timePointsMs(d, n)
            assertEquals(n, p.size)
            assertEquals(p.sorted(), p)
            assertTrue(p.all { it in 0..d })
        }
    }

    @Test fun targetSize() {
        assertEquals(Pair(768, 432), FrameRules.targetSize(1920, 1080))
        assertEquals(Pair(432, 768), FrameRules.targetSize(1080, 1920))
        assertEquals(Pair(640, 480), FrameRules.targetSize(640, 480))
        assertEquals(Pair(768, 768), FrameRules.targetSize(768, 768))
        assertEquals(Pair(1, 1), FrameRules.targetSize(0, 100))
        assertEquals(Pair(768, 1), FrameRules.targetSize(100000, 130))
    }

    @Test fun grayWeights() {
        assertEquals(0, FrameRules.gray(0xFF000000.toInt()))
        assertEquals(255, FrameRules.gray(0xFFFFFFFF.toInt()))
        assertEquals(76, FrameRules.gray(0xFFFF0000.toInt()))    // 299 * 255 / 1000 = 76
        assertEquals(149, FrameRules.gray(0xFF00FF00.toInt()))   // 587 * 255 / 1000 = 149
        assertEquals(29, FrameRules.gray(0xFF0000FF.toInt()))    // 114 * 255 / 1000 = 29
    }

    @Test fun duplicateDetection() {
        val a = IntArray(1024) { 100 }
        val same = IntArray(1024) { 103 }          // diff 3 <= 4.0
        val other = IntArray(1024) { 110 }         // diff 10
        assertEquals(0.0, FrameRules.meanAbsDiff(a, a), 0.0001)
        assertEquals(3.0, FrameRules.meanAbsDiff(a, same), 0.0001)
        assertTrue(FrameRules.isDuplicate(a, same))
        assertFalse(FrameRules.isDuplicate(a, other))
        assertEquals(255.0, FrameRules.meanAbsDiff(a, IntArray(10)), 0.0001)
        assertFalse(FrameRules.isDuplicate(a, IntArray(10)))
        assertFalse(FrameRules.isDuplicateOfLast(emptyList(), a))
        assertTrue(FrameRules.isDuplicateOfLast(listOf(other, a), same))
        assertFalse(FrameRules.isDuplicateOfLast(listOf(a, other), same))
    }

    @Test fun onlyPhotoAndVideoGiveFrames() {
        assertTrue(FrameRules.supports(MediaIntake.Kind.VIDEO))
        assertTrue(FrameRules.supports(MediaIntake.Kind.IMAGE))
        assertFalse(FrameRules.supports(MediaIntake.Kind.LINK))
        assertFalse(FrameRules.supports(MediaIntake.Kind.UNKNOWN))
    }

    @Test fun textsAreHonest() {
        assertEquals("frames 3/6", FrameRules.stageText(3, 6))
        assertEquals("frames 0/0", FrameRules.stageText(-1, -2))
        assertTrue(FrameRules.errorText("x").startsWith("video se frame nahi nikle: "))
        assertEquals("6 frame nikle.", FrameRules.summary(6, 6, 0))
        assertEquals("4 frame nikle (2 milte-julte hata diye).", FrameRules.summary(4, 6, 2))
        assertEquals("Koi frame nahi nikla.", FrameRules.summary(0, 6, 0))
        assertEquals("3 frame nikle (zyada nahi ban paye).", FrameRules.summary(3, 6, 0))
    }

    @Test fun analysisStageCountsAreReal() {
        assertEquals(50, AnalysisStage("FRAMES", 3, 6, "x").percent())
        assertEquals(0, AnalysisStage("FRAMES", 0, 0, "x").percent())
        assertEquals(100, AnalysisStage("FRAMES", 9, 6, "x").percent())
        assertEquals("frames 3/6", AnalysisStage("FRAMES", 3, 6, "x").label())
        assertTrue(AnalysisStage("DONE", 1, 1, "").isTerminal())
        assertTrue(AnalysisStage("FAILED", 1, 1, "").isTerminal())
        assertFalse(AnalysisStage("FRAMES", 1, 2, "").isTerminal())
        assertTrue(AnalysisStage.isKnown("VISION"))
        assertFalse(AnalysisStage.isKnown("nope"))
    }
}
