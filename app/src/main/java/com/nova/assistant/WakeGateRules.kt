package com.nova.assistant

/**
 * v7.119 (lite, Part A): the "Vosk wake recognizer runs only while a voice is present" gate of v7.117, moved out of
 * NovaService into pure Kotlin so it can be unit tested. Behaviour is identical to the v7.117 inline code:
 *  - a chunk is VOICE when its rms is above max(300, noise * 1.8);
 *  - a voice opens the gate for HOLD_MS (2 s); inside that window every chunk is fed to Vosk;
 *  - when the gate opens, the older pre-roll chunks (all but the newest, which is fed right after) are replayed first;
 *  - when the gate closes, the recognizer is reset once (no half sentence is carried into the next voice).
 */
object WakeGateRules {
    const val HOLD_MS = 2000L
    const val MIN_RMS = 300.0
    const val NOISE_MULT = 1.8

    /** True when this chunk is loud enough to be a voice. */
    fun voice(rms: Double, noise: Double): Boolean = rms > maxOf(MIN_RMS, noise * NOISE_MULT)

    /** True while the hold window is still running. */
    fun isOpen(now: Long, openUntil: Long): Boolean = now < openUntil

    /** New end of the hold window: a voice restarts it, silence leaves it unchanged. */
    fun extend(now: Long, openUntil: Long, voice: Boolean): Long = if (voice) now + HOLD_MS else openUntil

    /** How many OLD pre-roll chunks to replay: only on the chunk that OPENS the gate (the newest chunk is fed normally). */
    fun replayCount(preSize: Int, wasOpen: Boolean, voice: Boolean): Int =
        if (voice && !wasOpen && preSize > 1) preSize - 1 else 0

    /** True on the one chunk where the gate goes from open to closed: reset the recognizer then. */
    fun closing(open: Boolean, wasOpen: Boolean): Boolean = !open && wasOpen
}
