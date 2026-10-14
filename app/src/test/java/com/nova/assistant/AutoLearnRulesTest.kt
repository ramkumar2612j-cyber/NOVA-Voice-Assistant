package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLearnRulesTest {
    private val goodAnswer = "Photosynthesis is how plants turn sunlight, water and carbon dioxide into sugar and oxygen. It happens in the chloroplasts."

    @Test fun plainFactualQuestionIsLearned() {
        val l = AutoLearnRules.shouldLearn("nova photosynthesis kya hota hai", goodAnswer)
        assertNotNull(l)
        assertTrue(l!!.topic.contains("photosynthesis"))
        assertTrue(l.note.length >= 20)
    }

    @Test fun commandsAreNeverLearned() {
        assertNull(AutoLearnRules.shouldLearn("nova youtube kholo kya chal raha hai", goodAnswer))
        assertNull(AutoLearnRules.shouldLearn("what is the best way to send money", goodAnswer))
    }

    @Test fun personalAndSensitiveAreNeverLearned() {
        assertNull(AutoLearnRules.shouldLearn("mera bank account kya hai", goodAnswer))
        assertNull(AutoLearnRules.shouldLearn("what is my otp for login", goodAnswer))
        assertNull(AutoLearnRules.shouldLearn("what is 123456 plus something", goodAnswer))
        assertNull(AutoLearnRules.shouldLearn("what is a@b.com about", goodAnswer))
    }

    @Test fun liveQuestionsAreNeverLearned() {
        assertNull(AutoLearnRules.shouldLearn("aaj ka mausam kaisa hai", goodAnswer))
        assertNull(AutoLearnRules.shouldLearn("what is the gold price now", goodAnswer))
        assertNull(AutoLearnRules.shouldLearn("who won the match", goodAnswer))
    }

    @Test fun notAQuestionIsNotLearned() {
        assertNull(AutoLearnRules.shouldLearn("photosynthesis plants sunlight energy", goodAnswer))
    }

    @Test fun tooShortOrTooLongIsNotLearned() {
        assertNull(AutoLearnRules.shouldLearn("kya hai", goodAnswer))
        assertNull(AutoLearnRules.shouldLearn("what is " + "word ".repeat(30), goodAnswer))
    }

    @Test fun failureAndOfflineTextsAreNotLearned() {
        assertNull(AutoLearnRules.shouldLearn("photosynthesis kya hota hai", "Abhi jawab nahi mil paya, thodi der baad dobara bolo."))
        assertNull(AutoLearnRules.shouldLearn("photosynthesis kya hota hai", "Offline jawab: plants make food from sunlight and water."))
        assertNull(AutoLearnRules.shouldLearn("photosynthesis kya hota hai", "short"))
    }

    @Test fun duplicateTopicsAreDetected() {
        assertTrue(AutoLearnRules.duplicate("photosynthesis", listOf("solar panel", "photosynthesis")))
        assertFalse(AutoLearnRules.duplicate("black holes", listOf("solar panel", "photosynthesis")))
        assertFalse(AutoLearnRules.duplicate("photosynthesis", emptyList()))
    }

    @Test fun oldestAutoNotesAreDroppedFirst() {
        val ids = (1..105).map { "n$it" }
        val drop = AutoLearnRules.overflow(ids, 100)
        assertEquals(listOf("n1", "n2", "n3", "n4", "n5"), drop)
        assertTrue(AutoLearnRules.overflow(ids.take(100), 100).isEmpty())
    }

    @Test fun autoNotesNeedABetterMatchThanKeptNotes() {
        assertTrue(AutoLearnRules.minScoreFor("auto") > AutoLearnRules.minScoreFor("web"))
        assertEquals(LearnRules.MIN_SCORE, AutoLearnRules.minScoreFor("ai"), 0.0)
    }
}
