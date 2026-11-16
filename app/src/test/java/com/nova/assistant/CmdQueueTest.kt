package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.40 PART 4 step B. Written, NOT run here (no kotlinc / network in the sandbox). */
class CmdQueueTest {
    @Test fun plainAurIsNeverSplit() { assertEquals(1, CmdQueue.split("torch jala do aur volume badha do").size) }
    @Test fun semicolonAndNewlineSplit() {
        assertEquals(listOf("torch on", "volume up"), CmdQueue.split("torch on; volume up"))
        assertEquals(2, CmdQueue.split("torch on\nvolume up").size)
    }
    @Test fun numberedListSplit() { assertEquals(listOf("torch on", "volume up", "time batao"), CmdQueue.split("1. torch on 2. volume up 3) time batao")) }
    @Test fun softPhraseSplitsOnlyWithoutMessageCues() {
        assertEquals(listOf("torch jala do", "volume badha do"), CmdQueue.split("torch jala do uske baad volume badha do"))
        assertEquals(1, CmdQueue.split("rahul ko message bhejo ki main aa raha hu phir baat karte hain").size)
    }
    @Test fun maxTenAndDropCount() {
        val t = (1..12).joinToString(";") { "cmd$it" }
        assertEquals(10, CmdQueue.split(t).size)
        assertEquals(2, CmdQueue.splitDropped(t))
    }
    @Test fun queueRunsOneByOne() {
        val q = CmdQueue.Queue()
        assertEquals(0, q.addAll(listOf("a", "b", "c"), { it == "a" }))
        assertEquals("a", q.next()!!.text)
        assertEquals("b", q.next()!!.text)
        assertTrue(q.hasWaiting())
        assertEquals("c", q.next()!!.text)
        assertNull(q.next())
        assertTrue(q.isEmpty())
    }
    @Test fun capIsTen() {
        val q = CmdQueue.Queue()
        assertEquals(2, q.addAll((1..12).map { "c$it" }, { false }))
    }
    @Test fun clearEmptiesEverything() {
        val q = CmdQueue.Queue(); q.addAll(listOf("a", "b"), { true }); q.clear()
        assertTrue(q.isEmpty()); assertEquals("[]", q.json())
    }
    @Test fun jsonHasRouteAndEscapes() {
        val q = CmdQueue.Queue(); q.addAll(listOf("say \"hi\"", "torch"), { it == "torch" })
        val j = q.json()
        assertTrue(j.contains("\\\"hi\\\"")); assertTrue(j.contains("\"r\":\"OFFLINE\"")); assertTrue(j.contains("\"r\":\"ONLINE\""))
    }
}
