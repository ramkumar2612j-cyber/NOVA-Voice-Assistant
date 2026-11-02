package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultCardRulesTest {
    private fun report(summary: String = "Ek chai ki dukaan ka photo hai. Andar do log hain.", conf: String = "MEDIUM") = AnalysisRules.Report(
        "vision", summary, listOf("chai"), emptyList(),
        listOf(AnalysisRules.Claim("claim a", listOf(1))), listOf("claim b", "claim c"),
        listOf(AnalysisRules.Result("T1", "", "https://www.example.com/a/b?x=1", ""), AnalysisRules.Result("T2", "", "https://news.site.org/z", ""), AnalysisRules.Result("T3", "", "https://www.example.com/other", "")),
        conf, "why", emptyList())

    @Test fun headlineIsFirstSentenceAndCapped() {
        assertEquals("Ek chai ki dukaan ka photo hai.", ResultCardRules.headline("Ek chai ki dukaan ka photo hai. Andar do log hain."))
        assertTrue(ResultCardRules.headline("a".repeat(300)).length <= 90)
        assertEquals("", ResultCardRules.headline("   "))
    }
    @Test fun cardHasConfidenceCountsAndDomainsOnly() {
        val c = ResultCardRules.card(report(), true)
        assertTrue(c.contains("MEDIUM")); assertTrue(c.contains("Verified: 1")); assertTrue(c.contains("Not verified: 2"))
        assertTrue(c.contains("example.com, news.site.org")); assertFalse(c.contains("http")); assertFalse(c.contains("/a/b"))
    }
    @Test fun historyLineIsCountsOnly() {
        val l = ResultCardRules.historyLine(report())
        assertEquals("analysis vision | MEDIUM | v1 u2 s3", l); assertFalse(l.contains("chai"))
    }
    @Test fun readAllPhrases() {
        for (p in listOf("nova poora padho", "Poori report padho", "read the full report", "pura sunao", "puri report sunao")) assertTrue(p, ResultCardRules.isReadAll(p))
        for (p in listOf("", "ye movie kon si hai", "kitne baje hain", "nova video analyse karo")) assertFalse(p, ResultCardRules.isReadAll(p))
        assertFalse(ResultCardRules.isReadAll("poora padho " + "x".repeat(80)))
    }
    @Test fun speakableDropsMarksAndLinks() {
        val s = ResultCardRules.speakable("**Sources:**\n- [1] Title https://a.com/x\n- ok [2,3] baat")
        assertFalse(s.contains("http")); assertFalse(s.contains("[")); assertFalse(s.contains("*")); assertTrue(s.contains("baat"))
    }
    @Test fun chunksAreBoundedAndComplete() {
        assertTrue(ResultCardRules.chunks("").isEmpty())
        val long = (1..200).joinToString(" ") { "Yeh line number $it hai." }
        val ch = ResultCardRules.chunks(long, 300)
        assertTrue(ch.size > 1); assertTrue(ch.all { it.length <= 300 }); assertTrue(ch.size <= 12)
        assertEquals("Yeh line number 1 hai.", ch[0].substring(0, 22))
    }
}
