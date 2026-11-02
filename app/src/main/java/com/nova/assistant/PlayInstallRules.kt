package com.nova.assistant

/**
 * v6.97 wip14 (Part 3): pure rules for the narrow Play Store "Install" press. No Android classes, so it is unit tested.
 *
 * What this file decides (NovaAccessibilityService.playInstall will act on it in Part 4):
 *  - arm / armedFor: the press is allowed only inside a 60 s window that NOVA opened itself for ONE package.
 *  - pageMatches: the Play page on screen really is the app that was asked for (exact title, never a lookalike).
 *  - decide: from the screen text only, TAP / STARTED / INSTALLED / NEED_LOGIN / PAID / WAIT.
 *  - searchResultMatches: for an UNKNOWN app name, a search result is accepted only when its title is exactly the spoken name.
 *
 * It never presses anything. Only an exact "Install" line can ever give TAP. Price / Buy, Sign in, "Install anyway" and Update never do.
 * Limit (honest): button words are matched in English. A Play Store in another language gives WAIT, never a guess.
 */
object PlayInstallRules {
    const val WINDOW_MS = 60_000L

    enum class Decision { TAP, STARTED, INSTALLED, NEED_LOGIN, PAID, WAIT }

    private val ARMED = HashMap<String, Long>()

    // ------------------------------------------------------------------ armed window (per package)

    fun arm(pkg: String, now: Long, store: MutableMap<String, Long> = ARMED) {
        val p = pkg.trim()
        if (p.isEmpty()) return
        store[p] = now
    }

    fun armedFor(pkg: String, now: Long, store: MutableMap<String, Long> = ARMED): Boolean {
        val p = pkg.trim()
        if (p.isEmpty()) return false
        val at = store[p] ?: return false
        val age = now - at
        return age in 0..WINDOW_MS
    }

    fun disarm(pkg: String, store: MutableMap<String, Long> = ARMED) {
        store.remove(pkg.trim())
    }

    // ------------------------------------------------------------------ screen text helpers

    private fun clean(raw: String): String =
        raw.replace("\u200B", "").replace("\u00A0", " ").trim().lowercase()
            .removeSuffix("...").removeSuffix("\u2026").trim()
            .replace(Regex("\\s+"), " ")

    /** Only short lines can be a button label. A long line is description text and is ignored. */
    private fun buttons(lines: List<String>): Set<String> =
        lines.map { clean(it) }.filter { it.isNotEmpty() && it.length <= 40 }.toSet()

    private val PRICE = Regex("^(?:[\\u20B9$\\u20AC\\u00A3]|rs\\.?|inr|usd)\\s?\\d[\\d,.]*$")
    private val PROGRESS = setOf("installing", "downloading", "pending", "waiting for download", "waiting to download", "installing app", "download pending")

    // ------------------------------------------------------------------ page + search matching

    private val TITLE_SUFFIX = setOf("messenger", "app", "official")
    private val NAME_FILLER = setOf("app", "apps", "the", "official", "play", "store")

    private fun nameWords(s: String): List<String> =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(" ").filter { it.isNotEmpty() && it !in NAME_FILLER }

    /**
     * True when one line on screen is the app's title: exactly [label], or [label] plus one harmless word ("WhatsApp Messenger").
     * "WhatsApp Business", "YouTube Music", "Instagram Lite" are different apps and do NOT match "WhatsApp" / "YouTube" / "Instagram".
     */
    fun pageMatches(lines: List<String>, label: String): Boolean {
        val want = clean(label)
        if (want.isEmpty()) return false
        return lines.map { clean(it) }.any { l ->
            l == want || (l.startsWith("$want ") && l.removePrefix("$want ") in TITLE_SUFFIX)
        }
    }

    /** Unknown app: a Play search result is accepted only when its cleaned words equal the cleaned spoken words. */
    fun searchResultMatches(resultTitle: String, spokenName: String): Boolean {
        val a = nameWords(resultTitle)
        val b = nameWords(spokenName)
        return a.isNotEmpty() && a == b
    }

    // ------------------------------------------------------------------ the decision

    /**
     * [afterTap] = false: look at the page BEFORE pressing. true: look again AFTER the press.
     * Order matters: anything unsafe wins over TAP.
     */
    fun decide(lines: List<String>, afterTap: Boolean): Decision {
        val b = buttons(lines)
        if (b.any { it.startsWith("install anyway") }) return Decision.WAIT      // a warning dialog: never press it
        val hasInstall = "install" in b
        val installed = "open" in b && ("uninstall" in b || "update" in b) && !hasInstall
        if (afterTap) {
            if (b.any { it in PROGRESS }) return Decision.STARTED
            if ("cancel" in b && !hasInstall) return Decision.STARTED
            if (installed && "uninstall" in b) return Decision.STARTED           // finished so fast that only Open is left
        } else {
            if (installed) return Decision.INSTALLED
        }
        if (b.any { it == "sign in" || it == "log in" || it == "add account" || it.startsWith("sign in to") }) return Decision.NEED_LOGIN
        if (!hasInstall && b.any { PRICE.matches(it) || it == "buy" || it == "buy now" }) return Decision.PAID
        if (!afterTap && hasInstall) return Decision.TAP
        return Decision.WAIT
    }

    /** The one gate NovaAccessibilityService must pass before it presses: armed for this package AND the right app page AND TAP. */
    fun mayTap(pkg: String, label: String, lines: List<String>, now: Long, store: MutableMap<String, Long> = ARMED): Boolean =
        armedFor(pkg, now, store) && pageMatches(lines, label) && decide(lines, afterTap = false) == Decision.TAP
}
