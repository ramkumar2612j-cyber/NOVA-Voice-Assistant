package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class OfflinePackTest {
    private val sha = "a".repeat(64)
    private fun entry(path: String, size: Long, oid: String = sha, type: String = "file") =
        "{\"type\":\"$type\",\"path\":\"$path\",\"size\":$size,\"lfs\":{\"oid\":\"$oid\",\"size\":$size}}"
    private fun tree(vararg e: String) = "[" + e.joinToString(",") + "]"
    private val qwen = OfflinePack.byId("qwen25-15b")!!

    @Test fun catalogHasFiveModelsThreeChineseTwoWorld() {
        assertEquals(5, OfflinePack.MODELS.size)
        assertEquals(3, OfflinePack.MODELS.count { it.origin == "CN" })
        assertEquals(2, OfflinePack.MODELS.count { it.origin == "World" })
        assertEquals(5, OfflinePack.MODELS.map { it.id }.toSet().size)
        for (m in OfflinePack.MODELS) {
            assertTrue(m.minBytes < m.maxBytes)
            assertTrue(m.repo.contains("/"))
        }
    }

    @Test fun pickFileTakesTheQ4KMFileWithItsRealHash() {
        val t = tree(entry("README.md", 100, "x"), entry("qwen2.5-1.5b-instruct-q8_0.gguf", 1_700_000_000), entry("qwen2.5-1.5b-instruct-q4_k_m.gguf", 1_117_000_000))
        val f = OfflinePack.pickFile(t, qwen)!!
        assertEquals("qwen2.5-1.5b-instruct-q4_k_m.gguf", f.path)
        assertEquals(1_117_000_000L, f.size)
        assertEquals(sha, f.sha256)
        assertEquals("https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf", OfflinePack.downloadUrl(qwen, f))
    }

    @Test fun pickFileRefusesWrongSizeBadHashSplitAndSubfolderFiles() {
        assertNull(OfflinePack.pickFile(tree(entry("m-q4_k_m.gguf", 1000)), qwen))                           // far too small
        assertNull(OfflinePack.pickFile(tree(entry("m-q4_k_m.gguf", 1_100_000_000, "nothex")), qwen))      // no real sha256
        assertNull(OfflinePack.pickFile(tree(entry("m-q4_k_m-00001-of-00002.gguf", 1_100_000_000)), qwen)) // split part
        assertNull(OfflinePack.pickFile(tree(entry("sub/m-q4_k_m.gguf", 1_100_000_000)), qwen))            // sub folder
        assertNull(OfflinePack.pickFile(tree(entry("m-q4_k_m.gguf", 1_100_000_000, type = "directory")), qwen))
        assertNull(OfflinePack.pickFile("not json", qwen))
        assertNull(OfflinePack.pickFile("{}", qwen))
    }

    @Test fun downloadOnlyOnUnmeteredOnlineWithRamAndStorage() {
        val gb = 1024L * 1024 * 1024
        val all = OfflinePack.toDownload(emptySet(), emptySet(), 8000, 20 * gb, unmetered = true, online = true)
        assertEquals(OfflinePack.MODELS.map { it.id }, all.map { it.id })
        assertTrue(OfflinePack.toDownload(emptySet(), emptySet(), 8000, 20 * gb, unmetered = false, online = true).isEmpty())  // mobile data
        assertTrue(OfflinePack.toDownload(emptySet(), emptySet(), 8000, 20 * gb, unmetered = true, online = false).isEmpty())
        assertTrue(OfflinePack.toDownload(emptySet(), emptySet(), 8000, 1 * gb, unmetered = true, online = true).isEmpty())    // storage would run out
        val small = OfflinePack.toDownload(emptySet(), emptySet(), 3000, 20 * gb, unmetered = true, online = true)
        assertEquals(listOf("llama32-1b"), small.map { it.id })                                                                  // only what the RAM can run
        val rest = OfflinePack.toDownload(setOf("qwen25-15b"), setOf("llama32-1b"), 8000, 20 * gb, true, true)
        assertFalse(rest.any { it.id == "qwen25-15b" || it.id == "llama32-1b" })
    }

    @Test fun tryOrderFollowsCatalogOrderAndSkipsMissing() {
        val o = OfflinePack.tryOrder(mapOf("deepseek-r1-15b" to "/d", "qwen25-15b" to "/q", "unknown" to "/x"))
        assertEquals(listOf("qwen25-15b", "deepseek-r1-15b"), o.map { it.first.id })
    }

    private val chatml = "<|im_start|>system\nBe short.<|im_end|>\n<|im_start|>user\nhello<|im_end|>\n<|im_start|>assistant\n"

    @Test fun chatmlStaysChatml() {
        assertEquals(chatml, PackPrompt.convert(chatml, OfflinePack.T_CHATML))
    }

    @Test fun llama3TemplateUsesHeadersAndEndsOnTheAssistantHeader() {
        val p = PackPrompt.convert(chatml, OfflinePack.T_LLAMA3)
        assertEquals("<|start_header_id|>system<|end_header_id|>\n\nBe short.<|eot_id|><|start_header_id|>user<|end_header_id|>\n\nhello<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n", p)
        assertFalse(p.contains("im_start"))
    }

    @Test fun deepseekTemplateUsesUserAndAssistantMarkers() {
        val p = PackPrompt.convert(chatml, OfflinePack.T_DEEPSEEK)
        assertEquals("Be short.<\uFF5CUser\uFF5C>hello<\uFF5CAssistant\uFF5C>", p)
    }

    @Test fun realLocalChatPromptConvertsWithoutLeftovers() {
        val raw = LocalChat.buildPrompt("mausam kaisa hai", listOf("naam Ravi"), listOf("hi"))
        for (t in listOf(OfflinePack.T_LLAMA3, OfflinePack.T_DEEPSEEK)) {
            val p = PackPrompt.convert(raw, t)
            assertFalse(p.contains("<|im_start|>"))
            assertFalse(p.contains("<|im_end|>"))
            assertTrue(p.contains("mausam kaisa hai"))
        }
    }

    @Test fun textCleanCutsStopTokensAndThinking() {
        assertEquals("Namaste", PackText.clean("Namaste<|eot_id|>junk"))
        assertEquals("Hello there", PackText.clean("Hello there<end_of_turn>\n<start_of_turn>user"))
        assertEquals("Answer", PackText.clean("<think>hmm let me think</think>Answer</s>"))
        assertNull(PackText.clean("<think>still thinking and the time ran out"))
        assertNull(PackText.clean("   "))
        assertNull(PackText.clean(null))
        assertEquals("Ok", PackText.clean("Ok\uFF5Cend\u2581of\u2581sentence\uFF5C"))
    }

    @Test fun statusLineCountsOnlyRunnableModels() {
        val s = OfflinePack.statusLine(setOf("qwen25-15b"), 3000)
        assertTrue(s.contains("0/1"))   // 3 GB phone: only Llama 3.2 1B is runnable and it is not installed
        assertTrue(OfflinePack.statusLine(setOf("qwen25-15b"), 8000).contains("1/5"))
    }
}
