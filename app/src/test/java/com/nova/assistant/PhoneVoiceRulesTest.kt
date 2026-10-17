package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** v6.99 Part 1: voice "call uthao / call kaato" and the pure parts of the other phone commands. */
class PhoneVoiceRulesTest {
    private fun k(s: String) = PhoneVoiceRules.parse(Logic.norm(s))?.kind

    @Test fun answerPhrases() {
        for (s in listOf("call uthao", "call utha lo", "phone uthao", "answer the call", "pick up the call", "call receive karo", "accept call"))
            assertEquals(s, PhoneVoiceRules.K_ANSWER, k(s))
    }

    @Test fun rejectPhrases() {
        for (s in listOf("call kaato", "call kaat do", "reject call", "call cut karo", "call band karo", "hang up", "end call", "decline the call"))
            assertEquals(s, PhoneVoiceRules.K_REJECT, k(s))
    }

    @Test fun notCallCommands() {
        assertNull(k("rahul ko call karo"))
        assertNull(k("call mummy"))
        assertNull(k("phone band karo"))          // a power question, not a call
        assertNull(k("call mat uthao"))           // negated
        assertNull(k("kal subah call karna"))
    }

    @Test fun classifyGivesCallKinds() {
        assertEquals(PhoneVoiceRules.K_ANSWER, Logic.classify("call uthao")?.kind)
        assertEquals(PhoneVoiceRules.K_REJECT, Logic.classify("call kaato")?.kind)
        assertEquals("call", Logic.classify("call rahul")?.kind)   // a normal contact call is untouched
    }

    @Test fun pickAction() {
        assertEquals(0, PhoneVoiceRules.pickAction(listOf("Answer", "Decline"), true))
        assertEquals(1, PhoneVoiceRules.pickAction(listOf("Answer", "Decline"), false))
        assertEquals(1, PhoneVoiceRules.pickAction(listOf("Decline", "Answer"), true))
        assertEquals(0, PhoneVoiceRules.pickAction(listOf("Hang Up"), false))
        assertEquals(-1, PhoneVoiceRules.pickAction(listOf("Hang Up"), true))
        assertEquals(-1, PhoneVoiceRules.pickAction(listOf("Reply", "Mute"), true))
        assertEquals(-1, PhoneVoiceRules.pickAction(emptyList(), false))
    }

    @Test fun part2ScreenshotRingerRotation() {
        // v7.x: the app uses the repo's OWN rules for screenshot / ringer / rotation (see Logic.classify comment), same as PhoneVoiceRepoRulesTest
        assertEquals("global", Logic.classify("screenshot lo")?.kind); assertEquals("screenshot", Logic.classify("screenshot lo")?.arg)
        assertEquals("screenshot", Logic.classify("take screenshot")?.arg)
        val r = Logic.classify("ringer silent karo")
        assertEquals(PhoneVoiceRules.K_RINGER, r?.kind); assertEquals("silent", r?.arg)
        assertEquals("vibrate", Logic.classify("vibrate mode")?.arg)
        assertEquals("normal", Logic.classify("ringer normal")?.arg)
        val o = Logic.classify("rotation off karo")
        assertEquals(PhoneVoiceRules.K_ROTATION, o?.kind); assertEquals("auto_off", o?.arg)
        assertEquals("auto_on", Logic.classify("auto rotate on karo")?.arg)
    }

    @Test fun part2DoesNotStealOtherCommands() {
        assertEquals("volume", Logic.classify("volume mute")?.kind)
        assertEquals("volume", Understand.classify("awaaz band karo")?.kind)   // Understand = Logic + the loose Hinglish reader (what the app calls)
        assertEquals("quiet_on", Logic.classify("silent replies on")?.kind)
        assertNull(k("screenshot delete karo"))
        assertNull(k("rotation"))                 // no on / off word: nothing is guessed
        assertNull(k("ringer"))
    }

    @Test fun allFiveKindsActive() {
        assertEquals(PhoneVoiceRules.KINDS, PhoneVoiceRules.ACTIVE)
    }

    @Test fun texts() {
        assertEquals("Call utha liya", PhoneVoiceRules.doneText(PhoneVoiceRules.K_ANSWER, "", false))
        assertEquals("Call rejected", PhoneVoiceRules.doneText(PhoneVoiceRules.K_REJECT, "", true))
        assertNotNull(PhoneActions.failText("no_permission", true, false))
    }
}
