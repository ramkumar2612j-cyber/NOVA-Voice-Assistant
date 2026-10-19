package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.59 Part 13D: voice / typed analysis requests and chat-photo questions. Expected values worked out by hand. */
class AnalysisVoiceRulesTest {
    private val A = AnalysisVoiceRules.Ask.ANALYSE
    private val C = AnalysisVoiceRules.Ask.CHECK
    private val N = AnalysisVoiceRules.Ask.NONE

    @Test fun analyseSentences() {
        assertEquals(A, AnalysisVoiceRules.parse("nova ye video analyse karo"))
        assertEquals(A, AnalysisVoiceRules.parse("is reel ko analyze karo"))
        assertEquals(A, AnalysisVoiceRules.parse("media analysis karo"))
        assertEquals(A, AnalysisVoiceRules.parse("is photo me kya hai"))
        assertEquals(A, AnalysisVoiceRules.parse("ye image kaun hai"))
        assertEquals(A, AnalysisVoiceRules.parse("is video ko check karo"))
        assertEquals(A, AnalysisVoiceRules.parse("what is in this photo"))
    }

    @Test fun checkSentenceNeedsNoMediaWord() {
        assertEquals(C, AnalysisVoiceRules.parse("isko check karo ki sach hai ya nahi"))
        assertEquals(C, AnalysisVoiceRules.parse("ye sach hai ya fake check karo"))
    }

    @Test fun everythingElseIsNone() {
        assertEquals(N, AnalysisVoiceRules.parse(""))
        assertEquals(N, AnalysisVoiceRules.parse(null))
        assertEquals(N, AnalysisVoiceRules.parse("reel kya hai"))                       // general question, no "ye / is"
        assertEquals(N, AnalysisVoiceRules.parse("screen analyse karo"))                // screen belongs to the screen feature
        assertEquals(N, AnalysisVoiceRules.parse("is screen par kya hai"))
        assertEquals(N, AnalysisVoiceRules.parse("nova kya mila"))
        assertEquals(N, AnalysisVoiceRules.parse("ye video kya mila"))                  // status question stays with MediaIntake
        assertEquals(N, AnalysisVoiceRules.parse("mummy ko call karo"))
        assertEquals(N, AnalysisVoiceRules.parse("ye photo analyse karo aur phir uske baad sabko bhej do ok ab jaldi"))   // 13 words
    }

    @Test fun parseCoversEveryOldNotYetSentence() {
        // the old MediaIntake.Voice.ANALYSE_NOT_YET sentences must all reach the real analysis now
        for (s in listOf("nova ye video analyse karo", "is reel ko analyze karo", "media analysis karo", "clip vishleshan karo"))
            assertEquals(s, A, AnalysisVoiceRules.parse(s))
    }

    @Test fun questionPart() {
        assertEquals("", AnalysisVoiceRules.question("nova ye video analyse karo", A, false))
        assertEquals("", AnalysisVoiceRules.question("is photo me kya hai", A, false))
        assertEquals("Is video me kaun hai", AnalysisVoiceRules.question("Is video me kaun hai", A, false))   // kept so the gate can refuse it
        assertTrue(AnalysisVoiceRules.question("isko check karo ki sach hai ya nahi", C, false).startsWith("Kya ye sach hai"))
        assertTrue(AnalysisVoiceRules.question("x", C, true).startsWith("Is this true or fake"))
        assertEquals("", AnalysisVoiceRules.question("kuch bhi", N, false))
    }

    @Test fun whoQuestionIsRefusedByTheGate() {
        val q = AnalysisVoiceRules.question("is video me kaun hai", A, false)
        assertEquals(AnalysisRules.Gate.REFUSE_FACE_ID, AnalysisRules.gateQuestion(q))
    }

    private fun e(id: String, kind: MediaIntake.Kind, t: Long, name: String = "n") = MediaIntake.Entry(id, kind, name, 10L, t, id + ".bin", "")

    @Test fun newestPhotoOrVideoIsPicked() {
        val l = listOf(e("a", MediaIntake.Kind.IMAGE, 100), e("b", MediaIntake.Kind.VIDEO, 300), e("c", MediaIntake.Kind.LINK, 900), e("d", MediaIntake.Kind.IMAGE, 200))
        assertEquals("b", AnalysisVoiceRules.pickTarget(l)!!.id)                       // the link is never picked
        assertNull(AnalysisVoiceRules.pickTarget(listOf(e("c", MediaIntake.Kind.LINK, 5))))
        assertNull(AnalysisVoiceRules.pickTarget(emptyList()))
    }

    @Test fun textsNameTheFileAndStayHonest() {
        val v = e("b", MediaIntake.Kind.VIDEO, 1, "reel.mp4")
        assertTrue(AnalysisVoiceRules.startedText(v, false).contains("reel.mp4"))
        assertTrue(AnalysisVoiceRules.startedText(v, false).contains("STOP"))
        assertTrue(AnalysisVoiceRules.startedText(v, true).contains("newest video"))
        assertTrue(AnalysisVoiceRules.noMediaText(false).startsWith("Pehle video ya photo share karo"))
        assertTrue(AnalysisVoiceRules.noMediaText(true).startsWith("Share a video or photo first"))
    }

    @Test fun chatPhotoQuestionDetection() {
        assertTrue(AnalysisVoiceRules.isPhotoQuestion("ye kya hai?"))
        assertTrue(AnalysisVoiceRules.isPhotoQuestion("is photo me likha hua padho"))
        assertTrue(AnalysisVoiceRules.isPhotoQuestion("kya ye sach hai"))
        assertTrue(AnalysisVoiceRules.isPhotoQuestion("what is this"))
        assertFalse(AnalysisVoiceRules.isPhotoQuestion("thanks"))
        assertFalse(AnalysisVoiceRules.isPhotoQuestion("ok"))
        assertFalse(AnalysisVoiceRules.isPhotoQuestion(""))
        assertFalse(AnalysisVoiceRules.isPhotoQuestion(null))
        assertFalse(AnalysisVoiceRules.isPhotoQuestion("a".repeat(500)))
    }

    @Test fun askTextsMentionPrivacy() {
        assertTrue(AnalysisVoiceRules.chatPhotoAskMessage(false).contains("internet"))
        assertTrue(AnalysisVoiceRules.chatPhotoAskMessage(false).contains("sirf chhoti text query"))
        assertNotNull(AnalysisVoiceRules.chatPhotoAskTitle(true))
        assertTrue(AnalysisVoiceRules.noPhotoText(false).contains("photo"))
    }

    // v7.61 add-only: face-gate must also catch "ye image kaun hai" style questions
    @Test fun faceGateCatchesImageWhoQuestions() {
        val F = AnalysisRules.Gate.REFUSE_FACE_ID
        assertEquals(F, AnalysisRules.gateQuestion("ye image kaun hai"))
        assertEquals(F, AnalysisRules.gateQuestion("isme kaun hai"))
        assertEquals(F, AnalysisRules.gateQuestion("who is in this reel"))
        assertEquals(AnalysisRules.Gate.OK, AnalysisRules.gateQuestion("is photo me kya hai"))
    }
}
