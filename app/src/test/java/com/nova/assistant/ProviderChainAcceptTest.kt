package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class ProviderChainAcceptTest {
    private fun okBody(t: String) = "{\"choices\":[{\"message\":{\"content\":" + MiniJson.quote(t) + "}}]}"
    private fun prov(id: String) = FreeProvider(id, id, "https://x.test/$id", listOf("m1"), null)

    @Test fun proseAnswerIsSkippedAndNextProviderUsed() {
        val cd = Cooldowns()
        val calls = ArrayList<String>()
        val r = ProviderChain.run(
            listOf(prov("a"), prov("b")), { "" }, { true }, "sys", "hello", cd, { 0L },
            accept = { !it.startsWith("PROSE") }
        ) { p, m, _, _ ->
            calls.add(p.id + ":" + m)
            NetAnswer(200, okBody(if (p.id == "a") "PROSE no action" else "real answer"))
        }
        assertEquals("b", r.providerId)
        assertEquals("real answer", r.text)
        assertEquals(listOf("a:m1", "b:m1"), calls)
        assertFalse("rejected answer must not start a cooldown", cd.cooling("a", 0L))
    }
}
