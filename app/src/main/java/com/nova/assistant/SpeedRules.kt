package com.nova.assistant

/**
 * v7.78 PART 2 (speed, add-only): fixed sleeps in phone actions are replaced by "wait until ready, but never longer than the old
 * fixed wait". Pure Kotlin so it is unit tested. The old maximum stays the cap, so nothing can become slower or less safe.
 */
object SpeedRules {
    /** Poll interval used by the early-exit waits. */
    const val STEP_MS = 100L

    /** Retry pause after a cloud listener answered nothing (was 700 ms). The retry itself is unchanged. */
    const val HEAR_RETRY_MS = 350L

    /** true = stop waiting now. [elapsedMs] since the wait began; [ready] = the thing we wait for is there. Never stops before [minMs] unless [ready]. */
    fun shouldStop(elapsedMs: Long, minMs: Long, maxMs: Long, ready: Boolean): Boolean =
        elapsedMs >= maxMs || (ready && elapsedMs >= minMs)

    /** Number of poll steps for a cap, always at least one. */
    fun steps(maxMs: Long, stepMs: Long = STEP_MS): Int = maxOf(1L, (maxOf(0L, maxMs) + maxOf(1L, stepMs) - 1L) / maxOf(1L, stepMs)).toInt()

    /** The wait that replaced the fixed 1800 ms before an in-app search: package must be in front and the screen quiet. */
    fun appReady(frontIsTarget: Boolean, stableReads: Int): Boolean = frontIsTarget && stableReads >= 2
}
