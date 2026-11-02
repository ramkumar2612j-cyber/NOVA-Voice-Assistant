package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainChoiceRulesTest {
    private val gb = 1024L * 1024L * 1024L

    @Test fun recommendationFollowsRam() {
        assertEquals("qwen25-3b", BrainChoiceRules.recommendedId(8000))
        assertEquals("qwen25-3b", BrainChoiceRules.recommendedId(12000))
        assertEquals("qwen25-15b", BrainChoiceRules.recommendedId(6000))
        assertEquals("qwen25-15b", BrainChoiceRules.recommendedId(4000))
        assertNull(BrainChoiceRules.recommendedId(3999))
        assertNull(BrainChoiceRules.recommendedId(2000))
    }

    @Test fun ownerPickBrainIsNeverInTheAutomaticPackList() {
        assertNotNull(OfflinePack.byId("qwen25-3b"))
        assertTrue(OfflinePack.MODELS.none { it.id == "qwen25-3b" })
        assertTrue(OfflinePack.toDownload(emptySet(), emptySet(), 16000, 50 * gb, true, true).none { it.id == "qwen25-3b" })
        assertTrue(OfflinePack.runnable(16000).none { it.id == "qwen25-3b" })
        assertEquals(5, OfflinePack.MODELS.size)
    }

    @Test fun installedOwnerPickBrainIsTriedFirst() {
        val order = OfflinePack.tryOrder(mapOf("qwen25-15b" to "/a", "qwen25-3b" to "/b"))
        assertEquals("qwen25-3b", order.first().first.id)
        assertEquals("qwen25-15b", order[1].first.id)
        assertEquals(listOf("qwen25-15b"), OfflinePack.tryOrder(mapOf("qwen25-15b" to "/a")).map { it.first.id })
    }

    @Test fun optionStatesFollowRamStorageAndInstalled() {
        val strong = BrainChoiceRules.options(8000, 20 * gb, emptySet())
        assertEquals(listOf("qwen25-3b", "qwen25-15b"), strong.map { it.id })
        assertTrue(strong.all { it.state == BrainChoiceRules.State.OFFER })
        assertTrue(strong[0].recommended)
        assertFalse(strong[1].recommended)

        val mid = BrainChoiceRules.options(5000, 20 * gb, emptySet())
        assertEquals(BrainChoiceRules.State.NEEDS_RAM, mid[0].state)      // 3B needs 6 GB
        assertEquals(BrainChoiceRules.State.OFFER, mid[1].state)
        assertTrue(mid[1].recommended)

        val low = BrainChoiceRules.options(3000, 20 * gb, emptySet())
        assertTrue(low.all { it.state == BrainChoiceRules.State.NEEDS_RAM })
        assertTrue(low.none { it.recommended })

        val tight = BrainChoiceRules.options(8000, 1 * gb, emptySet())
        assertTrue(tight.all { it.state == BrainChoiceRules.State.NEEDS_SPACE })

        val have = BrainChoiceRules.options(8000, 20 * gb, setOf("qwen25-3b"))
        assertEquals(BrainChoiceRules.State.INSTALLED, have[0].state)
    }

    @Test fun downloadNeedsTheOwnersTapAndEveryCondition() {
        val none = emptySet<String>()
        assertNull(BrainChoiceRules.refusal("qwen25-3b", true, 8000, 20 * gb, true, true, none))
        assertEquals("owner has not confirmed", BrainChoiceRules.refusal("qwen25-3b", false, 8000, 20 * gb, true, true, none))
        assertEquals("unknown brain", BrainChoiceRules.refusal("llama32-1b", true, 8000, 20 * gb, true, true, none))
        assertEquals("unknown brain", BrainChoiceRules.refusal("../x", true, 8000, 20 * gb, true, true, none))
        assertEquals("already installed", BrainChoiceRules.refusal("qwen25-3b", true, 8000, 20 * gb, true, true, setOf("qwen25-3b")))
        assertEquals("phone RAM too low", BrainChoiceRules.refusal("qwen25-3b", true, 4000, 20 * gb, true, true, none))
        assertEquals("not enough free storage", BrainChoiceRules.refusal("qwen25-3b", true, 8000, 2 * gb, true, true, none))
        assertEquals("no internet", BrainChoiceRules.refusal("qwen25-3b", true, 8000, 20 * gb, true, false, none))
        assertTrue(BrainChoiceRules.refusal("qwen25-3b", true, 8000, 20 * gb, false, true, none)!!.contains("Wi-Fi"))
    }

    @Test fun onlyTheTwoOfferedIdsAreAccepted() {
        assertTrue(BrainChoiceRules.isOfferedId("qwen25-3b"))
        assertTrue(BrainChoiceRules.isOfferedId("qwen25-15b"))
        assertFalse(BrainChoiceRules.isOfferedId("smollm2-17b"))
        assertFalse(BrainChoiceRules.isOfferedId(""))
    }

    @Test fun headlineIsHonestAboutWeakPhones() {
        assertTrue(BrainChoiceRules.headline(2000, emptySet()).contains("RAM kam"))
        assertTrue(BrainChoiceRules.headline(8000, emptySet()).contains("Qwen2.5 3B"))
        assertTrue(BrainChoiceRules.headline(8000, setOf("qwen25-15b")).contains("Bada brain ON"))
    }
}
