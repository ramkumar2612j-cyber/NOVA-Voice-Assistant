package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEditIntentTest {
    private fun k(s: String) = PhotoEditIntent.parse(s)?.kind

    @Test fun enhance() { assertEquals(PhotoEditIntent.Kind.ENHANCE, k("nova is photo ko professional banao")); assertEquals(PhotoEditIntent.Kind.ENHANCE, k("photo saaf aur sundar karo")) }
    @Test fun bgRemoveNeedsAi() { val p = PhotoEditIntent.parse("is photo ka background hatao")!!; assertEquals(PhotoEditIntent.Kind.BG_REMOVE, p.kind); assertTrue(p.needsAi) }
    @Test fun bgReplace() { assertEquals(PhotoEditIntent.Kind.BG_REPLACE, k("photo ka background badlo")) }
    @Test fun filters() {
        assertEquals("BW", PhotoEditIntent.parse("is photo ko black and white karo")!!.preset)
        assertEquals("SEPIA", PhotoEditIntent.parse("photo vintage karo")!!.preset)
        assertEquals("CINEMATIC", PhotoEditIntent.parse("isko cinematic karo photo")!!.preset)
        assertFalse(PhotoEditIntent.parse("is photo ko black and white karo")!!.needsAi)
    }
    @Test fun cropRatios() {
        assertEquals("1:1", PhotoEditIntent.parse("photo square crop karo")!!.ratio)
        assertEquals("16:9", PhotoEditIntent.parse("photo 16:9 crop karo")!!.ratio)
        assertEquals("4:5", PhotoEditIntent.parse("photo crop 4 x 5")!!.ratio)
    }
    @Test fun brightAndOriginal() {
        assertEquals(PhotoEditIntent.Kind.BRIGHT, k("is photo ko bright karo"))
        assertEquals(PhotoEditIntent.Kind.ORIGINAL, k("pehle jaisa photo wapas karo"))
    }
    @Test fun restyleAndRemoveObject() {
        assertEquals(PhotoEditIntent.Kind.RESTYLE, k("photo ko cartoon karo"))
        assertEquals(PhotoEditIntent.Kind.REMOVE_OBJECT, k("is photo se aadmi hatao"))
    }
    @Test fun doesNotStealOtherSentences() {
        assertNull(k("ek billi ki photo banao"))
        assertNull(k("ye photo whatsapp par bhejo"))
        assertNull(k("camera kholo photo kheencho"))
        assertNull(k("restaurant ki website banao photos ke saath"))
        assertNull(k("aaj ka mausam batao"))
        assertNull(k(""))
    }
    @Test fun blockedWords() { assertTrue(PhotoEditIntent.parse("is photo ko nude professional banao") == null || PhotoEditIntent.parse("is photo ko nude professional banao")!!.blocked) ; assertNotNull(PhotoEditIntent.parse("is photo ko nsfw edit karo")); assertTrue(PhotoEditIntent.parse("is photo ko nsfw edit karo")!!.blocked) }
    @Test fun v712BanaoNeedsObjectMarker() { assertNull(k("professional photo banao")); assertNull(k("ek sundar photo banao")); assertEquals(PhotoEditIntent.Kind.ENHANCE, k("is photo ko sundar banao")) }
}
