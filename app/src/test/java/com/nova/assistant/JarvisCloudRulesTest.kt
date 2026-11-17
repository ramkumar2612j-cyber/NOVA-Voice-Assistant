package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * v7.217 CENTRAL CLOUD BRAIN rules tests. Pure rules only — network/Android nahi.
 * LWW fixture vectors EXACTLY wahi hain jo desktop/test/cloud.test.js aur tools/test_cloud_brain_contract.py use karte hain
 * (teeno taraf same winner nikalna chahiye).
 */
class JarvisCloudRulesTest {

    private fun code(addr: String, tok: String): String =
        "JARVIS-CLOUD:" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"a\":\"$addr\",\"p\":\"$tok\"}".toByteArray())

    // ---------- pairing code ----------

    @Test fun codeParses() {
        val p = JarvisCloud.Rules.parseCode(code("https://brain.example.com", "ABCdef123_-456789012345678"))
        assertNotNull(p)
        assertEquals("https://brain.example.com", p!!.first)
        assertEquals("ABCdef123_-456789012345678", p.second)
    }

    @Test fun codeRejectsHttp() {
        assertNull(JarvisCloud.Rules.parseCode(code("http://brain.example.com", "ABCdef123_-456789012345678")))
    }

    @Test fun codeAcceptsLocalhostHttp() {
        assertNotNull(JarvisCloud.Rules.parseCode(code("http://127.0.0.1:8787", "ABCdef123_-456789012345678")))
    }

    @Test fun codeRejectsGarbage() {
        assertNull(JarvisCloud.Rules.parseCode(null))
        assertNull(JarvisCloud.Rules.parseCode(""))
        assertNull(JarvisCloud.Rules.parseCode("JARVIS-CLOUD:!!!nothbase64!!!"))
        assertNull(JarvisCloud.Rules.parseCode("JARVIS-LINK:ABCDEFGH"))
        assertNull(JarvisCloud.Rules.parseCode(code("https://a b.com", "x")))   // whitespace host
        assertNull(JarvisCloud.Rules.parseCode(code("", "ABCdef123_-456789012345678")))   // khali addr
    }

    // ---------- cleanBase ----------

    @Test fun cleanBaseBasics() {
        assertEquals("https://b.example.com", JarvisCloud.Rules.cleanBase("https://b.example.com/"))
        assertEquals("", JarvisCloud.Rules.cleanBase("ftp://b.example.com"))
        assertEquals("", JarvisCloud.Rules.cleanBase("https://user:pass@b.example.com"))
        assertEquals("http://127.0.0.1:8787", JarvisCloud.Rules.cleanBase("http://127.0.0.1:8787"))
    }

    // ---------- LWW (fixtures = JS/python mirror) ----------

    @Test fun lwwHigherVersionWins() {
        val w = JarvisCloud.Rules.lwwWinner("mine", 2, 1000, "d1", "theirs", 1, 2000, "d2")
        assertEquals("mine", w)
    }

    @Test fun lwwSameVersionNewerTsWins() {
        val w = JarvisCloud.Rules.lwwWinner("mine", 1, 1000, "d1", "theirs", 1, 2000, "d2")
        assertEquals("theirs", w)
    }

    @Test fun lwwTieBreaksByDeviceId() {
        // (1,1000,d1) vs (1,1000,d2): tuple compare — d2 bada, to theirs jeetega
        val w = JarvisCloud.Rules.lwwWinner("mine", 1, 1000, "d1", "theirs", 1, 1000, "d2")
        assertEquals("theirs", w)
        val w2 = JarvisCloud.Rules.lwwWinner("mine", 1, 1000, "d2", "theirs", 1, 1000, "d1")
        assertEquals("mine", w2)
    }

    @Test fun lwwLowerVersionLosesEvenWithNewerTs() {
        val w = JarvisCloud.Rules.lwwWinner("mine", 1, 999_999, "d9", "theirs", 2, 1, "d1")
        assertEquals("theirs", w)
    }

    // ---------- retry backoff ----------

    @Test fun retryDelay() {
        assertEquals(0L, JarvisCloud.Rules.retryDelayMs(0))
        assertEquals(60_000L, JarvisCloud.Rules.retryDelayMs(1))
        assertEquals(5 * 60_000L, JarvisCloud.Rules.retryDelayMs(2))
        assertEquals(15 * 60_000L, JarvisCloud.Rules.retryDelayMs(3))
        assertEquals(15 * 60_000L, JarvisCloud.Rules.retryDelayMs(99))
    }

    // ---------- privacy ----------

    @Test fun privateNeverUploads() {
        assertFalse(JarvisCloud.Rules.uploadable("private", true))
        assertFalse(JarvisCloud.Rules.uploadable("private", false))
        assertTrue(JarvisCloud.Rules.uploadable("all", true))
        assertTrue(JarvisCloud.Rules.uploadable("all", false))
    }

    @Test fun platformIsAndroid() {
        assertEquals("android", JarvisCloud.Rules.platform())
    }
}
