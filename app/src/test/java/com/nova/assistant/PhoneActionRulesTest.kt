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
}
