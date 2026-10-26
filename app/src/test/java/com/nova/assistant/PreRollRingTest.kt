package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.119 Part B: fixed-slot pre-roll (replaces ArrayList + copyOf per chunk). */
class PreRollRingTest {
    private fun chunk(v: Int, n: Int = 4) = ShortArray(n) { v.toShort() }
    private fun PreRollRing.firsts() = (0 until size).map { arrayAt(it)[0].toInt() }

    @Test fun startsEmpty() { val r = PreRollRing(5, 4); assertEquals(0, r.size) }

    @Test fun keepsOrderOldestFirst() {
        val r = PreRollRing(5, 4)
        for (v in 1..3) r.add(chunk(v), 4)
        assertEquals(listOf(1, 2, 3), r.firsts())
    }

    @Test fun dropsOldestAfterFive() {
        val r = PreRollRing(5, 4)
        for (v in 1..8) r.add(chunk(v), 4)
        assertEquals(5, r.size)
        assertEquals(listOf(4, 5, 6, 7, 8), r.firsts())
    }

    @Test fun lengthIsTheCopiedCountNotTheArraySize() {
        val r = PreRollRing(5, 8)
        r.add(chunk(7, 8), 3)
        assertEquals(1, r.size)
        assertEquals(3, r.lengthAt(0))
    }

    @Test fun copiesTheDataSoTheSourceCanBeReused() {
        val r = PreRollRing(5, 4)
        val src = chunk(1)
        r.add(src, 4)
        src.fill(9)
        assertEquals(1, r.arrayAt(0)[0].toInt())
    }

    @Test fun reusesTheSameArraysAfterItIsFull() {
        val r = PreRollRing(5, 4)
        for (v in 1..5) r.add(chunk(v), 4)
        val before = (0 until 5).map { System.identityHashCode(r.arrayAt(it)) }.toSet()
        for (v in 6..40) r.add(chunk(v), 4)
        val after = (0 until 5).map { System.identityHashCode(r.arrayAt(it)) }.toSet()
        assertEquals(before, after)   // no new array was created
    }

    @Test fun clearEmptiesAndWorksAgain() {
        val r = PreRollRing(5, 4)
        for (v in 1..7) r.add(chunk(v), 4)
        r.clear()
        assertEquals(0, r.size)
        r.add(chunk(42), 4)
        assertEquals(listOf(42), r.firsts())
    }

    @Test fun biggerChunkThanSlotStillWorks() {
        val r = PreRollRing(5, 2)
        r.add(chunk(5, 6), 6)
        assertEquals(6, r.lengthAt(0))
        assertEquals(5, r.arrayAt(0)[5].toInt())
    }

    @Test fun badIndexThrows() {
        try { PreRollRing(5, 4).arrayAt(0); fail("expected IndexOutOfBoundsException") } catch (e: IndexOutOfBoundsException) { }
    }

    @Test fun replayKeepsGateSemantics() {
        // same rule as WakeGateRulesTest: a full ring replays all but the newest chunk, oldest first
        val r = PreRollRing(5, 4)
        for (v in 1..5) r.add(chunk(v), 4)
        val n = WakeGateRules.replayCount(r.size, false, true)
        assertEquals(4, n)
        assertEquals(listOf(1, 2, 3, 4), (0 until n).map { r.arrayAt(it)[0].toInt() })
    }
}
