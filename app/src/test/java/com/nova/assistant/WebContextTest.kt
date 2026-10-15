package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebContextTest {
    @Test fun liveQuestionsNeedWeb() {
        assertTrue(WebContext.needsWeb("aaj ka latest news kya hai"))
        assertTrue(WebContext.needsWeb("who is the prime minister of japan"))
        assertTrue(WebContext.needsWeb("bitcoin price abhi kitna hai"))
    }

    @Test fun timelessQuestionsDoNot() {
        assertFalse(WebContext.needsWeb("explain recursion with an example"))
        assertFalse(WebContext.needsWeb("ek email likho chutti ke liye"))
        assertFalse(WebContext.needsWeb("hi"))
    }

    @Test fun queryIsTrimmedOnAWordEnd() {
        val q = WebContext.queryOf("word ".repeat(60))
        assertTrue(q.length <= 120)
        assertFalse(q.endsWith(" "))
    }

    @Test fun blockHasUpToThreeItemsAndNothingWhenEmpty() {
        assertEquals("", WebContext.block(emptyList(), true))
        assertEquals("", WebContext.block(listOf("short"), true))
        val items = List(5) { "Item number $it with enough text to be kept (Wikipedia)" }
        val b = WebContext.block(items, true)
        assertTrue(b.startsWith("WEB RESULTS"))
        assertEquals(3, b.lines().count { it.startsWith("- ") })
    }

    @Test fun sourcesLineAddedOnlyWhenMissing() {
        val items = listOf("Tokyo: capital of Japan and a very large city in Asia (Wikipedia)", "Google Search: some grounded summary text here")
        assertEquals(listOf("Wikipedia", "Google Search"), WebContext.sourceNames(items))
        val r = WebContext.withSources("Answer here.", items)
        assertTrue(r.endsWith("Sources: Wikipedia, Google Search"))
        assertEquals("Answer.\nSources: X", WebContext.withSources("Answer.\nSources: X", items))
        assertEquals("Answer.", WebContext.withSources("Answer.", emptyList()))
    }
}
