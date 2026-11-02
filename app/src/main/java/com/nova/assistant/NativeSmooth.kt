package com.nova.assistant

/**
 * v7.38 PART 3: pure rules for native smoothness. No Android classes, so a JUnit test can run them.
 * MainActivity maps onTrimMemory / onLowMemory / onPause / onResume to these and sends the result to the page (window.novaNative).
 */
object NativeSmooth {
    /** Same numbers as ComponentCallbacks2 (written out so tests need no Android jar): RUNNING_MODERATE 5, RUNNING_LOW 10, RUNNING_CRITICAL 15, UI_HIDDEN 20, BACKGROUND 40, MODERATE 60, COMPLETE 80. */
    const val UI_HIDDEN = 20
    const val RUNNING_MODERATE = 5
    const val RUNNING_LOW = 10
    const val RUNNING_CRITICAL = 15

    /**
     * Pressure sent to the page: 0 none, 1 light (one effects level down), 2 heavy (at least level 3), 3 critical (level 4, WebGL orb off).
     * UI_HIDDEN (20) means "our screen went to the background": that is NOT memory pressure, the page already pauses on its own.
     * Levels >= 40 only arrive while we are in the background, so they never change the page.
     */
    fun pressure(trimLevel: Int, lowMemoryCallback: Boolean = false): Int {
        if (lowMemoryCallback) return 3
        return when {
            trimLevel >= UI_HIDDEN -> 0
            trimLevel >= RUNNING_CRITICAL -> 3
            trimLevel >= RUNNING_LOW -> 2
            trimLevel >= RUNNING_MODERATE -> 1
            else -> 0
        }
    }

    /** At most one page message per 3 s for the same or a weaker pressure (Android can fire several callbacks in a row). A stronger pressure always goes through. */
    fun shouldSend(nowMs: Long, lastMs: Long, lastPressure: Int, pressure: Int): Boolean {
        if (pressure <= 0) return false
        if (pressure > lastPressure) return true
        return nowMs - lastMs >= 3_000L
    }

    /** JS for the page. Guarded like the other Kotlin -> page calls, so an older page without the script never throws. */
    fun pressureJs(pressure: Int): String = "window.novaNative&&novaNative('mem'," + pressure.coerceIn(0, 3) + ")"
    fun backgroundJs(background: Boolean): String = "window.novaNative&&novaNative('bg'," + (if (background) 1 else 0) + ")"
}
