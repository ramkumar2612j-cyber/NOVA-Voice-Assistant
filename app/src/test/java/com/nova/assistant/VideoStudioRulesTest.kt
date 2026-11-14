package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.173 VIDEO STUDIO rules (written without a Kotlin compiler: NOT RUN yet - run in GitHub Actions). */
class VideoStudioRulesTest {
    private fun clip(i: Int, dur: Long, m: List<Float>) = VideoStudioRules.Clip(i, dur, m)

    @Test fun options() {
        val o = VideoStudioRules.parseOptions("youtube 16:9 90 second slow motion nahi best clip pehle")
        assertFalse(o.vertical); assertEquals(90, o.targetS); assertFalse(o.slow); assertTrue(o.zoom); assertTrue(o.bestFirst)
        val d = VideoStudioRules.parseOptions("")
        assertTrue(d.vertical); assertEquals(45, d.targetS); assertTrue(d.slow); assertTrue(d.zoom)
        assertFalse(VideoStudioRules.parseOptions("zoom nahi").zoom)
        assertTrue(VideoStudioRules.parseOptions("youtube shorts").vertical)          // shorts are vertical
        assertTrue(VideoStudioRules.parseOptions("reel aur youtube shorts dono").vertical)
        assertFalse(VideoStudioRules.parseOptions("youtube").vertical)
        assertEquals(90, VideoStudioRules.parseOptions("reels 150 second").targetS)    // Reels limit
        assertEquals(150, VideoStudioRules.parseOptions("youtube 16:9 150 second").targetS)
    }
    @Test fun slowMoOnTheBurst() {
        val m = List(10) { 0.03f } + listOf(0.5f) + List(29) { 0.04f }
        val p = VideoStudioRules.plan(listOf(clip(0, 20000, m)), VideoStudioRules.Options())
        assertTrue(p.any { it.speed < 1f })
        val slow = p.first { it.speed < 1f }
        assertEquals(1500L, slow.srcMs); assertEquals(3000L, slow.outMs)
        assertTrue(VideoStudioRules.totalOutMs(p) <= 45_000L + 3000L)
    }
    @Test fun noSlowWhenOff() {
        val m = List(10) { 0.03f } + listOf(0.5f) + List(29) { 0.04f }
        assertTrue(VideoStudioRules.plan(listOf(clip(0, 20000, m)), VideoStudioRules.Options(slow = false)).none { it.speed < 1f })
    }
    @Test fun budgetAndOrder() {
        val cs = (0 until 5).map { clip(it, 30000, List(60) { 0.1f }) }
        val p = VideoStudioRules.plan(cs, VideoStudioRules.Options(targetS = 30))
        assertEquals(listOf(0, 1, 2, 3, 4), p.map { it.clip }.distinct())
        assertTrue(VideoStudioRules.totalOutMs(p) <= 31_000L)
        assertEquals(VideoStudioRules.Zoom.PUNCH, p.first { it.clip == 1 }.zoom)
    }
    @Test fun tooShortClipsGiveEmpty() { assertTrue(VideoStudioRules.plan(listOf(clip(0, 500, listOf(0.1f))), VideoStudioRules.Options()).isEmpty()) }
    @Test fun zoomCurve() {
        assertEquals(1f, VideoStudioRules.zoomAt(VideoStudioRules.Zoom.NONE, 0.5f), 0.0001f)
        assertEquals(1.12f, VideoStudioRules.zoomAt(VideoStudioRules.Zoom.PUSH, 1f), 0.0001f)
        assertEquals(1.18f, VideoStudioRules.zoomAt(VideoStudioRules.Zoom.PUNCH, 0f), 0.0001f)
        assertEquals(1.0f, VideoStudioRules.zoomAt(VideoStudioRules.Zoom.PUNCH, 1f), 0.0001f)
    }
    @Test fun voice() {
        assertEquals(VideoStudioRules.K_STUDIO_OPEN, VideoStudioRules.parse("nova video edit karo")!!.kind)
        assertEquals(VideoStudioRules.K_STUDIO_OPEN, VideoStudioRules.parse("video studio kholo")!!.kind)
        assertNull(VideoStudioRules.parse("nova video dikhao")); assertNull(VideoStudioRules.parse("nova gaana chalao"))
    }
    @Test fun motion() {
        assertEquals(0f, VideoStudioRules.motionBetween(IntArray(4) { 10 }, IntArray(4) { 10 }), 0.0001f)
        assertTrue(VideoStudioRules.motionBetween(IntArray(4) { 0 }, IntArray(4) { 255 }) >= 1f)
    }
}
