package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.54 Part 13B-2a. Expected values worked out by hand. Only MiniJson (no org.json), so it runs in plain JUnit. */
class FreeVisionTest {
    private val OK_BODY = """{"choices":[{"message":{"content":"{\"summary\":\"x\"}"}}]}"""
    private fun answer(code: Int, body: String = "") = NetAnswer(code, body)
    private val keys = { n: String -> if (n == "groq") "gk" else if (n == "openrouter") "ok" else "" }
    private val groqOnly = { n: String -> if (n == "groq") "gk" else "" }

    @Test fun onlyImageCapableProvidersAreUsed() {
        assertEquals(listOf("groqqwen", "openrouterglm", "openrouter"), FreeVision.providers().map { it.id })
        assertEquals(listOf("z-ai/glm-5.3-flash:free"), FreeVision.GLM.models)
        assertEquals(listOf("qwen/qwen3.8-27b"), FreeVision.GROQ.models)
        assertEquals(listOf("openrouter/free"), FreeVision.OPENROUTER.models)
        // text-only / foreign providers never get a picture
        assertTrue(FreeVision.providers().none { it.id in setOf("groq", "zai", "zai2", "zai3", "pollinations") })
        assertTrue(FreeVision.providers().none { it.models.any { m -> m.contains("gpt-oss") || m.contains("llama-3.3") } })
    }

    @Test fun pickIndexesSpreadEvenly() {
        assertEquals(emptyList<Int>(), FreeVision.pickIndexes(0))
        assertEquals(listOf(0), FreeVision.pickIndexes(1))
        assertEquals(listOf(0, 1, 2), FreeVision.pickIndexes(3))
        assertEquals(listOf(0, 3, 5), FreeVision.pickIndexes(6))   // 0, round(2.5)=3, 5
        assertEquals(listOf(0, 2, 4), FreeVision.pickIndexes(5))
        assertEquals(listOf(0), FreeVision.pickIndexes(9, 1))
        assertEquals(listOf(0, 8), FreeVision.pickIndexes(9, 2))
    }

    @Test fun requestIsOpenAiStyleWithDataUrls() {
        val j = FreeVision.requestJson("qwen/qwen3.8-27b", "SYS", "Q", listOf("AAA", "BBB"), true)
        val root = MiniJson.parse(j)
        assertEquals("qwen/qwen3.8-27b", MiniJson.field(root, "model"))
        val msgs = MiniJson.field(root, "messages")
        assertEquals("system", MiniJson.field(MiniJson.at(msgs, 0), "role"))
        assertEquals("SYS", MiniJson.field(MiniJson.at(msgs, 0), "content"))
        val content = MiniJson.field(MiniJson.at(msgs, 1), "content")
        assertEquals("text", MiniJson.field(MiniJson.at(content, 0), "type"))
        assertEquals("Q", MiniJson.field(MiniJson.at(content, 0), "text"))
        assertEquals("image_url", MiniJson.field(MiniJson.at(content, 1), "type"))
        assertEquals("data:image/jpeg;base64,AAA", MiniJson.field(MiniJson.field(MiniJson.at(content, 1), "image_url"), "url"))
        assertEquals("data:image/jpeg;base64,BBB", MiniJson.field(MiniJson.field(MiniJson.at(content, 2), "image_url"), "url"))
        assertEquals("json_object", MiniJson.field(MiniJson.field(root, "response_format"), "type"))
    }

    @Test fun jsonModeOnlyForGroq() {
        assertTrue(FreeVision.jsonModeFor(FreeVision.GROQ))
        assertFalse(FreeVision.jsonModeFor(FreeVision.OPENROUTER))
        assertFalse(FreeVision.requestJson("m", "s", "u", listOf("A"), false).contains("response_format"))
    }

    @Test fun replyTextKeepsWholeJsonAndStripsThinkAndFence() {
        assertEquals("{\"summary\":\"x\"}", FreeVision.replyText(OK_BODY))
        val fenced = "{\"choices\":[{\"message\":{\"content\":\"<think>hmm</think>\\n```json\\n{\\\"a\\\":1}\\n```\"}}]}"
        assertEquals("{\"a\":1}", FreeVision.replyText(fenced))
        val long = "x".repeat(3000)
        assertEquals(3000, FreeVision.replyText("{\"choices\":[{\"message\":{\"content\":\"$long\"}}]}")!!.length)   // never shortened like spoken text
        assertNull(FreeVision.replyText("{\"choices\":[]}"))
        assertNull(FreeVision.replyText("{\"choices\":[{\"message\":{\"content\":\"  \"}}]}"))
        assertNull(FreeVision.replyText("not json"))
    }

    @Test fun codesMapToOutcomes() {
        assertEquals("NOKEY", AnalysisPipelineRules.callOutcome(FreeVision.codeFor("nokey")))
        assertEquals("LIMIT", AnalysisPipelineRules.callOutcome(FreeVision.codeFor("quota")))
        assertEquals("LIMIT", AnalysisPipelineRules.callOutcome(FreeVision.codeFor("cooling")))
        assertEquals("OFFLINE", AnalysisPipelineRules.callOutcome(FreeVision.codeFor("network")))
        assertEquals("OTHER", AnalysisPipelineRules.callOutcome(FreeVision.codeFor("server")))
        assertEquals("OTHER", AnalysisPipelineRules.callOutcome(FreeVision.codeFor("model")))
    }

    @Test fun readyNeedsSwitchKeyAndNoCooldown() {
        val cd = Cooldowns()
        assertTrue(FreeVision.ready(true, keys, cd, 0L))
        assertFalse(FreeVision.ready(false, keys, cd, 0L))                       // owner switched the free chain off
        assertFalse(FreeVision.ready(true, { "" }, cd, 0L))                      // no key
        assertTrue(FreeVision.ready(true, { n -> if (n == "openrouter") "k" else "" }, cd, 0L))
        cd.set("groqqwen", 60_000L, 0L)
        assertTrue(FreeVision.ready(true, keys, cd, 1L))                         // OpenRouter is still free
        assertFalse(FreeVision.ready(true, groqOnly, cd, 1L))                    // only Groq has a key and it is cooling
    }

    @Test fun runGroqAnswersFirst() {
        val seen = ArrayList<String>()
        val r = FreeVision.run("S", "U", listOf("A"), keys, true, Cooldowns(), { 0L }, 30_000L, { false }) { p, m, _, key ->
            seen.add(p.id + "/" + m + "/" + key); answer(200, OK_BODY)
        }
        assertEquals("{\"summary\":\"x\"}", r.text)
        assertEquals("groqqwen", r.providerId)
        assertEquals(listOf("groqqwen/qwen/qwen3.8-27b/gk"), seen)
    }

    @Test fun runMovesOnAndSetsSharedCooldown() {
        val cd = Cooldowns()
        val asked = ArrayList<String>()
        val r = FreeVision.run("S", "U", listOf("A"), keys, true, cd, { 100L }, 30_000L, { false }) { p, _, _, _ ->
            asked.add(p.id); if (p.id == "groqqwen") answer(401) else answer(200, OK_BODY)
        }
        assertEquals("openrouter", r.providerId)
        assertEquals(listOf("groqqwen", "openrouter"), asked)
        assertTrue(cd.cooling("groqqwen", 101L))                                  // wrong key: 10 min pause, shared with the text chain
        assertFalse(cd.cooling("openrouter", 101L))
    }

    @Test fun runQuotaOnGroqThenOpenRouter() {
        val cd = Cooldowns()
        val r = FreeVision.run("S", "U", listOf("A"), keys, true, cd, { 0L }, 30_000L, { false }) { p, _, _, _ ->
            if (p.id == "groqqwen") answer(429) else answer(200, OK_BODY)
        }
        assertEquals("openrouter", r.providerId)
        assertTrue(cd.cooling("groqqwen", 1L))
    }

    @Test fun runNoKeyMeansNoCallAtAll() {
        var calls = 0
        val r = FreeVision.run("S", "U", listOf("A"), { "" }, true, Cooldowns(), { 0L }, 30_000L, { false }) { _, _, _, _ -> calls++; answer(200, OK_BODY) }
        assertNull(r.text); assertEquals("nokey", r.failKind); assertEquals(0, calls)
    }

    @Test fun runSwitchOffMeansNoCall() {
        var calls = 0
        val r = FreeVision.run("S", "U", listOf("A"), keys, false, Cooldowns(), { 0L }, 30_000L, { false }) { _, _, _, _ -> calls++; answer(200, OK_BODY) }
        assertNull(r.text); assertEquals(0, calls)
    }

    @Test fun runOfflineEndsAtOnce() {
        var calls = 0
        val r = FreeVision.run("S", "U", listOf("A"), keys, true, Cooldowns(), { 0L }, 30_000L, { false }) { _, _, _, _ -> calls++; answer(598) }
        assertEquals("network", r.failKind); assertEquals(1, calls)
    }

    @Test fun runStopsWhenCancelled() {
        var calls = 0
        val r = FreeVision.run("S", "U", listOf("A"), keys, true, Cooldowns(), { 0L }, 30_000L, { true }) { _, _, _, _ -> calls++; answer(200, OK_BODY) }
        assertNull(r.text); assertEquals(0, calls)
    }

    @Test fun runEmptyAnswerIsAFailureNotAnInventedText() {
        val r = FreeVision.run("S", "U", listOf("A"), groqOnly, true, Cooldowns(), { 0L }, 30_000L, { false }) { _, _, _, _ -> answer(200, "{\"choices\":[]}") }
        assertNull(r.text); assertEquals("other", r.failKind)
    }

    @Test fun runBudgetEnds() {
        var t = 0L
        var calls = 0
        val r = FreeVision.run("S", "U", listOf("A"), keys, true, Cooldowns(), { t }, 1_000L, { false }) { _, _, _, _ -> calls++; t += 2_000L; answer(500) }
        assertNull(r.text); assertEquals(1, calls)                                // second provider is not asked: the 35 s rule holds
    }

    @Test fun pipelineRulesForFreeFallback() {
        assertTrue(AnalysisPipelineRules.tryFreeAfter("LIMIT", true, false))
        assertTrue(AnalysisPipelineRules.tryFreeAfter("NOKEY", true, false))
        assertTrue(AnalysisPipelineRules.tryFreeAfter("OTHER", true, false))
        assertFalse(AnalysisPipelineRules.tryFreeAfter("OFFLINE", true, false))   // no internet: every provider would fail too
        assertFalse(AnalysisPipelineRules.tryFreeAfter("OK", true, false))
        assertFalse(AnalysisPipelineRules.tryFreeAfter("LIMIT", false, false))    // no free key / switch off
        assertFalse(AnalysisPipelineRules.tryFreeAfter("LIMIT", true, true))      // already on the free chain
        assertEquals(listOf(Pair(0, 3), Pair(3, 3)), AnalysisPipelineRules.batches(6, FreeVision.FRAMES_PER_CALL))
        assertEquals(listOf(Pair(0, 3), Pair(3, 3)), AnalysisPipelineRules.batches(12, FreeVision.FRAMES_PER_CALL))   // never more than 2 calls
        assertEquals(listOf(Pair(0, 2)), AnalysisPipelineRules.batches(2, FreeVision.FRAMES_PER_CALL))
        assertNotNull(AnalysisPipelineRules.freeUsedNote(false)); assertTrue(AnalysisPipelineRules.freeUsedNote(true).contains("Groq"))
    }
}
