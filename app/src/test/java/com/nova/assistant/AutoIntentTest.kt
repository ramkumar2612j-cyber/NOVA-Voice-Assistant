package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.30 PART 1: pure tests for AutoIntent (no Android classes). NOT RUN in the sandbox (no kotlinc): the CI "Build and Test APK" run is the first real run. */
class AutoIntentTest {
    private fun k(s: String, photo: Boolean = false) = AutoIntent.detect(s, photo).kind

    @Test fun logoWishIsImage() { assertEquals(AutoIntent.Kind.IMAGE, k("mujhe ek logo chahiye")) }
    @Test fun englishPosterIsImage() { assertEquals(AutoIntent.Kind.IMAGE, k("i need a poster for my cafe")) }
    @Test fun siteWishIsSite() { assertEquals(AutoIntent.Kind.SITE, k("gym ke liye site chahiye")) }
    @Test fun onlineStoreIsSite() { assertEquals(AutoIntent.Kind.SITE, k("online store banwana hai")) }
    @Test fun drawWithoutNounIsImage() { assertEquals(AutoIntent.Kind.IMAGE, k("ek billi draw karo")) }
    @Test fun logoForWebsiteIsImageNotSite() {
        assertEquals(AutoIntent.Kind.IMAGE, k("website ke liye logo banao"))
        assertEquals(AutoIntent.Kind.IMAGE, k("create a logo for my website"))
    }
    @Test fun logoWaliWebsiteIsSite() { assertEquals(AutoIntent.Kind.SITE, k("logo wali website banao")) }
    @Test fun photosAsPartOfSiteStaySite() { assertEquals(AutoIntent.Kind.SITE, k("restaurant ki website photos ke sath banao")) }
    @Test fun withoutPhotoStillSite() { assertEquals(AutoIntent.Kind.SITE, k("bina photo ke website banao")) }
    @Test fun questionsAreNotRequests() {
        assertEquals(AutoIntent.Kind.NONE, k("website kaise banate hain"))
        assertEquals(AutoIntent.Kind.NONE, k("what is a logo"))
        assertEquals(AutoIntent.Kind.NONE, k("how to make a poster"))
    }
    @Test fun otherFeaturesKeepTheirSentences() {
        assertEquals(AutoIntent.Kind.NONE, k("photo whatsapp pe bhejo"))
        assertEquals(AutoIntent.Kind.NONE, k("gallery se photo chuno"))
        assertEquals(AutoIntent.Kind.NONE, k("website publish karo"))
        assertEquals(AutoIntent.Kind.NONE, k("nova torch jala do"))
        assertEquals(AutoIntent.Kind.NONE, k("nova volume badhao"))
    }
    @Test fun oneWordIsNothing() { assertEquals(AutoIntent.Kind.NONE, k("draw")) }
    @Test fun blockedWordsAreFlagged() { assertTrue(AutoIntent.detect("nude logo banao").blocked) }
    @Test fun normalRequestIsNotBlocked() { assertFalse(AutoIntent.detect("mujhe ek logo chahiye").blocked) }
    @Test fun hindiAnimalSubjectIsTranslated() {
        val r = AutoIntent.detect("ek billi draw karo")
        assertEquals("cat", r.subject)
    }
    @Test fun photoAttachedEditIsPhotoEdit() { assertEquals(AutoIntent.Kind.PHOTO_EDIT, k("background hatao", true)) }
    @Test fun editWithoutPhotoIsNotPhotoEdit() { assertEquals(AutoIntent.Kind.NONE, k("background hatao", false)) }
    @Test fun textJobsAreWrite() {
        assertEquals(AutoIntent.Kind.WRITE, k("ek kahani likh do"))
        assertEquals(AutoIntent.Kind.WRITE, k("write a poem about rain"))
        assertEquals(AutoIntent.Kind.WRITE, k("mere liye resume likho"))
    }
    @Test fun writeNeverStealsPictureOrSite() {
        assertEquals(AutoIntent.Kind.SITE, k("gym ke liye site chahiye jisme code likha ho"))
        assertEquals(AutoIntent.Kind.NONE, k("website ka code likho"))
    }
    @Test fun writeIsOnlyAHintNovaServiceDoesNotRouteIt() {
        // NovaService handles SITE and IMAGE only; WRITE falls through to the normal AI chat
        assertTrue(AutoIntent.detect("ek kahani likh do").kind != AutoIntent.Kind.SITE)
    }
}
