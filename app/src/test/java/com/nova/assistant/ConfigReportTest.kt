package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigReportTest {
    private fun st(
        gemini: Boolean = false, groq: Boolean = false, openrouter: Boolean = false, zai: Boolean = false, keyless: Boolean = false,
        chainOn: Boolean = true, mic: Boolean = true, acc: Boolean = true, notif: Boolean = true, overlay: Boolean = true, local: Boolean = false
    ) = ConfigReport.State(gemini, groq, openrouter, zai, keyless, chainOn, mic, acc, notif, overlay, local)

    @Test fun queryPhrases() {
        assertTrue(ConfigReport.isQuery("nova setup check"))
        assertTrue(ConfigReport.isQuery("Kya missing hai"))
        assertTrue(ConfigReport.isQuery("missing keys"))
        assertFalse(ConfigReport.isQuery("torch jala do"))
        assertFalse(ConfigReport.isQuery("setup check karke whatsapp kholo"))
    }

    @Test fun agentNeedsAKeyedProvider() {
        assertFalse(ConfigReport.agentReady(st()))
        assertFalse(ConfigReport.agentReady(st(keyless = true)))
        assertTrue(ConfigReport.agentReady(st(gemini = true)))
        assertTrue(ConfigReport.agentReady(st(groq = true)))
        assertFalse(ConfigReport.agentReady(st(groq = true, chainOn = false)))
    }

    @Test fun chatCanUseKeylessOrOfflineModel() {
        assertFalse(ConfigReport.chatReady(st()))
        assertTrue(ConfigReport.chatReady(st(keyless = true)))
        assertTrue(ConfigReport.chatReady(st(local = true)))
    }

    @Test fun missingListsEveryGap() {
        val m = ConfigReport.missing(st(mic = false, acc = false, notif = false, overlay = false), true)
        assertTrue(m.any { it.startsWith("Microphone") })
        assertTrue(m.any { it.startsWith("Accessibility") })
        assertTrue(m.any { it.startsWith("AI key for phone jobs") })
        assertTrue(m.any { it.startsWith("Chat answers") })
        assertTrue(m.any { it.startsWith("Notification access") })
        assertTrue(m.any { it.startsWith("Display over other apps") })
    }

    @Test fun onlyGeminiMissingIsReportedAsBackupMode() {
        val m = ConfigReport.missing(st(groq = true), true)
        assertEquals(1, m.size)
        assertTrue(m[0].startsWith("Gemini key"))
    }

    @Test fun reportNeverClaimsEverythingWorks() {
        val ok = ConfigReport.report(st(gemini = true), true)
        assertTrue(ok.contains("configured"))
        assertTrue(ok.contains("out of quota"))
        val bad = ConfigReport.report(st(), false)
        assertTrue(bad.startsWith("Set nahi hai"))
    }

    @Test fun noAiMessageNamesTheFix() {
        assertTrue(ConfigReport.noAiForAgent(true).contains("Setup"))
        assertTrue(ConfigReport.noAiForAgent(false).contains("Setup"))
    }
}
