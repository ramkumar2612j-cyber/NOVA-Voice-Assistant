package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class TeachRulesTest {
    @Test fun parsesUserAliasExamplesWithoutTreatingThemAsCommands() {
        val a = TeachRules.parse("mummy = Sunita")
        assertEquals("mummy", a?.alias)
        assertEquals("Sunita", a?.value)
        val b = TeachRules.parse("yaad rakho mera camera = Camera app")
        assertEquals("mera camera", b?.alias)
        assertEquals("Camera app", b?.value)
    }

    @Test fun credentialsAndMalformedAliasesAreRejected() {
        assertNull(TeachRules.parse("wifi password = hunter2"))
        assertFalse(TeachRules.valid("api key", "my thing"))
        assertFalse(TeachRules.valid("x", "Sunita"))
        assertFalse(TeachRules.valid("a".repeat(41), "Sunita"))
    }

    @Test fun importRequiresAnExactVersionedBoundedSchema() {
        assertTrue(TeachRules.exportValidJson("{\"version\":1,\"items\":[{\"alias\":\"mummy\",\"value\":\"Sunita\"}]}"))
        assertFalse(TeachRules.exportValidJson("{\"version\":2,\"items\":[]}"))
        assertFalse(TeachRules.exportValidJson("{\"version\":1,\"items\":[{\"alias\":\"mummy\",\"value\":\"Sunita\",\"apiKey\":\"secret\"}]}"))
        assertFalse(TeachRules.exportValidJson("not json"))
    }

    @Test fun ordinaryQuestionsAreNeverTreatedAsTeaching() {
        assertFalse(TeachRules.isIntent("what is the weather today"))
        assertFalse(TeachRules.isIntent("pyar ka matlab kya hai"))
        assertFalse(TeachRules.isIntent("battery is low"))
        assertNull(TeachRules.parse("what is the weather"))
        assertNull(TeachRules.parse("battery is low"))
    }

    @Test fun explicitTeachingSentencesStillWork() {
        assertTrue(TeachRules.isIntent("mummy = Sunita"))
        assertTrue(TeachRules.isIntent("mera camera ka matlab camera app"))
        val p = TeachRules.parse("remember mummy is Sunita")
        assertEquals("mummy", p?.alias)
        assertEquals("Sunita", p?.value)
    }
}
