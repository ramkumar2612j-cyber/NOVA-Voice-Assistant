package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class PhoneActionRulesTest {
    @Test fun acceptsOnlyStrictKnownJsonActions() {
        val torch = PhoneActionRules.parse("{\"tool\":\"torch\",\"args\":{\"on\":true}}")
        assertNotNull(torch)
        assertEquals("torch", torch!!.tool)
        assertEquals(true, torch.args["on"])
        assertEquals("global_action", PhoneActionRules.parse("{\"tool\":\"global_action\",\"args\":{\"action\":\"back\"}}")?.tool)
    }

    @Test fun rejectsHostileUnknownOrExtraSchemaFields() {
        assertNull(PhoneActionRules.parse("not json"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"tap\",\"args\":{\"label\":\"Pay\"}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"pay\",\"args\":{}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"torch\",\"args\":{\"on\":true,\"execute\":\"anything\"}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"torch\",\"args\":{\"on\":true},\"confirm\":true}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"none\",\"args\":{}}"))
    }

    @Test fun validatesArgumentTypesAndRanges() {
        assertNull(PhoneActionRules.parse("{\"tool\":\"torch\",\"args\":{\"on\":\"true\"}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"alarm\",\"args\":{\"hour\":25,\"minute\":0}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"timer\",\"args\":{\"seconds\":0}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"camera\",\"args\":{\"mode\":\"delete\",\"facing\":\"front\"}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"media\",\"args\":{\"action\":\"send\"}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"open_url\",\"args\":{\"url\":\" \"}}"))
    }

    @Test fun promptNeverOffersTapTypeOrPaymentTools() {
        val prompt = PhoneActionRules.systemPrompt(false).lowercase()
        assertFalse(prompt.contains("tap("))
        assertFalse(prompt.contains("type("))
        assertFalse(prompt.contains("pay("))
        assertFalse(PhoneActionRules.parse("{\"tool\":\"tap\",\"args\":{\"label\":\"pay\"}}") != null)
    }

    // ---- Part 9 (NOT RUN: Kotlin was not compiled in the sandbox)
    @Test fun part9AcceptsScreenshotRingerAndRotation() {
        assertEquals("screenshot", PhoneActionRules.parse("{\"tool\":\"global_action\",\"args\":{\"action\":\"screenshot\"}}")?.args?.get("action"))
        assertEquals("vibrate", PhoneActionRules.parse("{\"tool\":\"ringer\",\"args\":{\"mode\":\"vibrate\"}}")?.args?.get("mode"))
        assertEquals("silent", PhoneActionRules.parse("{\"tool\":\"ringer\",\"args\":{\"mode\":\"silent\"}}")?.args?.get("mode"))
        assertEquals("landscape", PhoneActionRules.parse("{\"tool\":\"rotation\",\"args\":{\"mode\":\"landscape\"}}")?.args?.get("mode"))
        assertEquals("auto_off", PhoneActionRules.parse("{\"tool\":\"rotation\",\"args\":{\"mode\":\"auto_off\"}}")?.args?.get("mode"))
    }

    @Test fun part9RejectsBadRingerAndRotationModes() {
        assertNull(PhoneActionRules.parse("{\"tool\":\"ringer\",\"args\":{\"mode\":\"loud\"}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"ringer\",\"args\":{}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"ringer\",\"args\":{\"mode\":\"vibrate\",\"volume\":5}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"rotation\",\"args\":{\"mode\":\"upside_down\"}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"rotation\",\"args\":{\"mode\":true}}"))
        assertNull(PhoneActionRules.parse("{\"tool\":\"global_action\",\"args\":{\"action\":\"power_off\"}}"))
    }

    @Test fun part9NewToolsCanBeChainedAndAreKnownToVerify() {
        val l = PhoneActionRules.parseAll("{\"actions\":[{\"tool\":\"ringer\",\"args\":{\"mode\":\"vibrate\"}},{\"tool\":\"rotation\",\"args\":{\"mode\":\"portrait\"}}]}")
        assertEquals(2, l.size)
        assertTrue(ToolVerify.KNOWN_TOOLS.containsAll(listOf("ringer", "rotation")))
        assertTrue(PhoneActionRules.likelyAction("phone ko vibrate par kar do"))
        assertTrue(PhoneActionRules.likelyAction("screen rotation band karo"))
    }
}
