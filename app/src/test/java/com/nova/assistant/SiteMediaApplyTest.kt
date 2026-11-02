package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.03 G: SiteMedia.userBlock / applyUser are pure (no android classes). */
class SiteMediaApplyTest {
    private fun item(id: String, kind: String, place: MediaPlace.Place?, uri: String = "data:$kind/x;base64,QUJD") =
        SiteMediaStore.Item(id, kind, uri, "", 0L, place)

    @Test fun noItemsIsNoOp() {
        val h = "<html><body>hi</body></html>"
        val o = SiteMedia.applyUser(h, emptyList())
        assertEquals(h, o.html); assertTrue(o.placed.isEmpty()); assertEquals("", SiteMedia.userBlock(emptyList()))
    }

    @Test fun imgPlaceholderGetsData() {
        val o = SiteMedia.applyUser("<body><img src=\"NOVA_USER_MEDIA:m1\" alt=\"x\"></body>", listOf(item("m1", "image", MediaPlace.Place.HERO)))
        assertTrue(o.html.contains("src=\"data:image/x;base64,QUJD\""))
        assertFalse(o.html.contains("NOVA_USER_MEDIA"))
        assertTrue(o.missing.isEmpty())
    }

    @Test fun imgForVideoItemBecomesVideoTag() {
        val o = SiteMedia.applyUser("<body><img src=\"NOVA_USER_MEDIA:m2\" alt=\"v\"></body>", listOf(item("m2", "video", MediaPlace.Place.GALLERY)))
        assertTrue(o.html.contains("<video src=\"data:video/x;base64,QUJD\""))
        assertFalse(o.html.contains("<img"))
        assertFalse(o.html.contains("NOVA_USER_MEDIA"))
    }

    @Test fun unreferencedHeroIsInjectedAfterHeader() {
        val o = SiteMedia.applyUser("<html><body><header>H</header><main>M</main></body></html>", listOf(item("m1", "image", MediaPlace.Place.HERO)))
        assertEquals(listOf("m1"), o.missing)
        assertTrue(o.html.indexOf("data:image/x") > o.html.indexOf("</header>"))
        assertTrue(o.html.indexOf("data:image/x") < o.html.indexOf("<main>"))
        assertFalse(o.html.contains("NOVA_USER_MEDIA"))
    }

    @Test fun unreferencedWithoutPlaceGoesBeforeBodyEnd() {
        val o = SiteMedia.applyUser("<html><body><p>a</p></body></html>", listOf(item("m3", "image", null)))
        assertTrue(o.html.indexOf("data:image/x") < o.html.indexOf("</body>"))
        assertTrue(o.html.contains("<section"))
    }

    @Test fun logoAndBackgroundAreInjected() {
        val h = "<html><head></head><body><p>a</p></body></html>"
        val o = SiteMedia.applyUser(h, listOf(item("m1", "image", MediaPlace.Place.LOGO), item("m2", "image", MediaPlace.Place.BACKGROUND)))
        assertTrue(o.html.contains("background-image:url('data:image/x;base64,QUJD')"))
        assertTrue(o.html.indexOf("height:56px") in 0 until o.html.indexOf("<p>a</p>"))
        assertFalse(o.html.contains("NOVA_USER_MEDIA"))
    }

    @Test fun dollarInDataIsSafe() {
        val o = SiteMedia.applyUser("<body><img src=\"NOVA_USER_MEDIA:m1\"></body>", listOf(item("m1", "image", null, "data:image/jpeg;base64,A\$1B")))
        assertTrue(o.html.contains("A\$1B"))
    }

    @Test fun promptBlockListsEveryItem() {
        val b = SiteMedia.userBlock(listOf(item("m1", "image", MediaPlace.Place.HERO), item("m2", "video", null)))
        assertTrue(b.contains("NOVA_USER_MEDIA:"))
        assertTrue(b.contains("m1 = image, place: hero"))
        assertTrue(b.contains("m2 = video"))
    }
}
