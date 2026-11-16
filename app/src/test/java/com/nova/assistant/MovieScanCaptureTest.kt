package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.64 PART 14B-2: the capture loop with a fake screen (no Android). */
class MovieScanCaptureTest {
    private class Fake(
        var protectedAt: (Int) -> Boolean? = { false },
        var shots: List<ByteArray?> = listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), byteArrayOf(4)),
        var cancelAtSleep: Int = -1,
        var sdk30: Boolean = true
    ) : MovieScanCapture.Source {
        var checks = 0; var shotCalls = 0; var sleeps = 0; var cancel = false
        val sleepMs = ArrayList<Long>()
        override fun foregroundProtected(): Boolean? = protectedAt(checks++)
        override fun shot(): ByteArray? = shots.getOrNull(shotCalls++)
        override fun cancelled(): Boolean = cancel
        override fun sleep(ms: Long) { sleeps++; sleepMs.add(ms); if (sleeps == cancelAtSleep) cancel = true }
        override fun supported(): Boolean = sdk30
    }

    @Test fun takesFourDistinctFramesWithGaps() {
        val f = Fake(); val r = MovieScanCapture.run(f, 4)
        assertEquals(MovieFrameRules.Stop.OK, r.stop); assertEquals(4, r.frames.size)
        assertEquals(listOf(1500L, 1500L, 1500L), f.sleepMs)
    }

    @Test fun countIsClampedToTwoToFour() {
        assertEquals(2, MovieScanCapture.run(Fake(), 0).frames.size)
        assertEquals(4, MovieScanCapture.run(Fake(), 99).frames.size)
    }

    @Test fun pausedVideoKeepsOneFrame() {
        val same = byteArrayOf(9, 9)
        val r = MovieScanCapture.run(Fake(shots = listOf(same, same.clone(), same.clone())), 3)
        assertEquals(MovieFrameRules.Stop.OK, r.stop); assertEquals(1, r.frames.size)
    }

    @Test fun protectedScreenFromTheStartCapturesNothing() {
        val f = Fake(protectedAt = { true }); val r = MovieScanCapture.run(f, 3)
        assertEquals(MovieFrameRules.Stop.PROTECTED, r.stop); assertTrue(r.frames.isEmpty()); assertEquals(0, f.shotCalls)
    }

    @Test fun protectedScreenMidScanThrowsEveryFrameAway() {
        // checks 0,1 (before + after shot 1) fine, check 2 (before shot 2) protected
        val f = Fake(protectedAt = { it >= 2 }); val r = MovieScanCapture.run(f, 4)
        assertEquals(MovieFrameRules.Stop.PROTECTED, r.stop); assertTrue(r.frames.isEmpty())
    }

    @Test fun screenChangesToProtectedWhileShotWasTaken() {
        val f = Fake(protectedAt = { it == 1 }); val r = MovieScanCapture.run(f, 2)
        assertEquals(MovieFrameRules.Stop.PROTECTED, r.stop); assertTrue(r.frames.isEmpty())
    }

    @Test fun noServiceOldAndroidNoFramesCancelled() {
        assertEquals(MovieFrameRules.Stop.NO_SERVICE, MovieScanCapture.run(Fake(protectedAt = { null }), 3).stop)
        assertEquals(MovieFrameRules.Stop.OLD_ANDROID, MovieScanCapture.run(Fake(sdk30 = false), 3).stop)
        assertEquals(MovieFrameRules.Stop.NO_FRAMES, MovieScanCapture.run(Fake(shots = listOf(null, null, null)), 3).stop)
        val c = Fake(cancelAtSleep = 1); val r = MovieScanCapture.run(c, 4)
        assertEquals(MovieFrameRules.Stop.CANCELLED, r.stop); assertTrue(r.frames.isEmpty())
    }

    @Test fun oversizeFrameIsDropped() {
        val big = ByteArray(MovieFrameRules.MAX_FRAME_BYTES + 1)
        val r = MovieScanCapture.run(Fake(shots = listOf(big, byteArrayOf(5))), 2)
        assertEquals(1, r.frames.size)
    }

    @Test fun statusTextsAreHonest() {
        for (en in listOf(false, true)) for (s in MovieFrameRules.Stop.values()) {
            val x = MovieFrameRules.statusText(s, 3, en).lowercase()
            assertFalse(x.contains("movie hai:")); assertFalse(x.contains("the movie is"))
            if (s == MovieFrameRules.Stop.OK) assertTrue(x.contains("14b-3"))
        }
        assertTrue(MovieFrameRules.statusText(MovieFrameRules.Stop.PROTECTED, 0, false).contains("kuch capture nahi kiya"))
    }
}
