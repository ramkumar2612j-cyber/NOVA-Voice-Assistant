package com.nova.assistant

import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class JarvisVoiceApiTest {
    @Test fun cleanBaseRules() {
        assertEquals("https://jarvis.example.com", JarvisVoiceApi.cleanBase("https://jarvis.example.com"))
        assertEquals("https://jarvis.example.com", JarvisVoiceApi.cleanBase("  https://jarvis.example.com/  "))
        assertEquals("https://jarvis.example.com", JarvisVoiceApi.cleanBase("https://jarvis.example.com/tts"))
        assertEquals("https://x.hf.space/api", JarvisVoiceApi.cleanBase("https://x.hf.space/api/"))
        assertEquals("https://1.2.3.4:8000", JarvisVoiceApi.cleanBase("https://1.2.3.4:8000"))
        assertEquals("http://127.0.0.1:8000", JarvisVoiceApi.cleanBase("http://127.0.0.1:8000"))
        for (bad in listOf("", "jarvis.example.com", "http://jarvis.example.com", "ftp://a.b", "https://", "https://user:pw@a.b.com", "https://a.b.com/x?token=1", "https://a.b.com/#x",
            "https://a b.com", "https://localhost", "https://a.b.com:99999x", "javascript:alert(1)", "https://a.b.com\\evil", "http://192.168.1.5:8000")) assertEquals(bad, "", JarvisVoiceApi.cleanBase(bad))
    }
    @Test fun wavAndEnglishAndReasons() {
        val wav = ByteArray(400); "RIFF".toByteArray().copyInto(wav, 0); "WAVE".toByteArray().copyInto(wav, 8)
        assertTrue(JarvisVoiceApi.isWav(wav)); assertFalse(JarvisVoiceApi.isWav(ByteArray(400))); assertFalse(JarvisVoiceApi.isWav("<html>".toByteArray())); assertFalse(JarvisVoiceApi.isWav(null))
        assertTrue(JarvisVoiceApi.englishOk("The workshop is ready."))
        assertTrue(JarvisVoiceApi.englishOk("Order 4417 ships Tuesday."))
        assertFalse(JarvisVoiceApi.englishOk("नमस्ते, आप कैसे हैं"))
        assertFalse(JarvisVoiceApi.englishOk("hello नमस्ते"))
        assertFalse(JarvisVoiceApi.englishOk("12345 !!!"))
        assertEquals(300, JarvisVoiceApi.DEFAULT_MAX_CHARS); assertEquals(40, JarvisVoiceApi.clampMaxChars(1)); assertEquals(800, JarvisVoiceApi.clampMaxChars(99999))
        assertTrue(JarvisVoiceApi.reason(401).contains("token")); assertTrue(JarvisVoiceApi.reason(404).contains("URL")); assertTrue(JarvisVoiceApi.reason(598).contains("internet"))
        assertEquals("""{"text":"hi"}""", JarvisVoiceApi.body("hi"))
    }
    @Test fun wantsDecision() {
        val u = "https://jarvis.example.com"
        fun w(on: Boolean = true, url: String = u, any: Boolean = false, en: Boolean = true, t: String = "The workshop is ready.", max: Int = 300, paused: Boolean = false) = JarvisSpeaker.wants(on, url, any, en, t, max, paused)
        assertTrue(w())
        assertFalse(w(on = false)); assertFalse(w(url = "")); assertFalse(w(url = "http://bad.com")); assertFalse(w(paused = true)); assertFalse(w(t = "")); assertFalse(w(t = "a".repeat(301)))
        assertFalse(w(en = false))                    // Hindi UI language: no Jarvis by default
        assertTrue(w(en = false, any = true))         // ... unless the owner allowed it (plain letters only)
        assertFalse(w(en = false, any = true, t = "नमस्ते"))   // Devanagari never
    }
    @Test fun clientAgainstLocalServer() {
        val wav = ByteArray(2000); "RIFF".toByteArray().copyInto(wav, 0); "WAVE".toByteArray().copyInto(wav, 8)
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var seenAuth: String? = null; var seenBody = ""
        srv.createContext("/tts") { ex ->
            seenAuth = ex.requestHeaders.getFirst("Authorization"); seenBody = String(ex.requestBody.readBytes())
            if (seenAuth != "Bearer secret-token-123") { ex.sendResponseHeaders(401, -1); ex.close() }
            else if (seenBody.contains("html")) { val h = "<html>oops</html>".toByteArray(); ex.sendResponseHeaders(200, h.size.toLong()); ex.responseBody.use { it.write(h) } }
            else { ex.responseHeaders.add("Content-Type", "audio/wav"); ex.sendResponseHeaders(200, wav.size.toLong()); ex.responseBody.use { it.write(wav) } }
        }
        srv.createContext("/health") { ex -> val b = """{"ok":true}""".toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) } }
        srv.start()
        try {
            val base = "http://127.0.0.1:" + srv.address.port
            val ok = JarvisVoiceClient.speech(base, "secret-token-123", "The workshop is ready.")
            assertNotNull(ok.why, ok.bytes); assertEquals(2000, ok.bytes!!.size); assertEquals("Bearer secret-token-123", seenAuth)
            assertEquals("The workshop is ready.", JSONObject(seenBody).getString("text"))
            val bad = JarvisVoiceClient.speech(base, "wrong", "hi"); assertNull(bad.bytes); assertTrue(bad.why, bad.why.contains("token"))
            val notWav = JarvisVoiceClient.speech(base, "secret-token-123", "html page"); assertNull(notWav.bytes)
            val h = JarvisVoiceClient.call(base, "", "GET", JarvisVoiceApi.healthUrl(base), null, 1000); assertEquals(200, h.http)
            // the token never goes to another host / a non-saved address
            assertEquals(0, JarvisVoiceClient.call(base, "t", "GET", "http://127.0.0.1:1/health", null, 1000).http)
            assertEquals(0, JarvisVoiceClient.call("https://a.b.com", "t", "GET", base + "/health", null, 1000).http)
        } finally { srv.stop(0) }
        assertEquals(598, JarvisVoiceClient.call("http://127.0.0.1:1", "", "GET", "http://127.0.0.1:1/health", null, 1000).http)   // refused = "no internet / server"
    }
    @Test fun stopMovesGenerationOn() {
        val g = JarvisSpeaker.generation()
        JarvisSpeaker.stop()
        assertTrue(g != JarvisSpeaker.generation())
        JarvisSpeaker.pause(1000L); assertTrue(JarvisSpeaker.isPaused(1001L)); assertFalse(JarvisSpeaker.isPaused(1000L + JarvisVoiceApi.PAUSE_MS)); JarvisSpeaker.resume(); assertFalse(JarvisSpeaker.isPaused())
    }
}
