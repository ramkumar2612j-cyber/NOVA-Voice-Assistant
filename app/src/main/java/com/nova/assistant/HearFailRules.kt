package com.nova.assistant

/**
 * v7.115 "Samajh nahi aaya" after the wake word (pure Kotlin, unit tested).
 * Two different problems used to get the same spoken line:
 *  - a FALSE wake (TV, noise): a very short capture with no real words. NOVA now goes back to sleep silently, no spoken line.
 *  - a REAL try that the offline English ear cannot read (Hindi / Hinglish without a Groq key or the Hindi pack): after 2 in a row
 *    NOVA says ONCE what is missing instead of repeating "dobara bolo" forever (again at most every 30 minutes).
 */
object HearFailRules {
    enum class Action { SLEEP, RETRY, SETUP_HELP }

    const val SHORT_AUDIO_S = 1.2
    const val HELP_AFTER = 2
    const val HELP_COOLDOWN_MS = 30 * 60_000L

    fun decide(audioSecs: Double, streak: Int, hasGroq: Boolean, hindiReady: Boolean, isFollow: Boolean, lastHelpAt: Long, now: Long): Action {
        if (isFollow) return Action.SLEEP
        if (audioSecs < SHORT_AUDIO_S) return Action.SLEEP
        val canHelp = !hasGroq && !hindiReady
        val cooled = lastHelpAt <= 0L || now < lastHelpAt || now - lastHelpAt >= HELP_COOLDOWN_MS
        if (canHelp && streak >= HELP_AFTER && cooled) return Action.SETUP_HELP
        return Action.RETRY
    }

    fun helpText(en: Boolean): String =
        if (en) "I only hear English offline. For Hindi / Hinglish add a Groq key in Settings or download the HINDI PACK."
        else "Offline main sirf English sun pata hoon. Hindi / Hinglish ke liye Settings me Groq key daalo ya HINDI PACK download karo."
}
