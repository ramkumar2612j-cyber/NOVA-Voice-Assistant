package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class SelfImproveRulesTest {
    private val apps = listOf("YouTube", "WhatsApp", "Camera")

    private fun reply(vararg pairs: Pair<String, String>): String =
        "{\"items\":[" + pairs.joinToString(",") { "{\"alias\":\"" + it.first + "\",\"value\":\"" + it.second + "\",\"why\":\"short\"}" } + "]}"

    @Test fun windowIsExactlyOneHour() {
        assertEquals(60L * 60L * 1000L, SelfImproveRules.WINDOW_MS)
        assertEquals(60L * 60L * 1000L, SelfImproveRules.DAILY_BUDGET_MS)
        assertTrue(SelfImproveRules.inWindow(3, 3))
        assertFalse(SelfImproveRules.inWindow(4, 3))
        assertFalse(SelfImproveRules.inWindow(2, 3))
    }

    @Test fun budgetStartsAgainOnANewDay() {
        assertEquals(SelfImproveRules.DAILY_BUDGET_MS, SelfImproveRules.remainingMs(99_999L, "20261014", "20261015"))
        assertEquals(1000L, SelfImproveRules.remainingMs(SelfImproveRules.DAILY_BUDGET_MS - 1000L, "20261015", "20261015"))
        assertEquals(0L, SelfImproveRules.remainingMs(SelfImproveRules.DAILY_BUDGET_MS * 2, "20261015", "20261015"))
    }

    @Test fun autoRunNeedsSwitchWindowBudgetGapAndRoom() {
        val gap = SelfImproveRules.MIN_GAP_MS
        assertTrue(SelfImproveRules.canAutoRun(true, 3, 3, 1000L, gap, 0, 0))
        assertFalse(SelfImproveRules.canAutoRun(false, 3, 3, 1000L, gap, 0, 0))
        assertFalse(SelfImproveRules.canAutoRun(true, 5, 3, 1000L, gap, 0, 0))
        assertFalse(SelfImproveRules.canAutoRun(true, 3, 3, 0L, gap, 0, 0))
        assertFalse(SelfImproveRules.canAutoRun(true, 3, 3, 1000L, gap - 1L, 0, 0))
        assertFalse(SelfImproveRules.canAutoRun(true, 3, 3, 1000L, gap, SelfImproveRules.MAX_AUTO_RUNS_PER_DAY, 0))
        assertFalse(SelfImproveRules.canAutoRun(true, 3, 3, 1000L, gap, 0, SelfImproveRules.MAX_PENDING))
    }

    @Test fun manualRunStillRespectsBudgetAndLimits() {
        assertTrue(SelfImproveRules.canManualRun(1000L, 0, 0))
        assertFalse(SelfImproveRules.canManualRun(0L, 0, 0))
        assertFalse(SelfImproveRules.canManualRun(1000L, SelfImproveRules.MAX_MANUAL_RUNS_PER_DAY, 0))
        assertFalse(SelfImproveRules.canManualRun(1000L, 0, SelfImproveRules.MAX_PENDING))
    }

    @Test fun validProposalIsKept() {
        val p = SelfImproveRules.parse(reply("yutub" to "YouTube"), apps, emptyList())
        assertEquals(1, p.size)
        assertEquals("yutub", p[0].alias)
        assertEquals("YouTube", p[0].value)
    }

    @Test fun appThatIsNotInstalledIsDropped() {
        assertTrue(SelfImproveRules.parse(reply("gaana" to "Spotify"), apps, emptyList()).isEmpty())
    }

    @Test fun knownOrDuplicateAliasesAreDropped() {
        assertTrue(SelfImproveRules.parse(reply("yutub" to "YouTube"), apps, listOf("Yutub")).isEmpty())
        assertEquals(1, SelfImproveRules.parse(reply("yutub" to "YouTube", "yutub" to "YouTube"), apps, emptyList()).size)
        assertTrue(SelfImproveRules.parse(reply("camera" to "Camera"), apps, emptyList()).isEmpty())
    }

    @Test fun secretsLinksAndBadShapesAreDropped() {
        assertTrue(SelfImproveRules.parse(reply("my password" to "YouTube"), apps, emptyList()).isEmpty())
        assertTrue(SelfImproveRules.parse("{\"items\":[{\"alias\":\"yutub\",\"value\":\"YouTube\",\"why\":\"api key here\"}]}", apps, emptyList()).isEmpty())
        assertTrue(SelfImproveRules.parse(reply("yutub 2" to "YouTube"), apps, emptyList()).isEmpty())
        assertTrue(SelfImproveRules.parse("not json at all", apps, emptyList()).isEmpty())
        assertTrue(SelfImproveRules.parse("{\"items\":\"x\"}", apps, emptyList()).isEmpty())
        assertTrue(SelfImproveRules.parse("", apps, emptyList()).isEmpty())
    }

    @Test fun atMostFivePerRun() {
        val many = listOf("aaa", "bbb", "ccc", "ddd", "eee", "fff", "ggg", "hhh").map { Pair(it, "YouTube") }
        assertEquals(SelfImproveRules.MAX_PER_RUN, SelfImproveRules.parse(reply(*many.toTypedArray()), apps, emptyList()).size)
    }

    @Test fun replyCutByTheChatCleanerKeepsTheCompleteItems() {
        val cut = "{\"items\":[{\"alias\":\"yutub\",\"value\":\"YouTube\",\"why\":\"x\"},{\"alias\":\"wa\",\"value\":\"What"
        val p = SelfImproveRules.parse(cut, apps, emptyList())
        assertEquals(1, p.size)
        assertEquals("yutub", p[0].alias)
    }

    @Test fun markdownFenceAroundJsonIsAccepted() {
        val p = SelfImproveRules.parse("```json\n" + reply("yutub" to "YouTube") + "\n```", apps, emptyList())
        assertEquals(1, p.size)
    }

    @Test fun neverGeminiNeverAnonymous() {
        val all = FreeProviders.ordered()
        val ok = SelfImproveRules.allowedProviders(all)
        assertTrue(ok.isNotEmpty())
        assertTrue(ok.none { it.keyName == null })
        assertTrue(ok.none { it.id.contains("gemini", true) || it.label.contains("gemini", true) })
        assertTrue(ok.none { it.id == "pollinations" })
    }

    @Test fun promptNeverMentionsPrivateData() {
        val u = SelfImproveRules.userPrompt(apps, listOf("mummy"))
        assertTrue(u.contains("YouTube"))
        assertFalse(SelfImproveRules.systemPrompt().contains("contact", true))
    }
}
