package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PART 7 (v6.79): redaction of keys / tokens, the Memory screen operations, import / export, Devanagari matching, task line consistency. Hand written (the table tests are generated into PersonalMemoryTest). */
class MemoryScreenOpsTest {
    private val base = listOf("mera naam shiv hai", "meri car blue hai", "papa ka naam ram hai")

    @Test fun keysAndTokensAreRefusedInRawText() {
        val rows = listOf(
            "AIzaSyD4kLm9Q2xR7vB3nT8pW1zY6uH0",
            "sk-proj-abc123def456ghi789",
            "ghp_abcdefghijklmnopqrstuv1234",
            "gsk_abcdefghijklmnop1234",
            "hf_abcdefghijklmnopqrst1234",
            "github_pat_abcdefghijklmnopqrst",
            "xoxb-1234-abcd-efgh-ijkl",
            "meri openrouter api key sk-or-v1-abcdef123456",
            "mera token yaad rakh"
        )
        for (t in rows) {
            assertEquals("refuse: $t", "key", PersonalMemory.refuseReason(t))
            val r = PersonalMemory.add(emptyList(), t)
            assertEquals("refused: $t", "refused", r.status)
            assertTrue(r.list.isEmpty())
            assertTrue("looks private: $t", PersonalMemory.looksPrivateContact(t))
            for (en in listOf(true, false)) assertFalse(PersonalMemory.saveReply(r, en).contains(t.take(8)))
        }
    }

    @Test fun normalTextIsNotMistakenForAKey() {
        val rows = listOf(
            "supercalifragilisticexpialidocious",
            "meri flight ai202 hai",
            "mera naam shiv hai",
            "my email is shiv at example dot com",
            "papa ka birthday 5 june ko hai",
            "i like chai and biscuits very very much indeed"
        )
        for (t in rows) assertNull("fine: $t", PersonalMemory.refuseReason(t))
    }

    @Test fun refusedFactsNeverReachAModelPrompt() {
        val facts = listOf("mera api key sk-abc123def456ghi789", "meri car blue hai", "mera token abc")
        assertEquals(listOf("meri car blue hai"), PersonalMemory.relevant(facts, "meri car ka colour kya hai"))
        assertEquals(emptyList<String>(), PersonalMemory.relevant(facts, "mera api key kya hai"))
    }

    @Test fun searchMatchesEveryWordIgnoringCase() {
        assertEquals(listOf(0, 2), PersonalMemory.search(base, "NAAM"))
        assertEquals(listOf(2), PersonalMemory.search(base, "papa  naam"))
        assertEquals(listOf(0, 1, 2), PersonalMemory.search(base, "  "))
        assertEquals(emptyList<Int>(), PersonalMemory.search(base, "xyz"))
    }

    @Test fun deleteOnlyTheExpectedFact() {
        val ok = PersonalMemory.removeAt(base, 1, "meri car blue hai")
        assertEquals("saved", ok.status)
        assertEquals(listOf(base[0], base[2]), ok.list)
        val moved = PersonalMemory.removeAt(base, 1, "papa ka naam ram hai")
        assertEquals("gone", moved.status)
        assertEquals(base, moved.list)
        assertEquals("gone", PersonalMemory.removeAt(base, 7, "x").status)
        assertEquals("gone", PersonalMemory.removeAt(base, -1, "x").status)
    }

    @Test fun editKeepsPositionAndRunsTheSameChecks() {
        val ok = PersonalMemory.replaceAt(base, 1, base[1], "  meri car red hai ")
        assertEquals("saved", ok.status)
        assertEquals(listOf(base[0], "meri car red hai", base[2]), ok.list)
        val rows = listOf(Pair("mera otp 5555", "refused"), Pair("api key abc", "refused"), Pair("ab", "short"), Pair("z".repeat(201), "long"), Pair("MERA naam SHIV hai", "dup"))
        for ((t, want) in rows) {
            val r = PersonalMemory.replaceAt(base, 1, base[1], t)
            assertEquals("edit: $t", want, r.status)
            assertEquals(base, r.list)
        }
        assertEquals("saved", PersonalMemory.replaceAt(base, 0, base[0], "MERA naam shiv HAI").status)   // the fact itself is not a duplicate of itself
        assertEquals("gone", PersonalMemory.replaceAt(base, 1, "stale text", "meri car red hai").status)
    }

    @Test fun exportThenImportRoundTrips() {
        val text = PersonalMemory.exportText(base)
        assertTrue(text.startsWith(PersonalMemory.EXPORT_HEADER))
        val r = PersonalMemory.importText(emptyList(), text)
        assertNotNull(r)
        assertEquals(base, r!!.list)
        assertEquals(3, r.added)
        assertEquals(0, r.refused)
    }

    @Test fun importedFileIsData_secretsAndKeysAreRefused() {
        val file = "\uFEFF# NOVA memory export v1\n# note\nnayi baat ek\nmera otp 4821\nsk-abc123def456ghi789xyz\nmy card number is 4111 1111 1111 1111\nnayi baat do\nab\nmeri car blue hai\n"
        val r = PersonalMemory.importText(base, file)!!
        assertEquals(2, r.added)
        assertEquals(1, r.dup)
        assertEquals(3, r.refused)
        assertEquals(1, r.skipped)
        assertEquals(listOf("nayi baat ek", "nayi baat do"), r.list.take(2))
        for (f in r.list) assertNull("stored fact must be safe: $f", PersonalMemory.refuseReason(f))
    }

    @Test fun importRejectsUnusableFiles() {
        assertNull(PersonalMemory.importText(base, ""))
        assertNull(PersonalMemory.importText(base, "# only comments\n# nothing else\n"))
        assertNull(PersonalMemory.importText(base, "x".repeat(PersonalMemory.MAX_IMPORT_CHARS + 1)))
        assertNull(PersonalMemory.importText(base, (1..(PersonalMemory.MAX_IMPORT_LINES + 1)).joinToString("\n") { "baat number $it hai" }))
    }

    @Test fun importStaysInsideTheFactLimit() {
        val file = (1..150).joinToString("\n") { "chai fact item$it" }
        val r = PersonalMemory.importText(emptyList(), file)!!
        assertEquals(PersonalMemory.MAX_FACTS, r.list.size)
        assertEquals(150, r.added)
        assertEquals(50, r.dropped)
        assertEquals("chai fact item1", r.list.first())   // file order is kept, the oldest lines were pushed out
    }

    @Test fun screenRepliesNeverEchoARefusedFact() {
        val r = PersonalMemory.replaceAt(base, 0, base[0], "mera otp 7788")
        assertFalse(PersonalMemory.screenReply(r).contains("7788"))
        assertEquals("Ye baat badal chuki hai. List dobara kholo.", PersonalMemory.screenReply(PersonalMemory.removeAt(base, 5, "x")))
    }

    @Test fun devanagariFactsAndQuestionsStillMatch() {
        assertTrue(PersonalMemory.tokens("बैटरी").contains("battery"))
        val facts = listOf("बैटरी कमजोर है", "meri car blue hai")
        assertEquals(listOf("बैटरी कमजोर है"), PersonalMemory.relevant(facts, "battery kitni hai"))
        assertEquals(listOf("बैटरी कमजोर है"), PersonalMemory.relevant(facts, "बैटरी कितनी है"))
        assertEquals(listOf(0), PersonalMemory.search(facts, "बैटरी"))
    }

    @Test fun taskLineRedactsSecretsAndGoesStaleConsistently() {
        for (en in listOf(true, false)) {
            val line = FallbackContext.taskLine("whatsapp bhejo password=hunter2 api_key: abc123", "RUNNING", "token=zzz999", 1000L, en)
            assertTrue(line.isNotBlank())
            assertFalse(line.contains("hunter2"))
            assertFalse(line.contains("abc123"))
            assertFalse(line.contains("zzz999"))
            assertEquals("", FallbackContext.taskLine("torch on", "SUCCEEDED", "", FallbackContext.TASK_FRESH_MS + 1, en))
            assertEquals("", FallbackContext.taskLine("", "RUNNING", "", 10L, en))
        }
        assertEquals("BASE", FallbackContext.systemWith("BASE", ""))
        assertTrue(FallbackContext.systemWith("BASE", "TASK").endsWith("TASK"))
    }
}
