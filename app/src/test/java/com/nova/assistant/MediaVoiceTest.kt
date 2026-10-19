package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import com.nova.assistant.MediaIntake.Voice as V

/** v7.51 Part 11-D. Expected values worked out by hand. */
class MediaVoiceTest {

    @Test fun statusPhrases() {
        assertEquals(V.STATUS, MediaIntake.voiceIntent("nova kya mila"))
        assertEquals(V.STATUS, MediaIntake.voiceIntent("Nova, kya mila hai?"))
        assertEquals(V.STATUS, MediaIntake.voiceIntent("kitni file mili"))
        assertEquals(V.STATUS, MediaIntake.voiceIntent("what did i share"))
    }

    @Test fun analyseVideoIsHonestNotYet() {
        assertEquals(V.ANALYSE_NOT_YET, MediaIntake.voiceIntent("nova ye video analyse karo"))
        assertEquals(V.ANALYSE_NOT_YET, MediaIntake.voiceIntent("is reel ko analyze karo"))
        assertEquals(V.ANALYSE_NOT_YET, MediaIntake.voiceIntent("media analysis karo"))
    }

    @Test fun otherSentencesAreNotTouched() {
        assertEquals(V.NONE, MediaIntake.voiceIntent("nova screen analyse karo"))
        assertEquals(V.NONE, MediaIntake.voiceIntent("screen par video analyse karo"))
        assertEquals(V.NONE, MediaIntake.voiceIntent("mujhe ek dost mila"))
        assertEquals(V.NONE, MediaIntake.voiceIntent("nova torch jala do"))
        assertEquals(V.NONE, MediaIntake.voiceIntent("ye video chalao"))
        assertEquals(V.NONE, MediaIntake.voiceIntent(""))
        assertEquals(V.NONE, MediaIntake.voiceIntent(null))
        assertEquals(V.NONE, MediaIntake.voiceIntent("is photo me kya hai"))
    }

    @Test fun longSentenceIsLeftAlone() {
        assertEquals(V.NONE, MediaIntake.voiceIntent("kal ek lamba video bana tha usko analyse karna hai jab time mile to bata dena"))
    }

    @Test fun analyseReplyNeverClaimsAnalysis() {
        val hi = MediaIntake.analyseNotYetReply(0, 0)
        assertTrue(hi.contains("abhi nahi"))
        assertFalse(hi.contains("ho gaya"))
        assertTrue(MediaIntake.analyseNotYetReply(2, 1).contains("2 file aur 1 link mile hain."))
        assertTrue(MediaIntake.analyseNotYetReply(0, 0, true).contains("not available yet"))
    }
}
