package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.218 SYNC rules tests. Fixtures EXACTLY same as desktop/test/sync.test.js — dono taraf same merge result
 * aana zaroori hai (phone <-> laptop conflict resolution me dono ek hi winner chunein).
 */
class SyncRulesTest {

    private fun t(id: String, title: String, ver: Int = 1, ts: Long = 0, device: String = "", state: String = "open") =
        SyncRules.TaskItem(id, title, ver = ver, ts = ts, device = device, state = state)

    private fun f(id: String, text: String, ver: Int = 1, ts: Long = 0, device: String = "", scope: String = "all") =
        SyncRules.FactItem(id, text, ver = ver, ts = ts, device = device, scope = scope)

    // ---------- tasks ----------

    @Test fun taskMergeUnionsById() {
        val mine = listOf(t("a", "mera kaam"), t("b", "b mine", ver = 2))
        val theirs = listOf(t("b", "b theirs", ver = 1), t("c", "un kaam"))
        val m = SyncRules.mergeTasks(mine, theirs)
        assertEquals(listOf("a", "b", "c"), m.map { it.id }.sorted())
        assertEquals("b mine", m.first { it.id == "b" }.title)   // higher ver jeeta
    }

    @Test fun taskMergeLwwSameVerNewerTs() {
        val mine = listOf(t("x", "purana", ts = 100))
        val theirs = listOf(t("x", "naya", ts = 200))
        assertEquals("naya", SyncRules.mergeTasks(mine, theirs).first().title)
    }

    @Test fun taskMergeTieBreakByDevice() {
        val m = SyncRules.mergeTasks(listOf(t("x", "mine", ts = 100, device = "d1")), listOf(t("x", "theirs", ts = 100, device = "d2")))
        assertEquals("theirs", m.first().title)   // d2 > d1
        val m2 = SyncRules.mergeTasks(listOf(t("x", "mine", ts = 100, device = "d2")), listOf(t("x", "theirs", ts = 100, device = "d1")))
        assertEquals("mine", m2.first().title)
    }

    @Test fun taskMergeSortsByTsAndCaps() {
        val many = (1..150).map { t("t$it", "kaam $it", ts = it.toLong()) }
        val m = SyncRules.mergeTasks(many, emptyList())
        assertEquals(SyncRules.MAX_TASKS, m.size)
        assertEquals("kaam 150", m.last().title)   // newest ts survives
    }

    // ---------- facts / memory ----------

    @Test fun factMergePrivateNeverCrossesDevices() {
        val mine = listOf(f("p1", "mera private", scope = "private"))
        val theirs = listOf(f("p2", "unka private", scope = "private"), f("s1", "shared fact"))
        val m = SyncRules.mergeFacts(mine, theirs)
        assertTrue(m.none { it.id == "p2" })           // doosre ka private kabhi nahi aata
        assertTrue(m.any { it.id == "p1" })            // apna private rehta hai
        assertTrue(m.any { it.id == "s1" })
    }

    @Test fun factMergeLww() {
        val mine = listOf(f("k", "mera", ver = 2))
        val theirs = listOf(f("k", "un ka", ver = 1))
        assertEquals("mera", SyncRules.mergeFacts(mine, theirs).first().text)
    }

    @Test fun memoryRoundTripStableIds() {
        val mem = listOf("mera naam Boss hai", "delhi me rehta hu")
        val facts = SyncRules.memoryToFacts(mem, 1000, "d1")
        assertEquals(2, facts.size)
        assertEquals(SyncRules.idOf("mera naam Boss hai"), facts[0].id)
        // same text -> same id (dedupe natural)
        val again = SyncRules.memoryToFacts(listOf("mera naam Boss hai"), 2000, "d2")
        assertEquals(facts[0].id, again[0].id)
    }

    @Test fun factsToMemoryNoDuplicates() {
        val existing = listOf("ek fact")
        val facts = listOf(f("1", "ek fact"), f("2", "naya fact"))
        val out = SyncRules.factsToMemory(facts, existing)
        assertEquals(listOf("ek fact", "naya fact"), out)
    }

    @Test fun factsToMemorySkipsPrivate() {
        val out = SyncRules.factsToMemory(listOf(f("1", "private cheez", scope = "private")), emptyList())
        assertTrue(out.isEmpty())
    }

    @Test fun taskToReminderTitleSanitizes() {
        assertEquals("seedha title", SyncRules.taskToReminderTitle(t("1", "seedha\n title\r\n")))
    }

    @Test fun capsHold() {
        assertEquals(100, SyncRules.MAX_TASKS)
        assertEquals(200, SyncRules.MAX_FACTS)
        assertEquals(400, SyncRules.MAX_TEXT)
    }
}
