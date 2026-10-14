package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class AiChoiceTest {
    private val ids = listOf("groq", "zai", "openrouter")

    @Test fun cleanKeepsKnownAndFallsBackToAuto() {
        assertEquals("auto", AiChoice.clean(null, ids))
        assertEquals("auto", AiChoice.clean("", ids))
        assertEquals("auto", AiChoice.clean("nonsense", ids))
        assertEquals("gemini", AiChoice.clean(" Gemini ", ids))
        assertEquals("groq", AiChoice.clean("GROQ", ids))
    }

    @Test fun orderWithFallbackPutsChosenFirst() {
        val r = AiChoice.order(ids, { it }, "zai", true)
        assertEquals(listOf("zai", "groq", "openrouter"), r)
    }

    @Test fun orderWithoutFallbackKeepsOnlyChosen() {
        assertEquals(listOf("zai"), AiChoice.order(ids, { it }, "zai", false))
        assertTrue(AiChoice.order(ids, { it }, "missing", false).isEmpty())
    }

    @Test fun autoAndGeminiKeepRankedOrder() {
        assertEquals(ids, AiChoice.order(ids, { it }, "auto", false))
        assertEquals(ids, AiChoice.order(ids, { it }, "gemini", true))
    }

    @Test fun geminiModeRules() {
        assertEquals(AiChoice.GeminiMode.FIRST, AiChoice.geminiMode("auto", true))
        assertEquals(AiChoice.GeminiMode.FIRST, AiChoice.geminiMode("gemini", false))
        assertEquals(AiChoice.GeminiMode.AFTER_FREE, AiChoice.geminiMode("groq", true))
        assertEquals(AiChoice.GeminiMode.NEVER, AiChoice.geminiMode("groq", false))
    }

    @Test fun chainAllowedOnlyFalseForGeminiWithoutFallback() {
        assertFalse(AiChoice.chainAllowed("gemini", false))
        assertTrue(AiChoice.chainAllowed("gemini", true))
        assertTrue(AiChoice.chainAllowed("auto", false))
        assertTrue(AiChoice.chainAllowed("groq", false))
    }

    @Test fun chipLabel() {
        assertEquals("AI: AUTO", AiChoice.chipLabel("auto"))
        assertEquals("AI: GEMINI", AiChoice.chipLabel("gemini"))
        assertEquals("AI: GROQ", AiChoice.chipLabel("groq"))
    }
}
