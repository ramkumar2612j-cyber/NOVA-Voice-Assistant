package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.186 JARVIS BRAIN: pure text rules. Written, NOT RUN. */
class JarvisBrainTest {
    @Test fun shortOrEmptyIsNotAsked() {
        assertFalse(JarvisBrain.worthAsking(""))
        assertFalse(JarvisBrain.worthAsking("torch on"))
        assertFalse(JarvisBrain.worthAsking("volume kam karo"))
    }

    @Test fun longSentenceIsAsked() {
        assertTrue(JarvisBrain.worthAsking("flipkart par iphone ka sabse sasta price dhundh ke batao"))
    }

    @Test fun parsesAgent() {
        val d = JarvisBrain.parse("{\"mode\":\"agent\",\"understood\":\"Flipkart par sasta iphone dhundhna\"}")
        assertNotNull(d)
        assertEquals(JarvisBrain.Mode.AGENT, d!!.mode)
        assertEquals("Flipkart par sasta iphone dhundhna", d.understood)
    }

    @Test fun parsesAnswerAndTools() {
        assertEquals(JarvisBrain.Mode.ANSWER, JarvisBrain.parse("{\"mode\":\"answer\",\"understood\":\"x\"}")!!.mode)
        assertEquals(JarvisBrain.Mode.TOOLS, JarvisBrain.parse("{\"mode\": \"tools\", \"understood\": \"x\"}")!!.mode)
    }

    @Test fun garbageGivesNull() {
        assertNull(JarvisBrain.parse(null))
        assertNull(JarvisBrain.parse(""))
        assertNull(JarvisBrain.parse("sorry I cannot"))
        assertNull(JarvisBrain.parse("{\"mode\":\"fly\",\"understood\":\"x\"}"))
    }
}
