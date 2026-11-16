package com.nova.assistant

/**
 * v7.187 BARGE-IN COMMAND (add-only, rides the v7.154 BARGE-IN toggle). Pure Kotlin, no Android, unit-testable.
 *
 * Before this: while Jarvis is SPEAKING, the mic only listens for a tiny STOP-word grammar (JarvisDeepRules).
 * Anything else said during speech is dropped, so interrupting with a real sentence ("ruko, mummy ko call karo")
 * did nothing until the owner said a bare stop word first.
 *
 * After this: the SAME barge-in recognizer runs with the OPEN vocabulary (same model the normal command
 * listener already uses), so it can hear a full sentence, not just stop words. JarvisDeepRules.hit (stop word +
 * echo-safe) is checked FIRST and unchanged. Only when that is false does this file decide whether the sentence
 * is a genuine new command (captured, queued, answered right after - old work is never lost) or Jarvis's own
 * voice bleeding back into the mic (ignored, exactly as before).
 */
object BargeInCommandRules {
    const val MIN_CONF = 0.80f      // a little below the stop-word bar: a full sentence is harder to hear perfectly
    const val MIN_RMS = 1000.0      // same loudness floor as the stop check: room noise / a TV must not trigger this
    const val MIN_WORDS = 2         // one stray word ("haan", "kya") is not a command
    const val MAX_ECHO_OVERLAP = 0.5 // if half or more of the heard words are also in Jarvis's own sentence, it is an echo

    enum class Result { NONE, CAPTURE }

    private fun words(s: String): List<String> = Logic.norm(s).split(" ").filter { it.length > 1 }

    /** Fraction of [heard]'s words that also appear anywhere in [spokenText] (Jarvis's own current sentence). */
    fun echoOverlap(heard: String, spokenText: String): Double {
        val h = words(heard)
        if (h.isEmpty()) return 0.0
        val sp = words(spokenText).toSet()
        if (sp.isEmpty()) return 0.0
        val matched = h.count { it in sp }
        return matched.toDouble() / h.size
    }

    /**
     * One decision for a chunk heard while Jarvis is speaking, AFTER the caller has already checked
     * JarvisDeepRules.hit (a bare stop word) and found it false. CAPTURE = treat [heard] as a new owner
     * command: stop speaking, queue it, answer it next (the old task, if any, keeps running / is not lost).
     */
    fun decide(heard: String, spokenText: String, conf: Float, rms: Double): Result {
        val t = heard.trim()
        if (t.isBlank()) return Result.NONE
        if (conf < MIN_CONF || rms < MIN_RMS) return Result.NONE
        if (words(t).size < MIN_WORDS) return Result.NONE
        if (echoOverlap(t, spokenText) > MAX_ECHO_OVERLAP) return Result.NONE
        return Result.CAPTURE
    }
}
