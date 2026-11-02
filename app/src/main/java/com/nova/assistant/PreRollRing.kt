package com.nova.assistant

/**
 * v7.119 Part B (lite series): fixed-size pre-roll for the wake loop. The old code made a fresh array copy on
 * EVERY 100 ms chunk, i.e. a new ShortArray (about 3 KB) about 10 times a second for as long as NOVA listens.
 * This ring owns [capacity] arrays that are reused, so the steady state allocates nothing.
 *
 * Order and semantics are the same as the old ArrayList (oldest first, newest last, at most [capacity] chunks).
 * Because arrays are reused, a reader MUST use [lengthAt] (not array.size) and must not keep an array after the next [add].
 */
class PreRollRing(val capacity: Int = 5, chunkSize: Int = 1600) {
    private val slots = Array(capacity) { ShortArray(chunkSize) }
    private val lens = IntArray(capacity)
    private var start = 0
    var size = 0
        private set

    /** Copies the first [n] samples of [src] into the next slot; the oldest chunk is dropped when full. */
    fun add(src: ShortArray, n: Int) {
        val len = n.coerceIn(0, src.size)
        val slot = if (size < capacity) (start + size) % capacity else start
        if (size == capacity) start = (start + 1) % capacity else size++
        var a = slots[slot]
        if (a.size < len) { a = ShortArray(len); slots[slot] = a }   // only if a chunk is ever bigger than the first one
        System.arraycopy(src, 0, a, 0, len)
        lens[slot] = len
    }

    private fun idx(i: Int): Int {
        if (i < 0 || i >= size) throw IndexOutOfBoundsException("i=$i size=$size")
        return (start + i) % capacity
    }

    /** i = 0 is the oldest chunk. The array may be longer than the chunk: use [lengthAt]. */
    fun arrayAt(i: Int): ShortArray = slots[idx(i)]

    fun lengthAt(i: Int): Int = lens[idx(i)]

    fun clear() { start = 0; size = 0 }
}
