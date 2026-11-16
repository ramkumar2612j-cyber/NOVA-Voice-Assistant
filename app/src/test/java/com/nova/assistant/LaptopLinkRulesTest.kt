package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaptopLinkRulesTest {
    private val key = "A".repeat(43)

    @Test fun parseCodeAcceptsValid() {
        assertEquals(key, LaptopLinkRules.parseCode("JARVIS-LINK:$key"))
        assertEquals(key, LaptopLinkRules.parseCode("  jarvis-link: $key \n"))
    }

    @Test fun parseCodeRejectsBad() {
        assertNull(LaptopLinkRules.parseCode(null))
        assertNull(LaptopLinkRules.parseCode(""))
        assertNull(LaptopLinkRules.parseCode(key))                       // prefix missing
        assertNull(LaptopLinkRules.parseCode("JARVIS-LINK:" + "A".repeat(42)))
        assertNull(LaptopLinkRules.parseCode("JARVIS-LINK:" + "A".repeat(44)))
        assertNull(LaptopLinkRules.parseCode("JARVIS-LINK:" + "A".repeat(42) + "="))
        assertNull(LaptopLinkRules.parseCode("OTHER:$key"))
        assertNull(LaptopLinkRules.parseCode("JARVIS-LINK:$key" + " ".repeat(300)))
    }

    @Test fun laptopPhrase() {
        assertEquals("chrome kholo", LaptopLinkRules.laptopCommand("laptop par chrome kholo"))
        assertEquals("screenshot lo", LaptopLinkRules.laptopCommand("Jarvis Laptop PE screenshot lo"))
        assertEquals("youtube par gaana search karo", LaptopLinkRules.laptopCommand("laptop mein youtube par gaana search karo"))
        assertNull(LaptopLinkRules.laptopCommand("laptop par"))
        assertNull(LaptopLinkRules.laptopCommand("mera laptop kharab hai"))
        assertNull(LaptopLinkRules.laptopCommand("chrome kholo"))
        assertNull(LaptopLinkRules.laptopCommand(null))
    }

    @Test fun dedupeAcceptsOnceThenAgainAfterTtl() {
        val d = LaptopLinkRules.Dedupe(300_000L)
        assertTrue(d.firstTime("a", 1_000L))
        assertFalse(d.firstTime("a", 2_000L))
        assertTrue(d.firstTime("b", 2_000L))
        assertTrue(d.firstTime("a", 400_000L))   // older than 5 minutes: forgotten
    }

    @Test fun topicsAreStableAndDifferByDirection() {
        val a = LaptopLink.topic(key, "l2p"); val b = LaptopLink.topic(key, "p2l")
        assertEquals(47, a.length)   // "jarvis-" (7) + 40 hex
        assertTrue(a.startsWith("jarvis-"))
        assertFalse(a == b)
        assertEquals(a, LaptopLink.topic(key, "l2p"))
    }

    @Test fun sealOpenRoundTripAndRejects() {
        val t = LaptopLink.topic(key, "p2l")
        val env = LaptopLink.seal(key, t, "cmd", "chrome kholo", "")
        val m = LaptopLink.open(key, t, env)
        assertNotNull(m)
        assertEquals("cmd", m!!.getString("type"))
        assertEquals("chrome kholo", m.getString("text"))
        assertNull(LaptopLink.open(key, LaptopLink.topic(key, "l2p"), env))        // wrong topic (AAD)
        assertNull(LaptopLink.open("B".repeat(43), t, env))                         // wrong key
        assertNull(LaptopLink.open(key, t, env, System.currentTimeMillis() + 600_000L))   // stale
        assertNull(LaptopLink.open(key, t, env.dropLast(3) + "AAA"))               // tampered
    }
}
