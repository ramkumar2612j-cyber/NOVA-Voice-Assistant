package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovaV73Test {
    @Test fun fastThinkingOnlyFor25Flash() {
        assertTrue(GeminiSpeed.supports("gemini-2.5-flash"))
        assertTrue(GeminiSpeed.supports("gemini-2.5-flash-lite"))
        assertFalse(GeminiSpeed.supports("gemini-2.5-pro"))
        assertFalse(GeminiSpeed.supports("gemini-2.0-flash"))
        assertFalse(GeminiSpeed.supports("gemini-1.5-flash"))
    }

    @Test fun fastThinkingLeavesOtherModelsUntouched() {
        val body = "{\"contents\":[]}"
        assertEquals(body, GeminiSpeed.withFastThinking("gemini-2.0-flash", body))
        assertEquals("not json", GeminiSpeed.withFastThinking("gemini-2.5-flash", "not json"))
    }

    @Test fun fastThinkingAddsBudgetZero() {
        val out = GeminiSpeed.withFastThinking("gemini-2.5-flash", "{\"generationConfig\":{\"temperature\":0.2}}")
        assertTrue(out.contains("thinkingBudget"))
        assertTrue(out.contains("temperature"))
        val noCfg = GeminiSpeed.withFastThinking("gemini-2.5-flash", "{\"contents\":[]}")
        assertTrue(noCfg.contains("thinkingBudget"))
    }

    @Test fun samjhaLineHasSubjectStylePhotosAndMode() {
        val l = SiteSummary.samjha("restaurant", "nova ek modern restaurant ki website banao 5 photos ke saath", false, false)
        assertTrue(l.startsWith("Samjha: restaurant"))
        assertTrue(l.contains("style modern"))
        assertTrue(l.contains("5 photo"))
        assertTrue(l.contains("nayi website"))
        assertTrue(SiteSummary.samjha("cafe", "isko professional banao", true, true).contains("edit of the last website"))
        assertNull(SiteSummary.photoCount("koi photo nahi"))
        assertNull(SiteSummary.style("kuch nahi"))
    }

    @Test fun geminiRichNoteOverridesReadAloudRules() {
        assertTrue(FreeProviders.GEMINI_RICH_NOTE.contains("ignore the rules about being read aloud"))
        assertTrue(FreeProviders.GEMINI_RICH_NOTE.contains("Samjha:"))
    }
}
