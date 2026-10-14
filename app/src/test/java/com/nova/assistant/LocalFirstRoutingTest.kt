package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class LocalFirstRoutingTest {
    private val SIMPLE = "tum kaun ho"      // short, not live, not heavy; "tum" also makes it small talk
    private val PLAIN = "sooraj kya hai"    // short, not small talk, not live, not heavy
    private val LIVE_Q = "bitcoin price today"
    private val HEAVY = "ek email likho boss ko"

    @Test fun defaultBehaviourIsUnchanged() {
        assertEquals(listOf(AiRouter.Step.CLOUD, AiRouter.Step.LOCAL), AiRouter.plan(true, false, true, PLAIN))
        assertEquals(listOf(AiRouter.Step.LOCAL, AiRouter.Step.CLOUD), AiRouter.plan(true, false, true, "hello"))
    }

    @Test fun localFirstLetsTheOnDeviceAiLeadOnShortSimpleQuestions() {
        assertEquals(listOf(AiRouter.Step.LOCAL, AiRouter.Step.CLOUD), AiRouter.plan(true, false, true, PLAIN, true))
        assertEquals(listOf(AiRouter.Step.LOCAL, AiRouter.Step.CLOUD), AiRouter.plan(true, false, true, SIMPLE, true))
    }

    @Test fun localFirstNeverTakesLiveOrHeavyQuestions() {
        assertEquals(listOf(AiRouter.Step.CLOUD, AiRouter.Step.LOCAL), AiRouter.plan(true, false, true, LIVE_Q, true))
        assertEquals(listOf(AiRouter.Step.CLOUD, AiRouter.Step.LOCAL), AiRouter.plan(true, false, true, HEAVY, true))
    }

    @Test fun localFirstNeedsAReadyLocalModel() {
        assertEquals(listOf(AiRouter.Step.CLOUD), AiRouter.plan(true, false, false, PLAIN, true))
        assertTrue(AiRouter.plan(false, false, false, PLAIN, true).isEmpty())
    }

    @Test fun localFirstWithoutCloudStillAnswersLocally() {
        assertEquals(listOf(AiRouter.Step.LOCAL), AiRouter.plan(false, false, true, PLAIN, true))
        assertEquals(listOf(AiRouter.Step.LOCAL), AiRouter.plan(true, true, true, PLAIN, true))
    }

    @Test fun anAdmittedDontKnowIsUnsureSoTheCloudTakesOver() {
        assertTrue(LocalChat.isUnsure("Mujhe pakka nahi pata"))
        assertTrue(LocalChat.isUnsure("I do not know that"))
        assertTrue(LocalChat.isUnsure(""))
        assertFalse(LocalChat.isUnsure("Sooraj ek taara hai jo dharti ko roshni deta hai."))
    }

    @Test fun liveQuestionsAreRecognisedAndSimpleOnesAreNot() {
        assertTrue(LocalChat.needsLive("aaj ka news"))
        assertTrue(LocalChat.needsLive("bitcoin price"))
        assertFalse(LocalChat.needsLive("tum kaun ho"))
    }

    @Test fun onlineTimeoutIsShorterThanTheOfflineOne() {
        assertTrue(LocalChat.ONLINE_TIMEOUT_MS < LocalChat.TIMEOUT_MS)
        assertEquals(9000L, LocalChat.ONLINE_TIMEOUT_MS)
    }
}
