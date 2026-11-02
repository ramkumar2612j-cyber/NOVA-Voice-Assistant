package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.50 Part 10. Expected values worked out by hand (Kotlin was NOT compiled when this was written). */
class RoundaboutRulesTest {
    @Test fun shortHinglishYesIsApproved() {
        assertEquals(true, Logic.parseAnswer("ha kr do"))
        assertEquals(true, Logic.parseAnswer("haan kar do"))
        assertEquals(true, Logic.parseAnswer("haan karo"))
        assertEquals(true, Logic.parseAnswer("kar do"))
        assertEquals(true, Logic.parseAnswer("theek hai"))
        assertEquals(true, Logic.parseAnswer("bilkul"))
        assertEquals(true, Logic.parseAnswer("ok chalo"))
    }

    @Test fun noWordStillWins() {
        assertEquals(false, Logic.parseAnswer("nahi kar do"))
        assertEquals(false, Logic.parseAnswer("mat kar do"))
        assertEquals(false, Logic.parseAnswer("ha ruko"))
    }

    @Test fun unclearStaysUnclear() {
        assertNull(Logic.parseAnswer("do"))
        assertNull(Logic.parseAnswer("hai"))
        assertNull(Logic.parseAnswer("karo"))
        assertNull(Logic.parseAnswer("sabko bhej do abhi"))
        assertNull(Logic.parseAnswer("haan sabko kar do"))
        assertNull(Logic.parseAnswer("yes please"))
        assertNull(Logic.parseAnswer("yes yes yes yes"))
        assertNull(Logic.parseAnswer("ha kar do kar do kar do"))
    }

    @Test fun repeatAndUndoPhrases() {
        assertTrue(RoundaboutRules.repeat("usi ko dobara"))
        assertTrue(RoundaboutRules.repeat("wahi kar do"))
        assertFalse(RoundaboutRules.repeat("usi ko dobara call karo kal"))
        assertTrue(RoundaboutRules.undo("ulta kar do"))
        assertFalse(RoundaboutRules.undo("ulta kar do abhi"))
    }

    @Test fun reverseOnlyForHarmlessKinds() {
        assertEquals("torch_off", RoundaboutRules.reverseOf("torch_on", "")?.kind)
        assertEquals("torch_on", RoundaboutRules.reverseOf("torch_off", "")?.kind)
        assertEquals("down", RoundaboutRules.reverseOf("volume", "up")?.arg)
        assertEquals("play", RoundaboutRules.reverseOf("media", "pause")?.arg)
        assertNull(RoundaboutRules.reverseOf("call", "mummy"))
        assertNull(RoundaboutRules.reverseOf("delete", ""))
        assertNull(RoundaboutRules.reverseOf("open_app", "youtube"))
    }

    @Test fun contextMemoryNeverRepeatsRiskyKinds() {
        ContextMemory.clear()
        ContextMemory.remember(Logic.Cmd("call", "mummy"), 1000L)
        assertNull(ContextMemory.resolve("usi ko dobara", 2000L))
        ContextMemory.remember(Logic.Cmd("torch_on"), 3000L)
        assertEquals("torch_on", ContextMemory.resolve("wahi kar do", 4000L)?.kind)
        assertEquals("torch_off", ContextMemory.resolve("ulta kar do", 4000L)?.kind)
        assertNull(ContextMemory.resolve("ulta kar do", 3000L + ContextMemory.TTL_MS + 1))
        ContextMemory.clear()
    }

    @Test fun hintsAreShortAndHarmless() {
        assertEquals("torch_on", RoundaboutRules.hint("andhera hai")?.kind)
        assertEquals("up", RoundaboutRules.hint("awaaz nahi aa rahi")?.arg)
        assertEquals(-10, RoundaboutRules.hint("aankhen chub rahi hain")?.num)
        assertNull(RoundaboutRules.hint("kal raat andhera hai to main der se aaya aur phir so gaya yaar"))
        assertNull(RoundaboutRules.hint("mummy ko call karo"))
    }

    @Test fun riskyOrderWithOnlyAHintAsksWho() {
        assertTrue(RoundaboutRules.needsWho("wo jo kal wale ko bhej do"))
        assertTrue(RoundaboutRules.needsWho("usi ko call karo"))
        assertTrue(RoundaboutRules.needsWho("call him"))
        assertTrue(RoundaboutRules.needsWho("usko payment kar do"))
        assertFalse(RoundaboutRules.needsWho("mummy ko call karo"))
        assertFalse(RoundaboutRules.needsWho("usko display karo"))
        assertFalse(RoundaboutRules.needsWho("usi ko dobara"))
        assertFalse(RoundaboutRules.needsWho("pichle hafte ka mausam batao"))
        assertFalse(RoundaboutRules.needsWho("usko reply bhej do 2"))
        assertFalse(RoundaboutRules.needsWho("usko 9876543210 par call karo"))
        assertNotNull(RoundaboutRules.whoQuestion(false))
        assertTrue(RoundaboutRules.whoQuestion(true).contains("Who"))
    }
}
