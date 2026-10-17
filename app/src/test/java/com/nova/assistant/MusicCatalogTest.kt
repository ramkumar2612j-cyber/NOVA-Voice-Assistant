package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class MusicCatalogTest {
    @Test fun parsesTitleAndArtist() {
        val j = """{"resultCount":2,"results":[{"trackName":"Kesariya","artistName":"Arijit Singh"},{"trackName":"","artistName":"x"},{"trackName":"Legacy","artistName":"Eminem"}]}"""
        val t = MusicCatalog.parse(j)
        assertEquals(2, t.size)
        assertEquals("Kesariya — Arijit Singh", t[0].label)
    }
    @Test fun brokenJsonGivesEmptyList() {
        assertTrue(MusicCatalog.parse("not json").isEmpty())
        assertTrue(MusicCatalog.parse("{}").isEmpty())
    }
}
