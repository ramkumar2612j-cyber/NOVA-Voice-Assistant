package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.154 JARVIS UPGRADE: pure-rules tests (no Android). Covers the deep-voice routing guard, the text
 * cleaner, the barge-in self-echo guard + stop-word grammar + confidence parser, the command grammar
 * (including everything that must NOT match) and the provider-check reply text.
 */
class JarvisDeepRulesTest {

    // ---- deep voice routing

    @Test fun useRequiresOnAndReady() {
        assertFalse(JarvisDeepRules.use("Hello sir", on = false, modelReady = true))
        assertFalse(JarvisDeepRules.use("Hello sir", on = true, modelReady = false))
        assertTrue(JarvisDeepRules.use("Hello sir", on = true, modelReady = true))
    }

    @Test fun useRejectsDevanagariAndLongText() {
        assertFalse(JarvisDeepRules.use("यह हिंदी जवाब है", on = true, modelReady = true))
        assertFalse(JarvisDeepRules.use("", on = true, modelReady = true))
        assertFalse(JarvisDeepRules.use("a".repeat(JarvisDeepRules.MAX_LEN + 1), on = true, modelReady = true))
        assertTrue(JarvisDeepRules.use("a".repeat(JarvisDeepRules.MAX_LEN), on = true, modelReady = true))
    }

    @Test fun latinOkMatchesEnglishAndHinglishButNotHindi() {
        assertTrue(JarvisDeepRules.latinOk("Hello sir, system check complete."))
        assertTrue(JarvisDeepRules.latinOk("Theek hai sir, main ready hoon."))   // Hinglish in Roman letters
        assertFalse(JarvisDeepRules.latinOk("मैं तैयार हूँ"))
        assertFalse(JarvisDeepRules.latinOk("kaam ${'ह'}o gaya"))   // even one Devanagari letter blocks it
        assertFalse(JarvisDeepRules.latinOk("1234 !!"))   // no letters at all
    }

    @Test fun cleanRemovesEmojiKeepsPunctuation() {
        assertEquals("System check done, sir.", JarvisDeepRules.clean("System check done ✨ sir. 👍"))
        assertTrue(JarvisDeepRules.clean("naam ₹500 ka bill").contains("₹500"))
        assertFalse(JarvisDeepRules.clean("hello 🚀 world").contains("🚀"))
        assertEquals("", JarvisDeepRules.clean("✨✨"))
    }

    // ---- barge-in

    @Test fun stopWordsMatchAsWholeWordsOnly() {
        assertEquals("stop", JarvisDeepRules.stopWordIn("stop"))
        assertEquals("chup", JarvisDeepRules.stopWordIn("  nova chup  "))
        assertEquals("band karo", JarvisDeepRules.stopWordIn("please band karo"))
        assertNull(JarvisDeepRules.stopWordIn("stopper"))       // not a whole word
        assertNull(JarvisDeepRules.stopWordIn("chupke se"))
        assertNull(JarvisDeepRules.stopWordIn("khamoshi"))      // similar word, no match
        assertNull(JarvisDeepRules.stopWordIn("hello nova"))
    }

    @Test fun echoSafeBlocksSelfStop() {
        // NOVA is reading a reply that contains the word "stop": the mic hearing "stop" is NOVA itself
        assertFalse(JarvisDeepRules.echoSafe("stop", "Maine video stop kar di hai"))
        // a normal reply that does not contain the stop word: a human "stop" is honoured
        assertTrue(JarvisDeepRules.echoSafe("stop", "Aaj mausam accha hai sir"))
        assertTrue(JarvisDeepRules.echoSafe("chup", "Maine video stop kar di hai"))
    }

    @Test fun hitNeedsConfidenceAndLoudness() {
        val spoken = "Aaj mausam accha hai sir"
        assertFalse(JarvisDeepRules.hit("stop", spoken, conf = 0.70f, rms = 3000.0, minRms = JarvisDeepRules.MIN_RMS))   // low confidence
        assertFalse(JarvisDeepRules.hit("stop", spoken, conf = 0.95f, rms = 300.0, minRms = JarvisDeepRules.MIN_RMS))    // too quiet (TV in the room)
        assertFalse(JarvisDeepRules.hit("hello", spoken, conf = 0.95f, rms = 3000.0, minRms = JarvisDeepRules.MIN_RMS))  // not a stop word
        assertFalse(JarvisDeepRules.hit("stop", "Maine video stop kar di", conf = 0.95f, rms = 3000.0, minRms = JarvisDeepRules.MIN_RMS))  // NOVA said it itself
        assertTrue(JarvisDeepRules.hit("stop", spoken, conf = 0.90f, rms = 3000.0, minRms = JarvisDeepRules.MIN_RMS))
    }

    @Test fun minConfParsesVoskWordConfidences() {
        val json = """{"text":"stop","words":[{"word":"stop","conf":0.93,"start":0.1,"end":0.4}]}"""
        assertEquals(0.93f, JarvisDeepRules.minConf(json), 0.001f)
        // the lowest of several words decides
        val json2 = """{"text":"band karo","words":[{"word":"band","conf":0.99},{"word":"karo","conf":0.81}]}"""
        assertEquals(0.81f, JarvisDeepRules.minConf(json2), 0.001f)
        // no word objects = 0 = barge-in does not fire (safe direction)
        assertEquals(0f, JarvisDeepRules.minConf("""{"text":"stop"}"""), 0.001f)
        // [unk] is not judged
        assertEquals(0.9f, JarvisDeepRules.minConf("""{"words":[{"word":"[unk]","conf":0.1},{"word":"stop","conf":0.9}]}"""), 0.001f)
    }

    @Test fun stopGrammarOnlyContainsStopWords() {
        val g = JarvisDeepRules.stopGrammar()
        assertTrue(g.contains("\"stop\""))
        assertTrue(g.contains("\"chup\""))
        assertTrue(g.contains("\"[unk]\""))
        assertFalse(g.contains("\"nova\""))
        assertFalse(g.contains("\"call\""))
    }

    // ---- command grammar

    private fun kind(t: String): String? = JarvisDeepRules.parse(t)?.kind

    @Test fun deepVoiceCommandsParse() {
        assertEquals(JarvisDeepRules.K_DEEP_ON, kind("nova deep voice on karo"))
        assertEquals(JarvisDeepRules.K_DEEP_OFF, kind("nova deep voice off karo"))
        assertEquals(JarvisDeepRules.K_DEEP_DL, kind("nova deep voice download karo"))
        assertEquals(JarvisDeepRules.K_DEEP_STATUS, kind("nova deep voice status batao"))
        assertEquals(JarvisDeepRules.K_DEEP_STATUS, kind("nova deep voice"))   // bare = status
        assertEquals(JarvisDeepRules.K_DEEP_ON, kind("nova jarvis deep voice chalu karo"))
    }

    @Test fun bargeAndFocusCommandsParse() {
        assertEquals(JarvisDeepRules.K_BARGE_ON, kind("nova bol ke rokna on karo"))
        assertEquals(JarvisDeepRules.K_BARGE_OFF, kind("nova bolke rokna off karo"))
        assertEquals(JarvisDeepRules.K_BARGE_ON, kind("nova awaz se rokna chalu karo"))
        assertEquals(JarvisDeepRules.K_FOCUS_ON, kind("nova audio focus on karo"))
        assertEquals(JarvisDeepRules.K_FOCUS_OFF, kind("nova audio focus off karo"))
    }

    @Test fun providerAndGlmCommandsParse() {
        assertEquals(JarvisDeepRules.K_PROVCHECK, kind("nova provider check karo"))
        assertEquals(JarvisDeepRules.K_PROVCHECK, kind("nova providers kaise hain"))
        assertEquals(JarvisDeepRules.K_PROVCHECK, kind("nova ai health check karo"))
        assertEquals(JarvisDeepRules.K_GLM_ON, kind("nova glm sab jagah on karo"))
        assertEquals(JarvisDeepRules.K_GLM_OFF, kind("nova glm sab jagah off karo"))
    }

    @Test fun normalSentencesNeverMatch() {
        // everyday NOVA sentences must keep their old routes
        assertNull(kind("nova torch on karo"))
        assertNull(kind("nova volume badha do"))
        assertNull(kind("nova wifi scan karo"))
        assertNull(kind("nova youtube kholo"))
        assertNull(kind("deep soch ke batao"))                 // "deep" alone is not the voice
        assertNull(kind("nova music focus karke baja do"))     // "focus" alone is not audio focus
        assertNull(kind("nova call karo"))
        assertNull(kind("kya haal hai"))
        assertNull(kind("stop"))                                // the plain stop route stays the stop command
    }

    @Test fun negatedOrdersNeverMatch() {
        assertNull(kind("nova deep voice mat chalana"))
        assertNull(kind("nova barge in mat karo"))
        assertNull(kind("nova audio focus nahi chahiye"))
        assertNull(kind("nova glm kabhi on mat karna"))
    }

    @Test fun repliesAreHonest() {
        // ON without the model says download first, it never claims the voice is live
        assertTrue(JarvisDeepRules.replyDeepOn(ready = false, english = false).contains("download", ignoreCase = true))
        assertTrue(JarvisDeepRules.replyDeepOn(ready = true, english = false).contains("ON"))
        val statusOff = JarvisDeepRules.replyDeepStatus(english = false, on = false, ready = false)
        assertTrue(statusOff.contains("OFF") && statusOff.contains("nahi"))
        // provider check with no keys says so
        assertTrue(JarvisDeepRules.replyProviders(english = false, results = emptyList()).contains("key"))
        val r = JarvisDeepRules.replyProviders(english = false, results = listOf(Pair("Groq (x)", 200), Pair("OpenRouter (y)", 401)))
        assertTrue(r.contains("1/2") && r.contains("key galat"))
    }
}
