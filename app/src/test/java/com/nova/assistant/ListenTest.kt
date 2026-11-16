package com.nova.assistant

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Three-second command silence rule, custom wake word and the unlock command. Pure JVM. */
class ListenTest {

    @After fun resetWake() { Logic.setWake(Logic.DEFAULT_WAKE) }

    // ------------------------------------------------------------ uniform command end-of-speech

    @Test fun spokenCommandsWaitForThreeSecondsOfSilence_andAllowLongCommands() {
        assertEquals(1300, Logic.END_SILENCE_MS)
        assertTrue("Long commands should not be cut off at the old 12-second limit", Logic.MAX_COMMAND_MS >= 60_000)
        assertTrue("No-speech guard must remain shorter than the normal command capture cap", Logic.MAX_COMMAND_MS > Logic.NO_SPEECH_COMMAND_MS)
    }

    // ------------------------------------------------------------ custom wake word

    @Test fun stripWake_default_stillWorks() {
        assertEquals("open youtube", Logic.stripWake("jarvis open youtube"))
        assertEquals("open youtube", Logic.stripWake("hey jarvis open youtube"))
        assertEquals("open youtube", Logic.stripWake("the jarvis open youtube"))
        assertEquals("torch on", Logic.stripWake("torch on"))
    }

    @Test fun stripWake_customWord_isRemoved_andJarvisToo() {
        Logic.setWake("jarvis")
        assertEquals("open youtube", Logic.stripWake("jarvis open youtube"))
        assertEquals("open youtube", Logic.stripWake("hey jarvis open youtube"))
        assertEquals("open youtube", Logic.stripWake("jarvis open youtube"))
        assertEquals("stop", Logic.stripWake("jarvis stop"))
    }

    @Test fun classify_withCustomWake_findsCommandsThatNeedTheStart() {
        Logic.setWake("jarvis")
        assertEquals("stop", Logic.classify("jarvis stop")?.kind)
        assertEquals("open_app", Logic.classify("jarvis open youtube")?.kind)
        assertEquals("youtube", Logic.classify("jarvis open youtube")?.arg)
        assertEquals("home", Logic.classify("jarvis go home")?.arg)
        assertEquals("settings", Logic.classify("jarvis wifi settings")?.kind)
    }

    @Test fun classify_defaultWake_unchanged() {
        assertEquals("stop", Logic.classify("jarvis stop")?.kind)
        assertEquals("youtube", Logic.classify("hey jarvis open youtube")?.arg)
        assertNull(Logic.classify("jarvis"))
    }

    @Test fun setWake_invalidFallsBackToJarvis() {
        Logic.setWake("x")
        assertEquals("open youtube", Logic.stripWake("jarvis open youtube"))
        Logic.setWake("hey jarvis")
        assertEquals("stop", Logic.stripWake("jarvis stop"))
    }

    // ------------------------------------------------------------ unlock command

    @Test fun unlock_isRecognised_andNeverAShortcut() {
        assertEquals("unlock", Logic.classify("unlock phone")?.kind)
        assertEquals("unlock", Logic.classify("jarvis unlock the phone")?.kind)
        assertEquals("global", Logic.classify("lock phone")?.kind)      // lock still locks
        assertTrue("unlock" !in Brain.SAFE_KINDS)                       // a shortcut / model can never unlock the phone
        val sk = Skill("bad", listOf("open sesame"), listOf("unlock phone"), "user")
        assertNotNull(Brain.validateSkill(sk))
    }
}
