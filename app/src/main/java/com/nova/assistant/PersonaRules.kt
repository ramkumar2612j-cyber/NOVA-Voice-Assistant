package com.nova.assistant

/**
 * v7.48 PART 9 - JARVIS PERSONA (pure Kotlin, no Android, no network, no AI call).
 *
 * The persona only changes the WAY NOVA talks: sometimes it calls the owner by name, adds one short witty line, or
 * a small "feeling" line that fits the time of day. It never changes what NOVA does, never touches a safety
 * question / confirmation / error, never touches calls, messages, recording, screen agent, payments or
 * auto-reply to other people (owner rule v7.46: no fixed text there - this file is not used on that path at all).
 * Default ON; the owner can switch it off (ExtCfg.persona).
 */
object PersonaRules {
    /** Reply kinds where a short personality touch is allowed: plain talk, answers and harmless acknowledgements. Everything else is left exactly as it is. */
    val SAFE_KINDS: Set<String> = setOf(
        "local_chat", "knowledge", "public_knowledge", "help_guide", "free_chain", "cloud",
        "local_skill", "music_play", "source_map", "context", "app_control"
    )

    /** Only short replies get a touch: a long answer stays as the AI wrote it. */
    const val MAX_REPLY = 180

    /** At least this long since the last touch, so the persona never feels pushy. */
    const val MIN_GAP_MS = 45_000L

    /** Words that mean "something went wrong / a question is waiting": no joke next to these. */
    private val PROBLEM = listOf(
        "nahi ", "nahin", "nahi.", "nahi,", "nahi!", "not ", "cannot", "can't", "couldn't", "unable", "fail", "error",
        "galat", "gadbad", "permission", "allow", "denied", "wrong", "problem", "dikkat", "unavailable",
        "haan ya nahi", "yes or no", "confirm", "sure", "pakka", "warning", "khatra", "danger", "emergency", "alert"
    )

    fun looksLikeProblem(reply: String): Boolean {
        val l = reply.lowercase() + " "
        if (l.trim().endsWith("?")) return true
        return PROBLEM.any { l.contains(it) }
    }

    /** Name used in speech: letters / spaces only, max 24 characters. Blank = no name touch. */
    fun speakName(owner: String): String {
        val cleaned = owner.filter { it.isLetter() || it == ' ' }.trim().replace(Regex("\\s+"), " ")
        return cleaned.take(24).trim()
    }

    fun daypart(hour: Int): Int = when (hour.coerceIn(0, 23)) {
        in 5..11 -> 0      // morning
        in 12..16 -> 1     // afternoon
        in 17..21 -> 2     // evening
        else -> 3          // night
    }

    private val NAME_HI = listOf("Ji %s.", "Haazir hoon, %s.", "Samajh gaya, %s.", "Haan %s.")
    private val NAME_EN = listOf("At your service, %s.", "Right away, %s.", "Understood, %s.", "Yes, %s.")

    private val WIT_HI = listOf("Aur hukum?", "Hukum kijiye.", "Main yahin hoon.", "Agla kaam bolo.")
    private val WIT_EN = listOf("Anything else?", "Standing by.", "Your move.", "Ready when you are.")

    private val FEEL_HI = listOf(
        listOf("Subah ka josh full hai!", "Naya din, nayi energy."),
        listOf("Dopahar hai, paani pee lo.", "Din aadha nikal gaya."),
        listOf("Shaam ho gayi, thodi chai ho jaye?", "Din kaisa raha?"),
        listOf("Itni raat ko jaag rahe ho? Main to jaagta hi hoon.", "Raat bahut ho gayi, neend bhi zaroori hai.")
    )
    private val FEEL_EN = listOf(
        listOf("Morning energy is full.", "New day, fresh start."),
        listOf("Afternoon already. Drink some water.", "Half the day is gone."),
        listOf("Evening now. How was the day?", "Time for a cup of tea?"),
        listOf("Up this late? I never sleep anyway.", "It is late. Sleep matters too.")
    )

    /** Which kind of touch this turn gets: 0 name line first, 1 witty tail, 2 feeling tail, 3 nothing. Deterministic from the seed. */
    fun styleFor(seed: Int): Int = when (Math.floorMod(seed, 6)) {
        0 -> 0
        2 -> 1
        4 -> 2
        else -> 3
    }

    private fun join(reply: String, tail: String): String {
        val r = reply.trimEnd()
        return if (r.isEmpty()) tail else if (r.last() in ".!?\u0964") "$r $tail" else "$r. $tail"
    }

    /**
     * Returns [reply] unchanged, or [reply] with one short persona line.
     * [seed] is a turn counter (deterministic, testable). [sinceLastTouchMs] = time since the last decorated reply.
     */
    fun decorate(
        reply: String,
        kind: String,
        owner: String,
        english: Boolean,
        hour: Int,
        seed: Int,
        sinceLastTouchMs: Long,
        hasPending: Boolean,
        failed: Boolean,
        silent: Boolean,
        enabled: Boolean = true
    ): String {
        if (!enabled || reply.isBlank() || failed || silent || hasPending) return reply
        if (kind !in SAFE_KINDS) return reply
        if (reply.length > MAX_REPLY) return reply
        if (sinceLastTouchMs in 0 until MIN_GAP_MS) return reply
        if (looksLikeProblem(reply)) return reply
        val name = speakName(owner)
        var style = styleFor(seed)
        if (style == 3) return reply
        if (style == 0 && name.isEmpty()) style = 1   // no name known: a witty tail instead
        val pick = Math.floorMod(seed / 6, 4)
        return when (style) {
            0 -> {
                val pat = (if (english) NAME_EN else NAME_HI)[pick]
                String.format(pat, name) + " " + reply.trimStart()
            }
            1 -> join(reply, (if (english) WIT_EN else WIT_HI)[pick])
            else -> {
                val pool = (if (english) FEEL_EN else FEEL_HI)[daypart(hour)]
                join(reply, pool[Math.floorMod(seed / 6, pool.size)])
            }
        }
    }
}
