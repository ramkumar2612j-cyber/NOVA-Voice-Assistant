package com.nova.assistant

/**
 * Pure rules for voice screen control (tap / type / scroll / swipe). No Android classes, so every rule
 * here is unit-testable. The Accessibility service does the actual work; these rules decide WHAT is allowed.
 *
 * Safety model (v6.98, owner decision 16 Oct 2026: NOVA acts on his spoken order directly):
 *  - tap/type are refused ONLY in the package installer, permission screens, system Settings and the Play Store
 *    (so NOVA cannot grant itself permissions, install something or change device security by itself).
 *    Banking / payment / wallet apps, NOVA's own screens, windows whose package cannot be read and password
 *    fields are NOT blocked any more: NOVA taps / types there when the owner orders it. Scrolling is allowed everywhere.
 *  - Risky labels (send, pay, delete, ...) no longer need a spoken yes (see AgentRules.check, ExtCfg.skipRiskyConfirm).
 *  - What NOVA READS is a separate rule: [readProtected] keeps bank / payment / settings / own / unreadable screens
 *    and password text OUT of screen reading and out of every cloud AI call.
 */
object Control {

    val RISKY = setOf(
        "send", "pay", "payment", "delete", "remove", "buy", "purchase", "transfer", "order", "uninstall",
        "erase", "reset", "submit", "post", "confirm", "bhejo", "bhej", "hatao", "kharido"
    )

    /** Places where tap / type is refused. */
    private val BLOCKED_PACKAGES = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.android.settings",
        "com.android.vending"
    )

    /** Packages whose screen text is never read or sent to a cloud AI (bank / payment apps), kept from the old rule. */
    private val READ_ONLY_SHIELD = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp"
    )

    private val BLOCKED_FRAGMENTS = listOf("bank", "wallet", "upi", "payment")

    fun isRiskyLabel(label: String): Boolean =
        Logic.norm(label).split(" ").any { it in RISKY }

    /** kind is "tap", "type" or "scroll". Scroll is harmless; tap / type are refused only in installer, permission, Settings and Play Store. */
    fun blocked(pkg: String?, ownPkg: String, kind: String): Boolean {
        if (kind == "scroll") return false
        if (pkg == null || pkg.isEmpty()) return false           // v6.98 owner: an unreadable window may still be acted on
        return pkg in BLOCKED_PACKAGES
    }

    /** The OLD strict rule, now used only for READING the screen / talking to a cloud AI (never for tap / type). */
    fun readProtected(pkg: String?, ownPkg: String): Boolean {
        if (pkg == null || pkg.isEmpty()) return true            // no readable window: read nothing
        if (pkg == ownPkg) return true
        if (pkg in BLOCKED_PACKAGES || pkg in READ_ONLY_SHIELD) return true
        val p = pkg.lowercase()
        return BLOCKED_FRAGMENTS.any { it in p }
    }

    private fun clean(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** 2 = the whole text equals the label, 1 = the text contains the label as whole words, 0 = no match. */
    fun matchScore(nodeText: String, label: String): Int {
        val n = clean(nodeText)
        val l = clean(label)
        if (n.isEmpty() || l.isEmpty()) return 0
        if (n == l) return 2
        return if (" $n ".contains(" $l ")) 1 else 0
    }

    /** "down"/"up"/"left"/"right" from spoken words (English and Hinglish), or null. */
    fun direction(words: Collection<String>): String? {
        for (w in words) {
            when (w) {
                "down", "neeche", "niche", "nichhe" -> return "down"
                "up", "upar", "oopar", "uper" -> return "up"
                "left", "baayein", "bayen" -> return "left"
                "right", "daayein", "dayen" -> return "right"
            }
        }
        return null
    }

    /**
     * Finger path (x1,y1,x2,y2) as fractions of the screen. "scroll down" = content moves up = the finger
     * swipes up. "swipe left" = the finger moves left.
     */
    fun swipePath(dir: String): FloatArray? = when (dir) {
        "down" -> floatArrayOf(0.5f, 0.72f, 0.5f, 0.28f)
        "up" -> floatArrayOf(0.5f, 0.28f, 0.5f, 0.72f)
        "left" -> floatArrayOf(0.8f, 0.5f, 0.2f, 0.5f)
        "right" -> floatArrayOf(0.2f, 0.5f, 0.8f, 0.5f)
        else -> null
    }

    /** Text to type: at most 500 characters, control characters removed. */
    fun cleanTyped(s: String): String =
        s.filter { !it.isISOControl() || it == ' ' }.trim().take(500)
}
