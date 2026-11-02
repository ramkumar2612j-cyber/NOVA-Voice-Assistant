package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class MusicPlayRulesTest {
    @Test fun ownerSentenceFromPhone() {
        val r = MusicPlayRules.parse("spotify open kro or legacy song play kro")!!
        assertEquals("spotify", r.appKey)
        assertEquals("legacy", r.query)
    }
    @Test fun hindiForm() {
        val r = MusicPlayRules.parse("NOVA spotify kholo aur Kesariya gana chalao")!!
        assertEquals("kesariya", r.query)
    }
    @Test fun youtubeMusicBeatsPlainWords() {
        val r = MusicPlayRules.parse("youtube music par arijit singh ke gaane bajao")!!
        assertEquals("ytmusic", r.appKey)
        assertEquals("arijit singh", r.query)
    }
    @Test fun noSongMeansNoMatch() {
        assertNull(MusicPlayRules.parse("spotify kholo"))
        assertNull(MusicPlayRules.parse("spotify play karo"))
        assertNull(MusicPlayRules.parse("battery kitni hai"))
    }

    // ---- wip13 ----
    private val kes = listOf(
        MusicCatalog.Track("Kesariya", "Arijit Singh"),
        MusicCatalog.Track("Kesariya (Reprise)", "Arijit Singh"),
        MusicCatalog.Track("Kesariya Tera", "Other Singer"),
        MusicCatalog.Track("Kesariya", "Arijit Singh, Pritam"),
        MusicCatalog.Track("Totally Different", "Nobody")
    )

    @Test fun similarSongsGiveChoices() {
        val c = MusicPlayRules.choices("kesariya", kes)
        assertTrue(c.size >= 2)
        assertTrue(c.none { it.title == "Totally Different" })
        assertEquals(c.size, c.map { it.label }.toSet().size)
    }
    @Test fun singleMatchGivesNoChoice() {
        assertTrue(MusicPlayRules.choices("legacy", listOf(MusicCatalog.Track("Legacy", "Eminem"))).isEmpty())
        assertTrue(MusicPlayRules.choices("kesariya", emptyList()).isEmpty())
    }
    @Test fun spokenPick() {
        val labels = listOf("Kesariya — Arijit Singh", "Kesariya Tera — Other Singer")
        assertEquals(1, MusicPlayRules.matchSpoken("doosra wala", labels))
        assertEquals(0, MusicPlayRules.matchSpoken("1", labels))
        assertEquals(1, MusicPlayRules.matchSpoken("tera", labels))
        assertNull(MusicPlayRules.matchSpoken("kesariya", labels))
        assertNull(MusicPlayRules.matchSpoken("5", labels))
    }
    @Test fun judgeNeverClaimsPlayingWithoutProof() {
        assertEquals(MusicPlayRules.Outcome.VERIFIED_MATCH, MusicPlayRules.judge(false, true, true, "Kesariya", "Arijit Singh", "kesariya"))
        assertEquals(MusicPlayRules.Outcome.PLAYING_OTHER, MusicPlayRules.judge(false, true, true, "Blinding Lights", "The Weeknd", "kesariya"))
        assertEquals(MusicPlayRules.Outcome.NOT_PLAYING, MusicPlayRules.judge(false, false, false, "", "", "kesariya"))
        assertEquals(MusicPlayRules.Outcome.NOT_PLAYING, MusicPlayRules.judge(false, false, null, "", "", "kesariya"))
        assertEquals(MusicPlayRules.Outcome.PLAYING_UNCONFIRMED, MusicPlayRules.judge(false, true, null, "", "", "kesariya"))
        assertEquals(MusicPlayRules.Outcome.CANNOT_TELL, MusicPlayRules.judge(true, true, null, "", "", "kesariya"))
        assertEquals(MusicPlayRules.Outcome.NOT_PLAYING, MusicPlayRules.judge(true, true, false, "", "", "kesariya"))
    }
    @Test fun alternativesExcludeCurrentAndTried() {
        val o = MusicPlayRules.others("spotify", setOf("ytmusic"))
        assertTrue(o.none { it.key == "spotify" || it.key == "ytmusic" })
        assertTrue(o.isNotEmpty())
    }
    @Test fun explicitAppWinsAndStaysSeparate() {
        assertEquals("spotify", MusicPlayRules.parse("spotify par kesariya play karo")!!.appKey)
        assertEquals("ytmusic", MusicPlayRules.parse("youtube music par kesariya play karo")!!.appKey)
        assertNull(MusicPlayRules.parse("whatsapp par kesariya bhejo"))
    }
}
