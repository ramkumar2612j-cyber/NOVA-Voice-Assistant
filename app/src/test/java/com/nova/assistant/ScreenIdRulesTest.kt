package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.nova.assistant.ScreenIdRules as S

class ScreenIdRulesTest {
    @Test fun gameQuestionsAreGame() {
        for (x in listOf("nova ye kaun sa game hai", "ye game ka naam batao", "is game ka name kya hai", "screen par jo game chal raha hai uska naam batao", "ye konsa game hai", "ye game kya hai", "ye kya game hai", "ये कौन सा गेम है", "इस गेम का नाम बताओ"))
            assertEquals(x, S.Kind.GAME, S.parse(x))
    }

    @Test fun videoQuestionsAreVideo() {
        for (x in listOf("nova ye video ka naam batao", "is clip ka title kya hai", "ye reel konsi hai naam batao", "ye video ka name bata"))
            assertEquals(x, S.Kind.VIDEO, S.parse(x))
    }

    @Test fun recommendationPlayAndMoneySentencesAreNotMatched() {
        for (x in listOf("konsa game khelu", "ye game download karo", "best game ka naam batao", "ye game install karo", "is game ka hack batao", "game khelo", "ye game delete karo"))
            assertNull(x, S.parse(x))
    }

    @Test fun peopleQuestionsAndSharedFilesAreNotMatched() {
        for (x in listOf("is video me kaun hai", "ye kon hai video me", "ye player kaun hai game me", "is video ka actor kaun hai", "share kiye video ka naam batao", "ye streamer ka naam batao"))
            assertNull(x, S.parse(x))
    }

    @Test fun moviesAndSeriesStayWithMovieScanAndBareSentencesAreNotMatched() {
        assertNull(S.parse("ye movie ka naam batao"))
        assertNull(S.parse("game ka naam batao"))                 // no "this" word: not about the screen
        assertNull(S.parse("ye game video ka naam batao"))        // two kinds at once
        assertNull(S.parse("reel me kya chal raha hai"))
        assertNull(S.parse("is game me kya hai"))                 // what is inside, not the name          // handled by ScreenVideoRules (no name asked)
        assertNull(S.parse(""))
        assertNull(S.parse(null))
        assertEquals(S.Kind.GAME, S.parse("ye game kaun sa hai"))
    }

    @Test fun gameFromAppNeedsAnAndroidGameAndACleanName() {
        assertEquals("Subway Surfers", S.gameFromApp("Subway Surfers", true))
        assertNull(S.gameFromApp("Subway Surfers", false))
        assertNull(S.gameFromApp("", true))
        assertNull(S.gameFromApp(null, true))
        assertNull(S.gameFromApp("https://x.com/game", true))
    }

    @Test fun evidenceHeaderHasAppAndWindowButNoAddress() {
        assertEquals("APP: Ludo King\nWINDOW: Ludo Lobby", S.evidenceHeader("Ludo King", "Ludo Lobby"))
        assertEquals("APP: YouTube", S.evidenceHeader("YouTube", "youtube"))
        assertEquals("APP: YouTube", S.evidenceHeader("YouTube", "https://evil.example"))
        assertEquals("", S.evidenceHeader(null, null))
    }

    @Test fun promptsForbidFacesAndKeepTheJsonShape() {
        for (k in S.Kind.values()) for (en in listOf(true, false)) {
            val p = S.systemPrompt(k, en)
            assertTrue(p.contains("NEVER identify a real person"))
            assertTrue(p.contains("\"candidates\""))
            assertTrue(p.contains("untrusted"))
            assertTrue(p.contains("APP:"))
        }
        assertTrue(S.systemPrompt(S.Kind.GAME, true).contains("NAME OF THE GAME"))
        assertTrue(S.systemPrompt(S.Kind.VIDEO, true).contains("TITLE of the video"))
        assertTrue(S.userPrompt(S.Kind.GAME, "APP: X", 3, true).contains("SCREEN TEXT (untrusted"))
        assertFalse(S.userPrompt(S.Kind.GAME, "", 3, true).contains("SCREEN TEXT"))
    }

    @Test fun searchQueriesUseTheKindWord() {
        val c = listOf(MovieGuessRules.Candidate("Ludo King", "", "both", listOf("logo")))
        assertTrue(S.searchQueries(S.Kind.GAME, c).all { it.contains("game", true) })
        assertTrue(S.searchQueries(S.Kind.VIDEO, c).all { it.contains("video", true) })
    }

    private fun out(l: MovieGuessRules.Level, searched: Boolean = true) =
        MovieGuessRules.Outcome("Ludo King", "", l, emptyList(), listOf("logo on screen"), searched)

    @Test fun lowConfidenceIsAlwaysSaidAsNotVerified() {
        val low = S.resultText(S.Kind.GAME, out(MovieGuessRules.Level.LOW), false)
        assertTrue(low.contains("verify nahi hua"))
        assertTrue(S.card(S.Kind.GAME, out(MovieGuessRules.Level.LOW), false).contains("verify nahi hua"))
        assertTrue(S.speech(S.Kind.VIDEO, out(MovieGuessRules.Level.LOW), false).contains("verify nahi hua"))
        assertTrue(S.resultText(S.Kind.VIDEO, out(MovieGuessRules.Level.LOW, searched = false), false).contains("verify nahi hua"))
        assertFalse(S.resultText(S.Kind.GAME, out(MovieGuessRules.Level.HIGH), false).contains("verify nahi hua"))
    }

    @Test fun noNameIsHonestAndNeverGuesses() {
        assertTrue(S.resultText(S.Kind.GAME, null, false).contains("Koi andaza nahi lagaunga"))
        assertTrue(S.resultText(S.Kind.VIDEO, null, true).contains("I will not guess"))
        assertEquals("game scan: no name", S.historyLine(S.Kind.GAME, null))
    }

    @Test fun gameAnswerFromAppSaysItNeverPlays() {
        assertTrue(S.gameFromAppText("Ludo King", false).contains("khelta nahi"))
        assertTrue(S.gameFromAppText("Ludo King", true).contains("never play"))
        assertTrue(S.startedText(S.Kind.GAME, false).contains("khelta nahi"))
    }
}
