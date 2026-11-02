package com.nova.assistant

/**
 * v7.4 "5-SECOND RULE": NOVA must understand a spoken/typed command in about 4.5 seconds, online or offline.
 * Pure Kotlin (unit tested). Layers are only TIME-BOUND here, none is removed:
 *   local rules (< 0.3 s) -> offline model (<= 2.5 s, only to understand a command) -> cloud AI (all the time that is left, two providers at once).
 * If nothing understood the sentence in time NOVA says so honestly; it never guesses an action.
 */
object UnderstandBudget {
    const val TOTAL_MS = 4500L
    const val LOCAL_MS = 300L
    const val OFFLINE_MODEL_MS = 2500L
    const val MIN_CALL_MS = 1200L
    const val HEDGE_WIDTH = 2

    fun remaining(startMs: Long, nowMs: Long, total: Long = TOTAL_MS): Long = maxOf(0L, total - (nowMs - startMs))

    /** Hard timeout for one HTTP call: what is left of the budget, never below a usable minimum. */
    fun callTimeoutMs(remainingMs: Long): Int = maxOf(MIN_CALL_MS, remainingMs).coerceAtMost(15_000L).toInt()

    /** The offline model may use at most OFFLINE_MODEL_MS and must leave 1.5 s for the cloud. */
    fun offlineModelMs(elapsedMs: Long, cloudAvailable: Boolean): Long {
        val room = if (cloudAvailable) TOTAL_MS - elapsedMs - 1500L else TOTAL_MS - elapsedMs
        return room.coerceIn(0L, OFFLINE_MODEL_MS)
    }

    /** Pure: the first VALID answer (by finish time) wins; null when nobody had a valid one. */
    class Attempt(val id: String, val text: String?, val finishedMs: Long)
    fun hedgedWinner(attempts: List<Attempt>): Attempt? = attempts.filter { !it.text.isNullOrBlank() }.minByOrNull { it.finishedMs }

    fun failureLine(en: Boolean): String = if (en) "I did not understand that, please say it again." else "Samajh nahi aaya, dobara bolo."
}

/** Latency per layer: only the layer NAME and numbers are kept (never the sentence, never a key). Shown in the test report / feature status. */
object Latency {
    private class Stat(var n: Int = 0, var total: Long = 0L, var max: Long = 0L, var last: Long = 0L, var slow: Int = 0)
    private val stats = LinkedHashMap<String, Stat>()
    const val SLOW_MS = 5000L

    @Synchronized fun record(layer: String, ms: Long) {
        val s = stats.getOrPut(layer.take(24)) { Stat() }
        s.n++; s.total += ms; s.last = ms; if (ms > s.max) s.max = ms; if (ms > SLOW_MS) s.slow++
    }

    inline fun <T> time(layer: String, block: () -> T): T {
        val t0 = System.currentTimeMillis()
        try { return block() } finally { record(layer, System.currentTimeMillis() - t0) }
    }

    @Synchronized fun summary(): String {
        if (stats.isEmpty()) return "abhi koi naap nahi"
        return stats.entries.joinToString(" | ") { (k, s) -> "$k: ${s.n}x, avg ${s.total / maxOf(1, s.n)}ms, max ${s.max}ms, >5s ${s.slow}" }
    }

    @Synchronized fun clear() = stats.clear()
}
