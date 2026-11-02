package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundVideoRulesTest {
    @Test fun startStopAndStatusNeedExplicitBackgroundVideoRecording() {
        assertEquals(BackgroundVideoRules.Action.START, BackgroundVideoRules.parse("background me video recording on karo")?.action)
        assertEquals(BackgroundVideoRules.Action.STOP, BackgroundVideoRules.parse("background me video recording band karo")?.action)
        assertEquals(BackgroundVideoRules.Action.STATUS, BackgroundVideoRules.parse("background me video recording chal rahi hai kya")?.action)
        assertEquals("front", BackgroundVideoRules.parse("background me front video recording start karo")?.facing)
    }

    @Test fun existingCameraToolKeepsItsOneSentenceCommand() {
        assertNull(BackgroundVideoRules.parse("mere mobile ka camera kholo aur front camera se video on karo"))
        assertNull(BackgroundVideoRules.parse("camera kholo aur video recording on karo"))
        assertNull(BackgroundVideoRules.parse("video recording on karo"))
    }

    @Test fun negationAndConflictingOrdersDoNotRun() {
        assertNull(BackgroundVideoRules.parse("background me video recording band mat karo"))
        assertNull(BackgroundVideoRules.parse("background me video recording start aur stop karo"))
        assertNull(BackgroundVideoRules.parse("background me video recording kaise hoti hai"))
    }
}
