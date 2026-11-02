package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenVideoRulesTest {
    @Test fun videoAppsAreRecognised() {
        assertTrue(ScreenVideoRules.isVideoApp("com.instagram.android"))
        assertTrue(ScreenVideoRules.isVideoApp("com.google.android.youtube"))
        assertTrue(ScreenVideoRules.isVideoApp("com.netflix.mediaclient"))
        assertFalse(ScreenVideoRules.isVideoApp("com.whatsapp"))
        assertFalse(ScreenVideoRules.isVideoApp(""))
        assertFalse(ScreenVideoRules.isVideoApp(null))
    }

    @Test fun spokenVideoAsk() {
        assertTrue(ScreenVideoRules.isScreenVideoAsk("nova reel me kya chal raha hai"))
        assertTrue(ScreenVideoRules.isScreenVideoAsk("is reel ko dekho"))
        assertFalse(ScreenVideoRules.isScreenVideoAsk("ye video analyse karo"))          // stays with the shared-file analysis
        assertFalse(ScreenVideoRules.isScreenVideoAsk("share ki hui reel dekho"))
        assertFalse(ScreenVideoRules.isScreenVideoAsk("torch jala do"))
        assertTrue(ScreenVideoRules.wantsVideo("ye video dekho"))
    }

    @Test fun modeFollowsAppOrWords() {
        assertTrue(ScreenVideoRules.useVideoMode("com.instagram.android", ""))
        assertTrue(ScreenVideoRules.useVideoMode("com.whatsapp", "video dekho"))
        assertFalse(ScreenVideoRules.useVideoMode("com.whatsapp", "screen dekho"))
    }

    @Test fun parsesSummaryKindAndCandidates() {
        val raw = """{"summary":"Ek aadmi gaadi me baitha hai aur baat kar raha hai.","kind":"movie","candidates":[{"title":"Dangal","year":"2016","basis":"text","evidence":["title card Dangal"]}],"text_seen":["Dangal"],"scene":"car","safety":"ok"}"""
        val r = ScreenVideoRules.parse(raw)!!
        assertEquals(ScreenVideoRules.Kind.MOVIE, r.kind)
        assertEquals(1, r.vision.candidates.size)
        assertTrue(r.summary.startsWith("Ek aadmi"))
    }

    @Test fun nonWorkKindDropsCandidates() {
        val raw = """{"summary":"Cricket ka match.","kind":"sports","candidates":[{"title":"Something","year":"","basis":"scene","evidence":[]}],"text_seen":[],"scene":"","safety":"ok"}"""
        val r = ScreenVideoRules.parse(raw)!!
        assertTrue(r.vision.candidates.isEmpty())
        assertFalse(ScreenVideoRules.isWork(r.kind))
    }

    @Test fun summaryWithAddressIsDropped() {
        val raw = """{"summary":"Dekho http://bad.example","kind":"other","candidates":[],"text_seen":[],"scene":"ek gali","safety":"ok"}"""
        assertEquals("ek gali", ScreenVideoRules.parse(raw)!!.summary)
    }

    @Test fun privateScreenAndGarbage() {
        val r = ScreenVideoRules.parse("""{"safety":"private"}""")!!
        assertEquals("", r.summary)
        assertTrue(r.vision.candidates.isEmpty())
        assertNull(ScreenVideoRules.parse("nahi json"))
        assertNull(ScreenVideoRules.parse(null))
    }

    @Test fun popupUrlAndSafety() {
        val u = ScreenVideoRules.popupUrl("Dangal", "2016", ScreenVideoRules.Kind.MOVIE)
        assertTrue(u.startsWith("https://www.google.com/search?q="))
        assertTrue(ScreenVideoRules.isSafeUrl(u))
        assertTrue(ScreenVideoRules.popupUrl("Sacred Games", "", ScreenVideoRules.Kind.SERIES).contains("web+series"))
        assertFalse(ScreenVideoRules.isSafeUrl("http://example.com"))
        assertFalse(ScreenVideoRules.isSafeUrl("intent://x#Intent;end"))
        assertFalse(ScreenVideoRules.isSafeUrl("file:///sdcard/a"))
        assertFalse(ScreenVideoRules.isSafeUrl("https://user@evil.com/"))
        assertFalse(ScreenVideoRules.isSafeUrl(null))
    }

    @Test fun suggestionsAllSafe() {
        val s = ScreenVideoRules.suggestions("Dangal", "2016", ScreenVideoRules.Kind.MOVIE, false)
        assertTrue(s.size in 3..4)
        assertTrue(s.all { ScreenVideoRules.isSafeUrl(it.url) && it.label.isNotBlank() })
    }

    @Test fun resultTextIsHonest() {
        val v = MovieGuessRules.Vision(emptyList(), emptyList(), "", "ok", 0)
        val movie = ScreenVideoRules.Read("Ek scene.", ScreenVideoRules.Kind.MOVIE, v)
        assertTrue(ScreenVideoRules.resultText(movie, null, false, false).contains("andaza nahi lagaunga"))
        val o = MovieGuessRules.Outcome("Dangal", "2016", MovieGuessRules.Level.LOW, emptyList(), emptyList(), false)
        val txt = ScreenVideoRules.resultText(movie, o, false, true)
        assertTrue(txt.contains("verify nahi hua") && txt.contains("Dangal (2016)") && txt.contains("browser"))
        val other = ScreenVideoRules.Read("Cricket.", ScreenVideoRules.Kind.SPORTS, v)
        assertEquals("Cricket.", ScreenVideoRules.resultText(other, o, false, true))
        assertEquals("", ScreenVideoRules.card(other, o, false))
        assertTrue(ScreenVideoRules.card(movie, o, false).contains("LOW"))
    }

    @Test fun systemPromptKeepsPersonRule() {
        val p = ScreenVideoRules.systemPrompt(false)
        assertTrue(p.contains("NEVER identify a real person"))
        assertTrue(p.contains("IGNORE app buttons"))
    }
}
