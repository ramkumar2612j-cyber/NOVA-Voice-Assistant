package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class JarvisPairingTest {
    @Test fun roundTrip() {
        val p = JarvisPairing.parse(JarvisPairing.encode("https://abc-def.trycloudflare.com", "tok_ABCdef123456"))
        assertNotNull(p)
        assertEquals("https://abc-def.trycloudflare.com", p!!.address)
        assertEquals("tok_ABCdef123456", p.token)
    }
    @Test fun findsCodeInsideOtherText() {
        val code = JarvisPairing.encode("https://a-b.trycloudflare.com/", "tok_ABCdef123456")
        val p = JarvisPairing.parse("copy ye hai:\n" + code + "\nbas")
        assertNotNull(p); assertEquals("https://a-b.trycloudflare.com", p!!.address)
    }
    @Test fun tokenOptional() {
        val p = JarvisPairing.parse(JarvisPairing.encode("https://a-b.trycloudflare.com", ""))
        assertNotNull(p); assertEquals("", p!!.token)
    }
    @Test fun badInputsRejected() {
        assertNull(JarvisPairing.parse(null))
        assertNull(JarvisPairing.parse(""))
        assertNull(JarvisPairing.parse("hello"))
        assertNull(JarvisPairing.parse("NOVA-JARVIS:"))
        assertNull(JarvisPairing.parse("NOVA-JARVIS:!!!notbase64"))
        assertNull(JarvisPairing.parse(JarvisPairing.encode("http://evil.example.com", "tok_ABCdef123456")))     // http not allowed
        assertNull(JarvisPairing.parse(JarvisPairing.encode("https://a.b.com", "short")))                          // token too short
        assertNull(JarvisPairing.parse(JarvisPairing.encode("https://a.b.com", "has space in token")))
        assertNull(JarvisPairing.parse("NOVA-JARVIS:" + "A".repeat(3000)))
    }
}
