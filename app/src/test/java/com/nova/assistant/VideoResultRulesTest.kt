package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.12 AUTOPSY R3: result texts for video recording (pure). The real camera / MediaRecorder behaviour is NOT tested here. */
class VideoResultRulesTest {
    @Test fun savedStatusesAreGood() { for (s in listOf("saved", "saved_max", "saved_space")) assertTrue(s, VideoResultRules.forStatus(s)!!.good) }

    @Test fun failuresAreNeverCalledSaved() {
        for (s in listOf("no_frames", "stop_failed", "save_failed", "permission", "not_visible", "failed:camera busy")) {
            val t = VideoResultRules.forStatus(s)!!
            assertFalse(s, t.good)
            assertFalse(s, t.title.contains("save ho gayi"))
        }
    }

    @Test fun stopFailedTellsWhereTheFileIs() { assertTrue(VideoResultRules.forStatus("stop_failed")!!.body.contains("app folder")) }

    @Test fun failedCarriesTheReason() { assertTrue(VideoResultRules.forStatus("failed:no_camera")!!.body.contains("no_camera")) }

    @Test fun unknownStatusHasNoText() { assertNull(VideoResultRules.forStatus("already")); assertNull(VideoResultRules.forStatus("on")); assertNotNull(VideoResultRules.forStatus("saved")) }

    @Test fun startingIsNotAResult() { assertEquals(null, VideoResultRules.forStatus("starting")) }
}
