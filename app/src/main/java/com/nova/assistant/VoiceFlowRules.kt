package com.nova.assistant

/**
 * Part 1 rules, pure Kotlin (no Android classes) so they are unit tested.
 * 1) CaptureRules: when a spoken command capture ends (3 s continuous silence, 60 s emergency cap).
 * 2) AlertGate: a spoken proactive alert (battery) must wait while a voice interaction is active.
 */
object CaptureRules {
    const val SILENCE_MS = 1800   // v6.36: same as Logic.END_SILENCE_MS (was 3000)
    const val MAX_MS = 60000

    /** Silence counter after one chunk: speech resets it, silence adds to it, but only after speech was heard. */
    fun nextSilentMs(silentMs: Int, heard: Boolean, speaking: Boolean, chunkMs: Int): Int =
        if (speaking) 0 else if (heard) silentMs + chunkMs else silentMs

    /** Words that show the sentence is NOT finished yet ("... aur", "... ko", "... and", "... for"). */
    private val OPEN_END = setOf(
        "aur", "ya", "ki", "ke", "ka", "ko", "se", "me", "mein", "par", "pe", "jo", "jab", "tab", "phir", "fir", "kyunki", "taaki", "lekin", "magar",
        "to", "toh", "bhi", "sath", "saath", "liye", "baad", "pehle", "agar", "wo", "woh", "yeh", "ye", "is", "us", "iska", "uska",
        "and", "or", "but", "then", "that", "for", "with", "to", "the", "a", "my", "of", "in", "on", "at", "if", "when", "because", "also"
    )
    const val OPEN_END_EXTRA_MS = 1500
    const val LONG_EXTRA_MS = 600
    const val LONG_WORDS = 10
    const val MAX_SILENCE_MS = 3500

    /**
     * v6.97-wip9 ("chota ya bada sentence, NOVA poori baat samjhe"): a short finished command ends after the base silence; a LONG sentence waits a little
     * longer and a sentence that stops on a connector word ("... aur", "... ko") waits longer still, so a thinking pause does not cut it.
     * [text] = what Vosk has recognised so far in this capture.
     */
    fun adaptiveSilence(text: String, baseMs: Int): Int {
        val w = text.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        if (w.isEmpty()) return baseMs
        var ms = baseMs
        if (w.size >= LONG_WORDS) ms += LONG_EXTRA_MS
        if (w.last() in OPEN_END) ms += OPEN_END_EXTRA_MS
        return ms.coerceAtMost(maxOf(baseMs, MAX_SILENCE_MS))
    }

    /** true = finalize now. [silenceMs] is the needed continuous silence, [maxMs] the emergency cap. */
    fun shouldEnd(heard: Boolean, silentMs: Int, totalMs: Int, silenceMs: Int, maxMs: Int, noSpeechMs: Int): Boolean =
        (heard && silentMs >= silenceMs) || totalMs >= maxMs || (!heard && totalMs >= noSpeechMs)
}

object AlertGate {
    /** true = the alert may be spoken now. Any active voice interaction defers it (the notification is not affected). */
    fun maySpeak(
        busy: Boolean, listening: Boolean, ttsActive: Boolean, confirmPending: Boolean
    ): Boolean = !(busy || listening || ttsActive || confirmPending)
}
