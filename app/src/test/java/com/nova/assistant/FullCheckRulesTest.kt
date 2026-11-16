package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullCheckRulesTest {
    @Test fun linesOfFindsFailAndWarnOnly() {
        val r = "PASS mic ok\nFAIL torch band\n- FAIL wiring x\nWARN Band: jarvis\nSUMMARY pass=1 fail=2 warn=1"
        val it = FullCheckRules.linesOf("S", r)
        assertEquals(3, it.size)
        assertEquals(2, it.count { x -> x.state == "FAIL" })
    }
    @Test fun statusItemsOffAndWarnOnly() {
        val rows = listOf(Triple("Wake", "ok", ""), Triple("Acc", "off", "band"), Triple("Net", "warn", "slow"))
        val it = FullCheckRules.statusItems(rows)
        assertEquals(2, it.size)
        assertEquals(Triple(0, 1, 1), FullCheckRules.counts(it))
    }
    @Test fun buildPutsNotWorkingFirstAndKeepsReports() {
        val items = FullCheckRules.linesOf("S", "FAIL torch")
        val t = FullCheckRules.build("40", "2026-11-15 10:00", items, "SELF", "VERIFY", "RECENT ERRORS: none")
        assertTrue(t.indexOf("== NOT WORKING ==") < t.indexOf("== SELF TEST"))
        assertTrue(t.contains("not_working=1") && t.contains("SELF") && t.contains("VERIFY"))
    }
    @Test fun spokenHonestWhenNothingRan() {
        assertTrue(FullCheckRules.spoken(emptyList(), false).contains("chali nahi"))
    }
    @Test fun spokenListsFailuresAndCapsThem() {
        val items = (1..9).map { FullCheckRules.Item("S", "FAIL", "FAIL f$it") }
        val t = FullCheckRules.spoken(items, true, 6)
        assertTrue(t.contains("(9)") && t.contains("aur 3 aur"))
    }
    @Test fun askPhrases() {
        assertTrue(FullCheckRules.isAsk("jarvis kya nahi chal raha"))
        assertTrue(FullCheckRules.isAsk("full check karo"))
        assertFalse(FullCheckRules.isAsk("wifi kya band hai"))
        assertFalse(FullCheckRules.isAsk(""))
    }
}
