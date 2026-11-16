package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.12: AI picture placeholders in a website (pure parts only; the real ImageGen call is not unit tested). */
class SiteMediaAiTest {
    @Test fun wantsPicturesOnlyWhenAsked() {
        assertTrue(SiteMedia.wantsAiPictures("restaurant ki website banao photos ke saath"))
        assertFalse(SiteMedia.wantsAiPictures("restaurant ki website banao"))
        assertFalse(SiteMedia.wantsAiPictures("restaurant ki website banao bina photo ke"))
    }

    @Test fun fillImagesKnownAndUnknown() {
        val html = "<img src=\"JARVIS_IMG:a red car\"><div style=\"background:url('JARVIS_IMG:blue sky')\"></div>"
        val out = SiteMedia.fillImages(html, mapOf("a red car" to "data:image/jpeg;base64,AA\$B"))
        assertTrue(out.contains("data:image/jpeg;base64,AA\$B"))
        assertTrue(out.contains(SiteMedia.PIXEL_GIF))
        assertFalse(out.contains("JARVIS_IMG:"))
    }

    @Test fun noPlaceholderNoChange() { assertEquals("<p>x</p>", SiteMedia.fillImages("<p>x</p>", emptyMap())) }

    @Test fun placeholdersListedOnce() {
        assertEquals(listOf("a cat", "a dog"), SiteMedia.placeholders("<img src=\"JARVIS_IMG:a cat\"><img src=\"JARVIS_IMG:a dog\"><img src=\"JARVIS_IMG:a cat\">"))
    }
}
