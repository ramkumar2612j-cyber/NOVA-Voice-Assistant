package com.nova.assistant

/**
 * v6.56 self-tuning wake sensitivity. Pure Kotlin, unit tested.
 * Every false wake (nothing said afterwards) makes the next wake need a louder voice; a real command resets it; after a quiet
 * period it relaxes by itself. So a noisy room / TV teaches NOVA to be stricter without the owner touching any setting.
 */
object WakeAdapt {
    const val STEP = 0.25            // +25 % required loudness per false wake in a row
    const val MAX_SCALE = 2.0        // never more than double
    const val RELAX_MS = 10 * 60_000L

    fun scale(misses: Int, lastMissAt: Long, now: Long): Double {
        if (misses <= 0 || lastMissAt <= 0L) return 1.0
        if (now - lastMissAt > RELAX_MS) return 1.0
        return minOf(MAX_SCALE, 1.0 + STEP * misses)
    }
}
