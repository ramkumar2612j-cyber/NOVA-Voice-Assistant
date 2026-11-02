package com.nova.assistant

/**
 * v6.99 Part 2: pure text rules for a spoken STOP, no Android.
 * Only a WHOLE short utterance counts ("ruko", "ruk jao", "bas karo"); a stop word inside a longer command never stops anything.
 * Evidence level: written. Not compiled, not phone tested.
 */
object StopRules {
    private val PHRASES = setOf(
        "stop", "cancel", "chup", "quiet", "ruko", "be quiet", "shut up",
        "ruk jao", "ruk ja", "ruk jaiye", "ruko ruko", "roko", "ise roko", "isko roko",
        "bas karo", "rehne do", "mat karo", "stop it", "stop that", "stop now",
        "cancel it", "cancel karo", "cancel kar do"
    )

    /** True when the whole utterance (filler words already removed or not) is a stop request. */
    fun isStop(text: String): Boolean {
        val s = text.trim().lowercase().replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return false
        if (s in PHRASES) return true
        val noNova = s.removePrefix("nova ").trim()
        return noNova in PHRASES
    }

    /** A background job (picture, website) remembers the stop counter when it starts; if the counter moved, the owner said stop meanwhile. */
    fun wasStopped(startedAt: Int, now: Int): Boolean = startedAt != now
}
