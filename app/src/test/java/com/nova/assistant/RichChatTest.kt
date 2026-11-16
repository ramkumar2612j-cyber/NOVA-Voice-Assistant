package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.1: typed chat turn = ChatGPT/Claude style long answer; the spoken path keeps its short plain rules. */
class RichChatTest {

    private fun okBody(t: String) = "{\"choices\":[{\"message\":{\"content\":" + MiniJson.quote(t) + "}}]}"

    @Test fun richPromptAllowsStructureAndKeepsHonesty() {
        for (en in listOf(true, false)) {
            val p = FreeProviders.richSystemPrompt(en)
            assertTrue(p.contains("Samjha:"))
            assertTrue(p.contains("Sources:"))
            assertFalse(p.contains("at most 3"))
            assertFalse(p.contains("max 3"))
        }
        // the spoken prompt is untouched
        assertTrue(FreeProviders.systemPrompt(true).contains("at most 3 short sentences"))
    }

    @Test fun richReplyKeepsNewlinesListsAndCode() {
        val text = "Samjha: aapko ek plan chahiye.\n\n1. Pehla\n2. Doosra\n\n```kotlin\nval a = 1\n```"
        assertEquals(text, FreeProviders.parseReply(okBody(text), rich = true))
    }

    @Test fun richReplyIsNotCutAt700ButSpokenReplyIs() {
        val long = "Yeh ek lamba vaakya hai. ".repeat(100)   // 2500 chars
        assertTrue(FreeProviders.parseReply(okBody(long), rich = true)!!.length > 2000)
        assertTrue(FreeProviders.parseReply(okBody(long))!!.length <= FreeProviders.MAX_REPLY_CHARS)
    }

    @Test fun richDropsThinkBlock() {
        val r = FreeProviders.parseReply(okBody("<think>hidden</think>\nAnswer"), rich = true)
        assertEquals("Answer", r)
    }

    @Test fun richBodyUsesBigTokenAndQuestionCaps() {
        val q = "a".repeat(5000)
        val rich = FreeProviders.buildBody("m", "s", q, FreeProviders.RICH_MAX_TOKENS, "", emptyList(), FreeProviders.MAX_USER_CHARS_RICH)
        assertTrue(rich.contains("\"max_tokens\":2000"))
        assertTrue(rich.contains(q))
        val plain = FreeProviders.buildBody("m", "s", q)
        assertTrue(plain.contains("\"max_tokens\":350"))
        assertFalse(plain.contains(q))
    }

    @Test fun speakableStripsMarkdownAndCodeAndCaps() {
        val s = FreeProviders.speakable("## Title\n- **one**\n- two\n```\ncode here\n```\nDone.")
        assertFalse(s.contains("#"))
        assertFalse(s.contains("*"))
        assertFalse(s.contains("code here"))
        assertTrue(s.contains("one") && s.contains("Done."))
        assertTrue(FreeProviders.speakable("Vaakya. ".repeat(300)).length <= 800)
    }

    @Test fun chainRunsRichWithBigBody() {
        val p = FreeProvider("g", "g", "https://x.test/g", listOf("m"), null)
        var seen = ""
        val r = ProviderChain.run(listOf(p), { "" }, { true }, "sys", "hello", Cooldowns(), { 0L }, rich = true) { _, _, body, _ ->
            seen = body
            NetAnswer(200, okBody("line1\nline2"))
        }
        assertNotNull(r.text)
        assertEquals("line1\nline2", r.text)
        assertTrue(seen.contains("\"max_tokens\":2000"))
    }
}
