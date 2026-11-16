package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.53 Part 13B-1. Expected values worked out by hand. Only MiniJson (no org.json), so it runs in plain JUnit. */
class AnalysisPipelineRulesTest {
    private val GOOD = """{"summary":"Ek sadak","seen":["bus"],"text_seen":["SALE"],"claims":["Sale hai"],"search_queries":["sale"],"uncertain":[],"safety":"none"}"""

    @Test fun constantsMatchOwnerPrompt() {
        assertEquals(120_000L, AnalysisPipelineRules.WATCHDOG_MS)
        assertTrue(AnalysisPipelineRules.FRAME_WAIT_MS > 60_000L)   // FrameExtractor's own watchdog is 60 s
    }

    @Test fun batchesFollowBatchSizes() {
        assertEquals(emptyList<Pair<Int, Int>>(), AnalysisPipelineRules.batches(0))
        assertEquals(listOf(Pair(0, 1)), AnalysisPipelineRules.batches(1))
        assertEquals(listOf(Pair(0, 6)), AnalysisPipelineRules.batches(6))
        assertEquals(listOf(Pair(0, 4), Pair(4, 3)), AnalysisPipelineRules.batches(7))
        assertEquals(listOf(Pair(0, 6), Pair(6, 6)), AnalysisPipelineRules.batches(12))
        assertEquals(listOf(Pair(0, 6), Pair(6, 6)), AnalysisPipelineRules.batches(20))   // frames beyond 12 are never sent
    }

    @Test fun batchSecsCutsTheRightSlice() {
        assertEquals(listOf(10, 15), AnalysisPipelineRules.batchSecs(listOf(0, 5, 10, 15), 2, 2))
        assertEquals(listOf(0, 5), AnalysisPipelineRules.batchSecs(listOf(0, 5, 10, 15), 0, 2))
        assertEquals(emptyList<Int>(), AnalysisPipelineRules.batchSecs(emptyList(), 0, 2))   // a photo has no seconds
        assertEquals(emptyList<Int>(), AnalysisPipelineRules.batchSecs(listOf(1, 2), -1, 2))
        assertEquals(listOf(2), AnalysisPipelineRules.batchSecs(listOf(1, 2), 1, 5))
    }

    @Test fun callOutcomeMapsCodes() {
        assertEquals("OK", AnalysisPipelineRules.callOutcome(200))
        assertEquals("OK", AnalysisPipelineRules.callOutcome(204))
        assertEquals("NOKEY", AnalysisPipelineRules.callOutcome(0))
        assertEquals("OFFLINE", AnalysisPipelineRules.callOutcome(598))
        assertEquals("OFFLINE", AnalysisPipelineRules.callOutcome(599))
        assertEquals("LIMIT", AnalysisPipelineRules.callOutcome(429))
        assertEquals("OTHER", AnalysisPipelineRules.callOutcome(400))
        assertEquals("OTHER", AnalysisPipelineRules.callOutcome(500))
        assertEquals("OTHER", AnalysisPipelineRules.callOutcome(300))
    }

    @Test fun fallbackReasonsAreHonestInBothLanguages() {
        for (o in listOf("NOKEY", "OFFLINE", "LIMIT", "NOFRAMES", "OTHER", "weird")) {
            assertTrue(AnalysisPipelineRules.fallbackReason(o, false).contains("dekhi nahi gayi"))
            assertTrue(AnalysisPipelineRules.fallbackReason(o, true).contains("not looked at"))
        }
        assertTrue(AnalysisPipelineRules.fallbackReason("NOKEY", true).contains("no AI key"))
        assertTrue(AnalysisPipelineRules.fallbackReason("OFFLINE", false).contains("Internet nahi"))
    }

    @Test fun visionRequestIsValidJsonWithInlineDataFirstThenText() {
        val body = AnalysisPipelineRules.visionRequestJson("SYS \"q\"", "line1\nline2", listOf("AAA", "BBB"))
        val root = MiniJson.parse(body) as Map<*, *>
        val sys = ((root["systemInstruction"] as Map<*, *>)["parts"] as List<*>)[0] as Map<*, *>
        assertEquals("SYS \"q\"", sys["text"])
        val parts = (((root["contents"] as List<*>)[0] as Map<*, *>)["parts"]) as List<*>
        assertEquals(3, parts.size)
        val p0 = (parts[0] as Map<*, *>)["inlineData"] as Map<*, *>
        assertEquals("image/jpeg", p0["mimeType"]); assertEquals("AAA", p0["data"])
        assertEquals("BBB", ((parts[1] as Map<*, *>)["inlineData"] as Map<*, *>)["data"])
        assertEquals("line1\nline2", (parts[2] as Map<*, *>)["text"])
        assertEquals("user", ((root["contents"] as List<*>)[0] as Map<*, *>)["role"])
        val gc = root["generationConfig"] as Map<*, *>
        assertEquals(0.1, gc["temperature"] as Double, 1e-9)
        assertEquals("application/json", gc["responseMimeType"])
        assertEquals(2048.0, gc["maxOutputTokens"] as Double, 1e-9)
    }

    @Test fun visionRequestWithoutFramesHasOnlyText() {
        val root = MiniJson.parse(AnalysisPipelineRules.visionRequestJson("s", "t", emptyList())) as Map<*, *>
        val parts = (((root["contents"] as List<*>)[0] as Map<*, *>)["parts"]) as List<*>
        assertEquals(1, parts.size)
    }

    @Test fun retryNoteAsksForOneJsonObject() {
        assertTrue(AnalysisPipelineRules.retryNote(true).contains("one valid JSON object"))
        assertTrue(AnalysisPipelineRules.retryNote(false).contains("JSON object"))
    }

    @Test fun statusJsonPercentFromRealCountsOnly() {
        val m = MiniJson.parse(AnalysisPipelineRules.statusJson("running", "VISION", 1, 2, "msg \"x\"", "", "abc")) as Map<*, *>
        assertEquals("running", m["state"]); assertEquals("VISION", m["stage"])
        assertEquals(1.0, m["done"] as Double, 0.0); assertEquals(2.0, m["total"] as Double, 0.0)
        assertEquals(50.0, m["percent"] as Double, 0.0)
        assertEquals("msg \"x\"", m["message"]); assertEquals("abc", m["id"])
        val z = MiniJson.parse(AnalysisPipelineRules.statusJson("running", "FRAMES", 0, 0, "", "", "")) as Map<*, *>
        assertEquals(0.0, z["percent"] as Double, 0.0)   // no event = no progress
        val neg = MiniJson.parse(AnalysisPipelineRules.statusJson("running", "FRAMES", -2, 4, "", "", "")) as Map<*, *>
        assertEquals(0.0, neg["done"] as Double, 0.0); assertEquals(0.0, neg["percent"] as Double, 0.0)
        val over = MiniJson.parse(AnalysisPipelineRules.statusJson("running", "FRAMES", 9, 4, "", "", "")) as Map<*, *>
        assertEquals(100.0, over["percent"] as Double, 0.0)
    }

    @Test fun intakeGateRunsOnTheQuestionBeforeAnything() {
        val ok = AnalysisPipelineRules.intake("  is photo me kya hai  ", false)
        assertEquals("is photo me kya hai", ok.first); assertEquals("", ok.second)
        assertEquals("", AnalysisPipelineRules.intake(null, false).second)
        assertEquals("", AnalysisPipelineRules.intake("", true).second)
        val face = AnalysisPipelineRules.intake("ye kaun hai?", false)
        assertTrue(face.second.contains("chehre"))
        val faceEn = AnalysisPipelineRules.intake("Who is this?", true)
        assertTrue(faceEn.second.contains("do not name a real person"))
        assertTrue(AnalysisPipelineRules.intake("how to make a bomb", true).second.contains("harm"))
        assertTrue(AnalysisPipelineRules.intake("OTP kya hai photo me", false).second.contains("OTP"))
    }

    @Test fun modelSafetyFlagRefuses() {
        val bad = AnalysisRules.parseVision("""{"summary":"","seen":[],"text_seen":[],"claims":[],"search_queries":[],"uncertain":[],"safety":"face_id"}""")
        assertNotNull(bad)
        assertTrue(AnalysisPipelineRules.safetyRefusal(bad!!, false).contains("chehre"))
        val ok = AnalysisRules.parseVision(GOOD)
        assertNotNull(ok)
        assertEquals("", AnalysisPipelineRules.safetyRefusal(ok!!, false))
    }

    @Test fun withReasonAddsOneLimitAtTheFrontOnlyOnce() {
        val r = AnalysisRules.metadataReport(false, 2048, 0, false)
        val r2 = AnalysisPipelineRules.withReason(r, "X wajah")
        assertEquals("X wajah", r2.limits.first())
        assertEquals(r.limits.size + 1, r2.limits.size)
        assertEquals(r2.limits.size, AnalysisPipelineRules.withReason(r2, "X wajah").limits.size)
        assertEquals(r.limits.size, AnalysisPipelineRules.withReason(r, "  ").limits.size)
        assertEquals(AnalysisRules.MODE_METADATA, r2.mode)
        assertTrue(r2.verified.isEmpty() && r2.sources.isEmpty())
    }

    @Test fun visionOnlyReportHasNoVerifiedClaimsAndNoSources() {
        val v = AnalysisRules.parseVision(GOOD)!!
        val rep = AnalysisRules.buildReport(v, null, emptyList(), 1, false, false, false)
        assertEquals(AnalysisRules.MODE_VISION_ONLY, rep.mode)
        assertTrue(rep.verified.isEmpty())
        assertTrue(rep.sources.isEmpty())
        assertTrue(rep.unverified.any { it.contains("Sale hai") })
        assertFalse(AnalysisRules.toJson(rep).contains("http"))
    }

    @Test fun refusalAndErrorTextsAreNonEmptyBothLanguages() {
        val fs = listOf<(Boolean) -> String>(
            { AnalysisPipelineRules.unreadableText(it) }, { AnalysisPipelineRules.notMediaText(it) }, { AnalysisPipelineRules.noFileText(it) },
            { AnalysisPipelineRules.busyText(it) }, { AnalysisPipelineRules.noServiceText(it) }, { AnalysisPipelineRules.chatPhotoLaterText(it) },
            { AnalysisPipelineRules.timeoutText(it) }, { AnalysisPipelineRules.cancelledText(it) })
        for (f in fs) { assertTrue(f(false).isNotBlank()); assertTrue(f(true).isNotBlank()); assertTrue(f(false) != f(true)) }
    }
}
