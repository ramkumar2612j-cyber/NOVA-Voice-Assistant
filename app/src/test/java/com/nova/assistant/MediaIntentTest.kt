package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.01: pure-function tests for MediaIntent and the pure parts of SiteMedia. SiteMedia.replace / gradient are NOT called here (they use android Base64). */
class MediaIntentTest {
    @Test fun websiteWithPhotosIsSite() {
        val r = MediaIntent.classify("nova ek restaurant ki website banao jisme photos ho")
        assertNotNull(r)
        assertEquals(MediaIntent.Kind.SITE, r!!.kind)
        assertEquals("restaurant", r.subject)
        assertFalse(r.blocked)
    }

    @Test fun plainPictureIsLeftToImageIntent() {
        assertNull(MediaIntent.classify("ek billi ki photo banao"))
    }

    @Test fun videoRequestIsVideo() {
        val r = MediaIntent.classify("ek video banao")
        assertNotNull(r)
        assertEquals(MediaIntent.Kind.VIDEO, r!!.kind)
    }

    @Test fun recordingIsNotMedia() {
        assertNull(MediaIntent.classify("background me video recording on karo"))
    }

    @Test fun otherFeatureWordsWin() {
        assertNull(MediaIntent.classify("nova gemini kholo aur website banao jisme photo ho"))
    }

    @Test fun websiteWithoutMediaWordStaysWithOldRoute() {
        assertNull(MediaIntent.classify("nova ek gym ki website banao"))
    }

    @Test fun noPhotoOptOut() {
        val r = MediaIntent.classify("bina photo ke website banao")
        assertNotNull(r)
        assertEquals(MediaIntent.Kind.SITE, r!!.kind)
        assertFalse(MediaIntent.wantsImages("bina photo ke website banao"))
        assertFalse(MediaIntent.wantsImages("website without images"))
        assertFalse(MediaIntent.wantsImages("sirf text website banao"))
    }

    @Test fun picturesWantedByDefault() {
        assertTrue(MediaIntent.wantsImages("nova ek restaurant ki website banao"))
    }

    @Test fun videoWordDetected() {
        assertTrue(MediaIntent.wantsVideo("website me video background"))
        assertFalse(MediaIntent.wantsVideo("website me photo"))
    }

    @Test fun blockedWordsAreFlagged() {
        val r = MediaIntent.classify("nude photos ki website banao")
        assertNotNull(r)
        assertTrue(r!!.blocked)
    }

    @Test fun placeholdersAreDistinctAndInOrder() {
        val html = "<img src=\"NOVA_IMG:cozy italian restaurant\"><div style=\"background-image:url('NOVA_IMG:modern gym')\"></div><img src=\"NOVA_IMG:cozy italian restaurant\">"
        assertEquals(listOf("cozy italian restaurant", "modern gym"), SiteMedia.placeholders(html))
    }

    @Test fun noPlaceholdersMeansEmptyList() {
        assertTrue(SiteMedia.placeholders("<p>hello</p>").isEmpty())
    }

    @Test fun cleanMakesOneShortLine() {
        assertEquals("a b c", SiteMedia.clean("  a   b \n c "))
        assertEquals(160, SiteMedia.clean("x".repeat(500)).length)
    }

    @Test fun promptBlockTexts() {
        assertTrue(SiteMedia.promptBlock(true, false).contains("NOVA_IMG:"))
        assertTrue(SiteMedia.promptBlock(false, false).contains("does not want photos"))
        assertTrue(SiteMedia.promptBlock(true, true).contains("VIDEO"))
        assertFalse(SiteMedia.promptBlock(true, false).contains("VIDEO"))
    }
}
