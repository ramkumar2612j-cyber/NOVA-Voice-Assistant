package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicPlayRulesPlusTest {
    @Test fun nameAndNoiseWordsAreNotSearched() {
        val r = MusicPlayRulesPlus.parseBest("spotify open kr ke song play kro song ka naam tum hi ho bta diya")!!
        assertEquals("spotify", r.appKey)
        assertEquals("tum hi ho", r.query)
    }
    @Test fun kplayTypo() {
        val r = MusicPlayRulesPlus.parseBest("spotify open karke kplay kro kesariya")!!
        assertEquals("kesariya", r.query)
    }
    @Test fun noSongMeansNoSearch() {
        assertNull(MusicPlayRulesPlus.parseBest("spotify open kr ke song play kro"))
    }
    @Test fun oldBehaviourKept() {
        assertEquals("legacy", MusicPlayRulesPlus.parseBest("spotify open kro or legacy song play kro")!!.query)
        assertNull(MusicPlayRulesPlus.parseBest("battery kitni hai"))
    }
}
