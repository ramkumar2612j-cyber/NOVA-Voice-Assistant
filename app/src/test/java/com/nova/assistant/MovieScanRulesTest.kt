package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.63 PART 14B-1: the sentence "kon si movie hai" is understood; people / shared-file / play / recommend sentences are not. */
class MovieScanRulesTest {
    private val M = MovieScanRules.Ask.MOVIE
    private val N = MovieScanRules.Ask.NONE

    @Test fun movieQuestionsMatch() {
        for (s in listOf("nova ye kon si movie hai", "ye kaun si film hai", "ye reel kon si movie hai", "nova ye batao kon si movie hai", "is movie ka naam batao",
            "ye kaunsi movie ki reel hai", "which movie is this", "what film is this", "name this movie", "ye kis film ka scene hai", "ye konsa serial hai", "ye kaun sa anime hai",
            "नोवा ये कौन सी फिल्म है", "ye movie ka naam kya hai"))
            assertEquals(s, M, MovieScanRules.parse(s))
    }

    @Test fun peopleQuestionsDoNotMatch() {
        for (s in listOf("is movie ka hero kaun hai", "movie me kaun hai", "ye actor kaun hai", "ye kaun hai", "isme kaun hai", "who is in this movie", "is film ki heroine ka naam batao", "cast batao is movie ka"))
            assertEquals(s, N, MovieScanRules.parse(s))
    }

    @Test fun playRecommendAndSharedDoNotMatch() {
        for (s in listOf("konsi movie dekhu", "aaj kaun si movie dekhni chahiye", "movie chalao", "best movie kaun si hai", "naya movie kaun sa aaya", "movie download karo",
            "share ki hui movie ka naam batao", "gallery wali film kon si hai", "nova ye video analyse karo", "battery kitni hai", "kon si movie dekhe", ""))
            assertEquals(s, N, MovieScanRules.parse(s))
        assertEquals(N, MovieScanRules.parse(null))
        assertEquals(N, MovieScanRules.parse("ye kon si movie hai ".repeat(10)))
    }

    @Test fun faceGateStillRefusesPeopleAndDoesNotBlockMovieName() {
        assertEquals(AnalysisRules.Gate.OK, AnalysisRules.gateQuestion("ye reel kon si movie hai"))
        assertEquals(AnalysisRules.Gate.OK, AnalysisRules.gateQuestion("ye kaun si film hai"))
        assertEquals(AnalysisRules.Gate.REFUSE_FACE_ID, AnalysisRules.gateQuestion("ye kaun hai"))
        assertEquals(AnalysisRules.Gate.REFUSE_FACE_ID, AnalysisRules.gateQuestion("who is this person"))
    }

    @Test fun movieSentenceWouldHaveBeenAnAnalysisAskSoOrderMatters() {
        // proves why MovieScanRules must run first in NovaService
        assertEquals(AnalysisVoiceRules.Ask.ANALYSE, AnalysisVoiceRules.parse("ye reel kon si movie hai"))
        assertEquals(M, MovieScanRules.parse("ye reel kon si movie hai"))
    }

    @Test fun honestTextNeverNamesAMovieOrSaysStarted() {
        for (en in listOf(false, true)) {
            val x = MovieScanRules.notYetText(en)
            assertTrue(x.contains("14B"))
            assertFalse(x.lowercase().contains("scan shuru"))
            assertFalse(x.lowercase().contains("scan started"))
        }
        assertTrue(MovieScanRules.mentionsScreen("screen par ye kon si movie hai"))
        assertFalse(MovieScanRules.mentionsScreen("ye kon si movie hai"))
    }
}
