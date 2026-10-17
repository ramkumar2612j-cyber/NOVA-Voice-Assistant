package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v7.12: the repo's OWN ringer / rotation / screenshot / volume rules (Understand.classify). Added next to PhoneVoiceRulesTest, which tests the other line's PhoneVoiceRules. */
class PhoneVoiceRepoRulesTest {
    private fun c(s: String) = Understand.classify(s)

    @Test fun screenshotRingerRotationRepoStyle() {
        assertEquals("ringer", c("ringer silent karo")?.kind); assertEquals("silent", c("ringer silent karo")?.arg)
        assertEquals("vibrate", c("vibrate mode")?.arg)
        assertEquals("rotation", c("rotation off karo")?.kind); assertEquals("auto_off", c("rotation off karo")?.arg)
        assertEquals("auto_on", c("auto rotate on karo")?.arg)
    }

    @Test fun volumeNotStolen() {
        assertEquals("volume", c("volume mute")?.kind)
        assertEquals("volume", c("awaaz band karo")?.kind)
        assertNull(c("rotation")?.takeIf { it.kind == "rotation" })
    }
}
