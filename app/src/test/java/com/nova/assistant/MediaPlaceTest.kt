package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.03 G: pure tests for MediaPlace (where a picked gallery file goes) and SiteMediaStore (memory list, max 6). */
class MediaPlaceTest {
    @Test fun parsesPlaces() {
        assertEquals(MediaPlace.Place.HERO, MediaPlace.parse("ye photo hero mein lagao"))
        assertEquals(MediaPlace.Place.GALLERY, MediaPlace.parse("dusri photo gallery mein daalo"))
        assertEquals(MediaPlace.Place.LOGO, MediaPlace.parse("isko logo banao header mein"))
        assertEquals(MediaPlace.Place.BACKGROUND, MediaPlace.parse("video background mein lagao"))
        assertNull(MediaPlace.parse("ek restaurant ki website banao"))
    }

    @Test fun itemNumbers() {
        assertEquals(1, MediaPlace.itemIndex("pehli photo hero mein"))
        assertEquals(2, MediaPlace.itemIndex("dusri photo gallery mein"))
        assertEquals(3, MediaPlace.itemIndex("m3 logo banao"))
        assertEquals(-1, MediaPlace.itemIndex("last photo footer mein"))
        assertNull(MediaPlace.itemIndex("ye photo hero mein"))
    }

    @Test fun placeCommandNeedsPlaceVerbAndMedia() {
        assertTrue(MediaPlace.isPlaceCommand("ye photo hero mein lagao"))
        assertTrue(MediaPlace.isPlaceCommand("m2 gallery mein rakho"))
        assertFalse(MediaPlace.isPlaceCommand("hero mein"))
        assertFalse(MediaPlace.isPlaceCommand("photo lagao"))
        assertFalse(MediaPlace.isPlaceCommand("aaj ka mausam batao"))
        assertFalse(MediaPlace.isPlaceCommand(""))
    }

    @Test fun storeAddsMax6AndAppliesText() {
        SiteMediaStore.clear()
        val now = 1_000_000L
        for (n in 1..6) assertEquals("m$n", SiteMediaStore.add("image", "data:image/jpeg;base64,AA", "", now))
        assertNull(SiteMediaStore.add("image", "data:image/jpeg;base64,AA", "", now))
        assertNull(SiteMediaStore.add("audio", "x", "", now))
        assertEquals(1, SiteMediaStore.applyText("dusri photo gallery mein lagao", now))
        assertEquals(MediaPlace.Place.GALLERY, SiteMediaStore.items(now)[1].place)
        assertEquals(1, SiteMediaStore.applyText("ye photo hero mein lagao", now))   // newest item without a place = m6
        assertEquals(MediaPlace.Place.HERO, SiteMediaStore.items(now)[5].place)
        SiteMediaStore.clear()
        assertEquals(0, SiteMediaStore.items(now).size)
    }

    @Test fun storeForgetsAfterSixHours() {
        SiteMediaStore.clear()
        SiteMediaStore.add("image", "data:image/jpeg;base64,AA", "", 0L)
        assertEquals(0, SiteMediaStore.items(7 * 60 * 60_000L).size)
        SiteMediaStore.clear()
    }

    @Test fun storeRefusesTooBigTotal() {
        SiteMediaStore.clear()
        val big = "x".repeat(SiteMediaStore.MAX_TOTAL_CHARS - 10)
        assertEquals("m1", SiteMediaStore.add("video", big, "", 5L))
        assertNull(SiteMediaStore.add("image", "y".repeat(100), "", 5L))
        SiteMediaStore.clear()
    }
}
