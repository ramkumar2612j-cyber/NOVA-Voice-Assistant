package com.nova.assistant

/**
 * v7.115 TV / room-talk guard for the wake word (pure Kotlin, unit tested).
 * Logic.wakeVerified already judges ONE word (confidence + length). This adds two things it cannot see:
 *  1. LONG talk: a spoken "nova" (plus a short command in the same breath) is a few words; a TV or a room full of people is a long
 *     run of [unk] words, so a "nova" guessed at the start of that run is rejected.
 *  2. SELF-TUNING: every false wake in a row asks for a more confident "nova" next time (relaxes by itself, like WakeAdapt).
 * No word data in the result = cannot judge = allowed (a real wake is never blocked on an old engine).
 */
object WakeGuardRules {
    const val MAX_WORDS = 7               // words in one wake result; longer = people / TV talking
    const val BASE_CONF = 0.75            // same floor as Logic.WAKE_MIN_CONF
    const val STEP_CONF = 0.05            // +0.05 per false wake in a row
    const val MAX_CONF = 0.92
    const val WEAK_START_CONF = 0.85      // "[unk] nova": an unknown sound before nova needs a surer nova

    private val TEXT = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"")
    private val OBJ = Regex("\\{[^{}]*\\}")
    private val WORD = Regex("\"word\"\\s*:\\s*\"([^\"]*)\"")
    private val CONF = Regex("\"conf\"\\s*:\\s*(-?[0-9.]+)")

    fun words(json: String): List<String> {
        val t = TEXT.find(json)?.groupValues?.get(1)?.trim() ?: return emptyList()
        return t.split(" ").filter { it.isNotBlank() }
    }

    fun tooLong(json: String): Boolean = words(json).size > MAX_WORDS

    fun weakStart(json: String): Boolean = words(json).firstOrNull() == "[unk]"

    /** Confidence of the wake word in a Vosk result with word data, or null when there is none. */
    fun wakeConf(json: String, wake: String): Double? {
        for (m in OBJ.findAll(json)) {
            val o = m.value
            val w = WORD.find(o)?.groupValues?.get(1) ?: continue
            if (w != wake) continue
            val c = CONF.find(o)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            return c
        }
        return null
    }

    /** Confidence the wake word must reach after [misses] false wakes in a row (0 = normal). */
    fun minConf(misses: Int): Double {
        val m = if (misses < 0) 0 else misses
        val v = BASE_CONF + STEP_CONF * m
        return if (v > MAX_CONF) MAX_CONF else v
    }

    /** Misses that still count: they fade after the same quiet period WakeAdapt uses. */
    fun activeMisses(misses: Int, lastMissAt: Long, now: Long): Int =
        if (misses <= 0 || lastMissAt <= 0L || now - lastMissAt > WakeAdapt.RELAX_MS) 0 else misses

    fun allow(json: String, wake: String, misses: Int): Boolean {
        if (tooLong(json)) return false
        val conf = wakeConf(json, wake) ?: return true
        var need = minConf(misses)
        if (weakStart(json) && need < WEAK_START_CONF) need = WEAK_START_CONF
        return conf >= need
    }
}
