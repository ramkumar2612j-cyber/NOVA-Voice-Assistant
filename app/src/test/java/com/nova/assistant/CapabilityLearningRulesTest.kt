package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class CapabilityLearningRulesTest {
    @Test fun classifiesCommonBlockersWithoutSavingRawText() {
        assertEquals(CapabilityLearningRules.Gap.ACCESSIBILITY, CapabilityLearningRules.classify("Screen nahi padh pa raha"))
        assertEquals(CapabilityLearningRules.Gap.PROVIDER, CapabilityLearningRules.classify("API key quota failed"))
        assertEquals(CapabilityLearningRules.Gap.NETWORK, CapabilityLearningRules.classify("Internet connection offline"))
        assertEquals(CapabilityLearningRules.Gap.STUCK, CapabilityLearningRules.classify("Screen par kuch badal nahi raha, atak gaya"))
    }

    @Test fun repeatsIncreaseCountAndStayBounded() {
        val one = CapabilityLearningRules.record(emptyList(), CapabilityLearningRules.Gap.STUCK, 100L)
        val two = CapabilityLearningRules.record(one, CapabilityLearningRules.Gap.STUCK, 200L)
        assertEquals(2, two.single().count)
        assertEquals(200L, two.single().lastAtMs)
        assertTrue(CapabilityLearningRules.record(two, CapabilityLearningRules.Gap.NETWORK, 300L).size <= CapabilityLearningRules.MAX_RECORDS)
    }

    @Test fun promptAdviceNeverClaimsUnlimitedCapabilityOrBypassesSafety() {
        val lessons = CapabilityLearningRules.record(emptyList(), CapabilityLearningRules.Gap.PERMISSION, 100L)
        val prompt = CapabilityLearningRules.promptGuidance(lessons)
        assertTrue(prompt.contains("permission"))
        assertTrue(prompt.contains("owner"))
        assertTrue(prompt.length <= CapabilityLearningRules.MAX_GUIDANCE_CHARS)
    }
}
