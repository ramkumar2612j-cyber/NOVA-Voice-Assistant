package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class AppSearchRulesTest {
    @Test fun anyAppSearchWithMe() {
        val r = AppSearchRules.parse("youtube me kesariya search karo")!!
        assertEquals(AppSearchRules.Kind.SEARCH, r.kind)
        assertEquals("youtube", r.appPhrase)
        assertEquals("kesariya", r.query)
        assertFalse(r.play)
    }
    @Test fun playMarksPlay() {
        val r = AppSearchRules.parse("nova wynk par arijit singh ke gaane chalao")!!
        assertEquals("wynk", r.appPhrase)
        assertEquals("arijit singh", r.query)
        assertTrue(r.play)
    }
    @Test fun openThenSearch() {
        val r = AppSearchRules.parse("amazon kholo aur shoes search karo")!!
        assertEquals("amazon", r.appPhrase)
        assertEquals("shoes", r.query)
    }
    @Test fun noQueryNoMatch() {
        assertNull(AppSearchRules.parse("youtube me search karo"))
        assertNull(AppSearchRules.parse("youtube kholo"))
        assertNull(AppSearchRules.parse("battery kitni hai"))
    }
    @Test fun shareWithTarget() {
        val r = AppSearchRules.parse("ye share karo whatsapp pe")!!
        assertEquals(AppSearchRules.Kind.SHARE, r.kind)
        assertEquals("whatsapp", r.appPhrase)
    }
    @Test fun shareAlone() {
        val r = AppSearchRules.parse("isko share karo")!!
        assertEquals(AppSearchRules.Kind.SHARE, r.kind)
        assertEquals("", r.appPhrase)
    }
    @Test fun otherShareMeaningsAreNotHijacked() {
        assertNull(AppSearchRules.parse("screen share karo"))
        assertNull(AppSearchRules.parse("location share karo"))
    }
}
