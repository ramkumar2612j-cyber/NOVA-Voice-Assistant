package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.157: GLM 5.3 Flash own keys (1 main + 3 fallback). */
class Glm53KeysTest {
    private val ok = "{\"choices\":[{\"message\":{\"content\":\"GLM jawab.\"}}]}"

    @Test fun mainKeyAnswersFirstInABrainJob() {
        val keys = mapOf("glm53" to "m1", "glm53b" to "f1", "glm53c" to "f2", "glm53d" to "f3")
        val r = ProviderChain.run(FreeProviders.ordered(), { n -> keys[n] ?: "" }, { id -> id != "pollinations" }, "s", "u", Cooldowns(), { 0L }, glmFirst = true) { p, m, _, k ->
            assertEquals("z-ai/glm-5.3-flash:free", m)
            NetAnswer(200, ok)
        }
        assertEquals("glm53", r.providerId)
    }

    @Test fun threeFallbackKeysTakeOverWhenLimitIsUsedUp() {
        val keys = mapOf("glm53" to "m1", "glm53b" to "f1", "glm53c" to "f2", "glm53d" to "f3")
        val seen = ArrayList<String>()
        val r = ProviderChain.run(FreeProviders.ordered(), { n -> keys[n] ?: "" }, { id -> id != "pollinations" }, "s", "u", Cooldowns(), { 0L }, glmFirst = true) { p, _, _, k ->
            seen.add(p.id + ":" + k)
            if (p.id == "glm53d") NetAnswer(200, ok) else NetAnswer(429, "{}")
        }
        assertEquals("glm53d", r.providerId)
        assertTrue(seen.indexOf("glm53:m1") < seen.indexOf("glm53b:f1"))
        assertTrue(seen.indexOf("glm53b:f1") < seen.indexOf("glm53c:f2"))
        assertTrue(seen.indexOf("glm53c:f2") < seen.indexOf("glm53d:f3"))
    }

    @Test fun slotWithoutKeyIsSkippedWithoutACall() {
        val seen = ArrayList<String>()
        ProviderChain.run(FreeProviders.ordered(), { n -> if (n == "glm53c") "f2" else "" }, { id -> id != "pollinations" }, "s", "u", Cooldowns(), { 0L }, glmFirst = true) { p, _, _, _ ->
            seen.add(p.id); NetAnswer(200, ok)
        }
        assertEquals(listOf("glm53c"), seen)
    }
}
