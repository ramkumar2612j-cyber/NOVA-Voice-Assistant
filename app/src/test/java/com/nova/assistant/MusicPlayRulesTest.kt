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
}
