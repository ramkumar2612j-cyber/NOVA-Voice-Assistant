package com.nova.assistant

/**
 * Lets NOVA press "Install"/"Update" on Android's own install-confirmation screen, but ONLY for NOVA's own update that
 * UpdateManager already verified (same package, higher version, same signing key) and ONLY for 90 seconds after the owner started it.
 * It never presses "Install anyway" (Play Protect warning), "Open", "Done", or anything for any other app.
 * Pure Kotlin so the rules are unit-testable.
 */
object InstallTap {
    const val WINDOW_MS = 90_000L
    const val MAX_TRIES = 25

    @Volatile var until: Long = 0L
        private set

    val INSTALLERS: Set<String> = setOf(
        "com.google.android.packageinstaller", "com.android.packageinstaller",
        "com.samsung.android.packageinstaller", "com.miui.packageinstaller"
    )
    private val OK_WORDS = setOf("install", "update", "इंस्टॉल", "अपडेट", "इंस्टॉल करें", "अपडेट करें")

    fun arm(now: Long) { until = now + WINDOW_MS }
    fun disarm() { until = 0L }
    fun armed(now: Long): Boolean = until != 0L && now < until

    fun isInstaller(pkg: String?): Boolean = pkg != null && pkg in INSTALLERS

    /** Exact label match only: "Install" yes, "Install anyway" / "Open" / "Done" / "Cancel" no. */
    fun isTapLabel(label: String?): Boolean = label != null && label.trim().lowercase() in OK_WORDS
}
