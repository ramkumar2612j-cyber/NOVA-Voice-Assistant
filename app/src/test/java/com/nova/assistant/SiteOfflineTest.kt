package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.115: the offline website needs no AI and must pass the premium gate by itself. Written, NOT RUN here. */
class SiteOfflineTest {
    @Test fun everyThemePassesThePremiumGate() {
        for (s in listOf("ek restaurant ki website banao", "beauty salon", "gym website", "mobile app landing page", "kirana shop", "plumber service")) {
            val html = SiteOffline.build(s, s)
            assertTrue(s, html.length >= SiteQuality.MIN_LEN)
            assertEquals(s, emptyList<String>(), SiteQuality.check(html))
        }
    }

    @Test fun noPlaceholderLeftBehind() {
        val html = SiteOffline.build("ek restaurant ki website banao", "")
        assertFalse(html.contains("@@"))
    }

    @Test fun themeIsPickedFromWholeWordsOnly() {
        assertEquals("food", SiteOffline.themeKey("meri cafe ki website"))
        assertEquals("beauty", SiteOffline.themeKey("salon"))
        assertEquals("fit", SiteOffline.themeKey("yoga classes"))
        assertEquals("tech", SiteOffline.themeKey("startup landing page"))
        assertEquals("shop", SiteOffline.themeKey("kirana store"))
        assertEquals("general", SiteOffline.themeKey("plumber"))
        assertEquals("general", SiteOffline.themeKey("captain"))   // contains "tea"? no: whole words only
    }

    @Test fun nameDropsFillerWords() {
        assertEquals("Restaurant", SiteOffline.siteName("ek restaurant ki website banao"))
        assertEquals("Our Website", SiteOffline.siteName("ek website banao"))
    }

    @Test fun nameIsEscapedSoSpokenTextCannotInjectHtml() {
        val html = SiteOffline.build("<script>alert(1)</script> cafe", "")
        assertFalse(html.contains("<script>alert"))
    }
}
