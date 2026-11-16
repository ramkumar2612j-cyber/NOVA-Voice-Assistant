package com.nova.assistant

/** v7.08: the "allow Display over other apps" nudge repeats every 10 minutes while the card still cannot show (it used to be once per service life). Pure Kotlin. */
object OverlayNudgeRules {
    const val RENUDGE_MS = 10 * 60_000L

    /** [lastAt] = time of the last nudge, 0 = never. A clock that moved backwards also counts as due. */
    fun due(lastAt: Long, now: Long): Boolean = lastAt == 0L || now < lastAt || now - lastAt >= RENUDGE_MS
}
