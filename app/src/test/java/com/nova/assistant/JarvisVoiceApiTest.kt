package com.nova.assistant

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
    @Test fun stopMovesGenerationOn() {
        val g = JarvisSpeaker.generation()
        JarvisSpeaker.stop()
        assertTrue(g != JarvisSpeaker.generation())
        JarvisSpeaker.pause(1000L); assertTrue(JarvisSpeaker.isPaused(1001L)); assertFalse(JarvisSpeaker.isPaused(1000L + JarvisVoiceApi.PAUSE_MS)); JarvisSpeaker.resume(); assertFalse(JarvisSpeaker.isPaused())
    }
}
