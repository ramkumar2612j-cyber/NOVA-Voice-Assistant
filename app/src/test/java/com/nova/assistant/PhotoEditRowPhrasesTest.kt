package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** v7.12: the exact sentences the photo-editor buttons in ui.html send (nova-photo-pro-js). If a phrase changes there, change it here. */
class PhotoEditRowPhrasesTest {
    private fun k(s: String) = PhotoEditIntent.parse(s)?.kind

    @Test fun buttonPhrases() {
        assertEquals(PhotoEditIntent.Kind.ENHANCE, k("is photo ko professional banao"))
        assertEquals(PhotoEditIntent.Kind.BRIGHT, k("is photo ko bright karo"))
        assertEquals(PhotoEditIntent.Kind.FILTER, k("is photo ko black and white karo"))
        assertEquals(PhotoEditIntent.Kind.CROP, k("is photo ko square crop karo"))
        assertEquals(PhotoEditIntent.Kind.BG_REMOVE, k("is photo ka background hatao"))
        assertEquals(PhotoEditIntent.Kind.ORIGINAL, k("original photo wapas"))
    }

    @Test fun aiKindsNeedAi() {
        assertEquals(true, PhotoEditIntent.parse("is photo ka background hatao")?.needsAi)
        assertEquals(false, PhotoEditIntent.parse("is photo ko professional banao")?.needsAi)
        assertNotNull(PhotoEditIntent.parse("is photo ko black and white karo"))
    }
}
