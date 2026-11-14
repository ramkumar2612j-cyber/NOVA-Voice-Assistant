package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class MatchLivePipelineTest {
    private fun snap(score: String, a: String = "India", b: String = "Australia", match: Boolean = true) =
        """{"safety":"ok","is_match":$match,"sport":"cricket","team_a":"$a","team_b":"$b","tournament":"T20","score_a":"$score","score_b":"","status":"","confidence":"high"}"""
    private val PRED1 = """{"a_pct":60,"b_pct":40,"draw_pct":0,"reasons":["form [1]"],"watch":"powerplay"}"""
    private val PRED2 = """{"a_pct":99,"b_pct":1,"draw_pct":0,"line":"wicket gira"}"""

    private class Clock { var t = 0L }
    private class Brain(val visions: List<String>, val preds: List<String>, val blockedKind: String = "") : AnalysisPipeline.Brain {
        var vi = 0; var pi = 0; var visionCalls = 0
        override fun en() = false
        override fun blocked() = blockedKind
        override fun call(json: String): Pair<Int, String> { visionCalls++; val r = visions[minOf(vi, visions.size - 1)]; vi++; return Pair(200, r) }
        override fun callTextWith(json: String, readMs: Long, deadlineMs: Long): Pair<Int, String> { val r = preds[minOf(pi, preds.size - 1)]; pi++; return Pair(200, r) }
    }
    private fun src(c: Clock, stopAt: Long, prot: Boolean = false, onShot: () -> Unit = {}) = object : MovieScanCapture.Source {
        var n = 0
        override fun foregroundProtected(): Boolean? = prot
        override fun shot(): ByteArray? { onShot(); n++; return byteArrayOf(1, 2, 3, n.toByte()) }
        override fun cancelled(): Boolean = c.t >= stopAt
        override fun sleep(ms: Long) { c.t += ms }
        override fun supported(): Boolean = true
    }

    @Test fun identifies_predicts_then_updates_on_score_change_and_stops() {
        val c = Clock(); val said = ArrayList<String>()
        val brain = Brain(listOf(snap("100/2"), snap("100/2"), snap("104/2"), snap("104/2")), listOf(PRED1, PRED2))
        val end = MatchLivePipeline.run(src(c, 130_000), { "" }, brain, { t, _ -> said.add(t) }, { }, { c.t })
        assertEquals("CANCELLED", end)
        assertTrue(said[0].startsWith("Match mila")); assertTrue(said[1].contains("60%"))
        val live = said.first { it.startsWith("LIVE") }
        assertTrue(live.contains("104/2"))
        assertFalse(live.contains("99%"))   // the swing limit: 60 -> at most 85
    }

    @Test fun protected_screen_sends_nothing() {
        val c = Clock(); val said = ArrayList<String>()
        val brain = Brain(listOf(snap("1/0")), listOf(PRED1))
        val end = MatchLivePipeline.run(src(c, 60_000, prot = true), { "" }, brain, { t, _ -> said.add(t) }, { }, { c.t })
        assertEquals("PRIVATE", end); assertEquals(0, brain.visionCalls)
    }

    @Test fun no_match_on_screen_gives_up_after_three_looks() {
        val c = Clock(); val said = ArrayList<String>()
        val brain = Brain(listOf(snap("", match = false)), listOf(PRED1))
        val end = MatchLivePipeline.run(src(c, 10_000_000), { "" }, brain, { t, _ -> said.add(t) }, { }, { c.t })
        assertEquals("GAVE_UP", end); assertTrue(said.first().contains("nahi dikha")); assertTrue(said.last().contains("Teen baar"))
    }

    @Test fun owner_leaves_the_match_ends_live_mode() {
        val c = Clock(); val said = ArrayList<String>()
        val brain = Brain(listOf(snap("1/0"), snap("1/0"), snap("", match = false)), listOf(PRED1))
        val end = MatchLivePipeline.run(src(c, 10_000_000), { "" }, brain, { t, _ -> said.add(t) }, { }, { c.t })
        assertEquals("LEFT", end)
    }

    @Test fun no_ai_key_is_said_honestly() {
        val c = Clock(); val said = ArrayList<String>()
        val end = MatchLivePipeline.run(src(c, 100_000), { "" }, Brain(listOf(snap("1/0")), listOf(PRED1), blockedKind = "NOKEY"), { t, _ -> said.add(t) }, { }, { c.t })
        assertEquals("FAIL", end); assertTrue(said.last().contains("key"))
    }

    @Test fun different_match_restarts_the_analysis() {
        val c = Clock(); val said = ArrayList<String>()
        val brain = Brain(listOf(snap("1/0"), snap("1/0"), snap("3", "Real Madrid", "Barcelona")), listOf(PRED1))
        MatchLivePipeline.run(src(c, 200_000), { "" }, brain, { t, _ -> said.add(t) }, { }, { c.t })
        assertTrue(said.any { it.contains("alag match") }); assertTrue(said.any { it.startsWith("Match mila: Real Madrid") })
    }

    @Test fun bad_compose_answer_ends_with_honest_text() {
        val c = Clock(); val said = ArrayList<String>()
        val end = MatchLivePipeline.run(src(c, 100_000), { "" }, Brain(listOf(snap("1/0")), listOf("not json")), { t, _ -> said.add(t) }, { }, { c.t })
        assertEquals("NOPRED", end)
    }
}
