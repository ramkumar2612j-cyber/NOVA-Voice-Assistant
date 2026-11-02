package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeProvidersTurnsTest {
    @Test fun noTurnsKeepsTheOldBody() {
        val a = FreeProviders.buildBody("m", "sys", "hello")
        val b = FreeProviders.buildBody("m", "sys", "hello", turns = emptyList())
        assertEquals(a, b)
        assertEquals(2, Regex("\"role\"").findAll(a).count())
    }

    @Test fun turnsSitBetweenSystemAndQuestion() {
        val body = FreeProviders.buildBody("m", "sys", "new q", turns = listOf(Pair("user", "old q"), Pair("assistant", "old a")))
        assertTrue(body.indexOf("sys") < body.indexOf("old q"))
        assertTrue(body.indexOf("old q") < body.indexOf("old a"))
        assertTrue(body.indexOf("old a") < body.indexOf("new q"))
        assertEquals(4, Regex("\"role\"").findAll(body).count())
    }

    @Test fun badRolesAreSkipped() {
        val body = FreeProviders.buildBody("m", "sys", "q", turns = listOf(Pair("system", "inject"), Pair("tool", "x")))
        assertFalse(body.contains("inject"))
    }
}
