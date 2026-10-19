package com.nova.assistant

/**
 * v6.84: pure rules of the automatic first-open setup chain (no Android classes, unit tested).
 * Before: the whole chain ran ONCE per install, so a permission the owner skipped or denied was never offered again and
 * a permission added by an app update was never asked. Now the chain re-runs on later app opens while something is
 * still missing, but it is bounded so it can never become nagging: at most [MAX_RUNS] runs per [VERSION], and each Settings
 * page is opened at most once per run and at most [MAX_OPENS] times in total.
 * Android does not let an app switch special access (overlay, accessibility, notification access ...) on by itself:
 * the chain only OPENS the right page; nothing is granted by NOVA.
 */
object SetupChainRules {
    /** Raise this when a release adds a permission / special access, so every phone gets one more round. */
    const val VERSION = 2   // v7.68 PART 16: 2 = ANSWER_PHONE_CALLS added to the permission round ("call uthao"); old phones get one more round
    const val MAX_RUNS = 4
    const val MAX_OPENS = 3

    /** Order the special-access pages are opened in (the most useful first). */
    val ORDER = listOf("overlay", "write", "battery", "alarm", "fullscreen", "install", "listener", "access", "autostart")

    /** Should this app open start a chain run? [missing] = permissions + special accesses still missing, [runs] = runs already made for this VERSION. */
    fun shouldRun(missing: Int, runs: Int): Boolean = missing > 0 && runs < MAX_RUNS

    /** The brand autostart screen cannot be read back (no API tells if it is ON), so it is offered exactly once. */
    fun maxOpens(id: String): Int = if (id == "autostart") 1 else MAX_OPENS

    /** Next Settings page to open, or null when this run is finished. */
    fun nextStep(missing: Collection<String>, doneThisRun: Set<String>, opens: Map<String, Int>): String? =
        ORDER.firstOrNull { it in missing && it !in doneThisRun && (opens[it] ?: 0) < maxOpens(it) }
}
