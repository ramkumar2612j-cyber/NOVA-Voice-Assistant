package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraEyeRulesTest {
    @Test fun backCamera() {
        for (s in listOf("nova peeche wale camera se dekho ye kya hai", "back camera se dekho", "rear camera se batao ye kya hai", "camera se dekho", "nova camera me kya hai",
            "mere saamne kya hai", "saamne dekho batao")) {
            val a = CameraEyeRules.parse(s)
            assertNotNull(s, a)
            assertEquals(s, CameraEyeRules.Facing.BACK, a!!.facing)
        }
    }
    @Test fun frontCamera() {
        for (s in listOf("front camera se dekho", "selfie camera se dekho kya dikh raha hai")) {
            assertEquals(s, CameraEyeRules.Facing.FRONT, CameraEyeRules.parse(s)?.facing)
        }
    }
    @Test fun otherCameraFeaturesAreNotTaken() {
        for (s in listOf("camera kholo", "nova camera on karo", "photo kheencho camera se", "selfie lo", "video record karo back camera se", "camera band karo",
            "front camera se video banao", "torch jala do", "screen dekho", "gaana kya chal raha hai", "battery dekho", "gallery dekho", "nova ye kya chal raha hai")) {
            assertNull(s, CameraEyeRules.parse(s))
        }
    }
    @Test fun tooLongIsNotTaken() {
        assertNull(CameraEyeRules.parse("camera se dekho " + "bahut ".repeat(20)))
    }
    @Test fun promptNeverAsksForNames() {
        val p = CameraEyeRules.systemPrompt(false)
        assertTrue(p.contains("NEVER identify a real person"))
        assertFalse(CameraEyeRules.userPrompt("ye kya hai \"x\"", CameraEyeRules.Facing.BACK, false).contains("\"x\""))
    }
}
