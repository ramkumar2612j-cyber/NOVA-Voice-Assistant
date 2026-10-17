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
        val html = "<img src=\"NOVA_IMG:a red car\"><div style=\"background:url('NOVA_IMG:blue sky')\"></div>"
        val out = SiteMedia.fillImages(html, mapOf("a red car" to "data:image/jpeg;base64,AA\$B"))
        assertTrue(out.contains("data:image/jpeg;base64,AA\$B"))
        assertTrue(out.contains(SiteMedia.PIXEL_GIF))
        assertFalse(out.contains("NOVA_IMG:"))
    }

    @Test fun noPlaceholderNoChange() { assertEquals("<p>x</p>", SiteMedia.fillImages("<p>x</p>", emptyMap())) }

    @Test fun placeholdersListedOnce() {
        assertEquals(listOf("a cat", "a dog"), SiteMedia.placeholders("<img src=\"NOVA_IMG:a cat\"><img src=\"NOVA_IMG:a dog\"><img src=\"NOVA_IMG:a cat\">"))
    }
}
