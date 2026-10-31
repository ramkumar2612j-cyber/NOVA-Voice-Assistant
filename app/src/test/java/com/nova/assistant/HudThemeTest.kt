package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudThemeTest {
    @Test fun parseNeedsThePopupWord() {
        assertEquals(true, HudTheme.parse("nova light popup karo"))
        assertEquals(true, HudTheme.parse("popup halka kar do"))
        assertEquals(false, HudTheme.parse("nova dark popup karo"))
        assertEquals(false, HudTheme.parse("popup wapas ultron kar do"))
        assertNull(HudTheme.parse("torch light on karo"))
        assertNull(HudTheme.parse("popup light dark"))
        assertNull(HudTheme.parse("popup kya hai"))
    }

    @Test fun darkThemeChangesNothing() {
        assertEquals(0xFFFFFFFF.toInt(), HudTheme.ink(false, 0xFFFFFFFF.toInt()))
        assertEquals(0xFF27E1FF.toInt(), HudTheme.stateInk(false, 0xFF27E1FF.toInt()))
        assertEquals(0xF20A1236.toInt(), HudTheme.flat(false))
        assertEquals(0xF2081A48.toInt(), HudTheme.fillColors(false, false)[0])
    }

    @Test fun lightTextIsDarkAndUnknownColoursPassThrough() {
        assertEquals(0xFF0B1B3A.toInt(), HudTheme.ink(true, 0xFFFFFFFF.toInt()))
        assertEquals(0xFF51658A.toInt(), HudTheme.ink(true, 0xFFA9BCD9.toInt()))
        assertEquals(0xFF123456.toInt(), HudTheme.ink(true, 0xFF123456.toInt()))
        assertEquals(3, HudTheme.fillColors(true, true).size)
        assertTrue(HudTheme.fillColors(true, false)[0] != HudTheme.fillColors(false, false)[0])
    }

    @Test fun shimmerIsSubtleAndZeroAtTheEnds() {
        assertTrue(Math.abs(HudTheme.shimmerAlpha(0f)) < 0.0001f)
        assertTrue(Math.abs(HudTheme.shimmerAlpha(1f)) < 0.0001f)
        assertTrue(HudTheme.shimmerAlpha(0.5f) in 0.1f..0.2f)
        assertTrue(HudTheme.shimmer(HudTheme.SHIMMER_MS * 5 + 700) in 0f..1f)
        assertFalse(HudTheme.shimmerAlpha(0.5f) > 0.25f)
    }

    // v7.152: old ORDER phrasings keep switching the theme (nothing removed)...
    @Test fun popupThemeOrdersStillSwitch() {
        assertTrue(HudTheme.parse("nova light popup karo") != null && !HudTheme.isQuestion("nova light popup karo"))
        assertTrue(HudTheme.parse("popup halka kar do") != null && !HudTheme.isQuestion("popup halka kar do"))
        assertTrue(HudTheme.parse("popup wapas ultron kar do") != null && !HudTheme.isQuestion("popup wapas ultron kar do"))
        assertTrue(HudTheme.parse("popup dark") != null && !HudTheme.isQuestion("popup dark"))   // the short old command keeps working
        assertTrue(HudTheme.parse("light popup on karo") != null && !HudTheme.isQuestion("light popup on karo"))
    }

    // ...but a QUESTION must answer the state, never silently flip the setting.
    @Test fun popupThemeQuestionsAreNeverOrders() {
        assertTrue(HudTheme.isQuestion("popup light hai ya dark"))
        assertTrue(HudTheme.isQuestion("popup ka rang kya hai"))
        assertTrue(HudTheme.isQuestion("popup kaun sa theme hai batao"))
        assertTrue(HudTheme.isQuestion("popup light hai"))
        assertTrue(HudTheme.isQuestion("popup dark?"))
    }
}
