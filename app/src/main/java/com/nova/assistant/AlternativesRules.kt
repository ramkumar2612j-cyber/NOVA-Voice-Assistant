package com.nova.assistant

/**
 * v7.106 PART 3: alternatives engine (route switching inside ONE job). Pure rules (no Android classes), unit-testable.
 * For each coarse task KIND there is a fixed, ordered list of candidate routes (an app label or a site host, nothing else).
 * `next(...)` is deterministic: it skips routes already tried in this job, orders the rest by the Part 2 playbook score
 * (worked before = first, often failed = last, unknown = neutral 0.5, ties keep the list order), allows at most MAX_SWITCHES
 * per job and never returns a route that is on the HARD DENY / protected list.
 * The result is only a HINT for the agent: the model still emits the action and AgentRules.check still vets it.
 * Nothing is installed and no task text, screen text or contact content is used or stored.
 */
object AlternativesRules {
    const val MAX_SWITCHES = 4
    const val TAP_FAIL_TRIGGER = 3
    const val MAX_LINE_CHARS = 260

    /** Why a switch is being asked for. It only changes the wording of the hint, never the order of the routes. */
    enum class Gap { APP_MISSING, STUCK, TAP_FAILED }

    /** label = short app label or site host (a-z 0-9 space . -). site = open it in Chrome through openAppOrSite. */
    data class Candidate(val label: String, val site: Boolean)

    private fun app(l: String) = Candidate(l, false)
    private fun web(l: String) = Candidate(l, true)

    private val BROWSERS = listOf(app("chrome"), app("samsung internet"), app("firefox"), app("edge"))

    private val ROUTES: Map<PlaybookRules.Kind, List<Candidate>> = mapOf(
        PlaybookRules.Kind.VIDEO to listOf(app("youtube"), web("youtube.com"), web("google.com")),
        PlaybookRules.Kind.MUSIC to listOf(app("gaana"), app("spotify"), app("youtube"), web("youtube.com")),
        PlaybookRules.Kind.MAIL to listOf(app("gmail"), web("mail.google.com")),
        PlaybookRules.Kind.MESSAGE to listOf(app("whatsapp"), app("whatsapp business"), app("messages")),
        PlaybookRules.Kind.MAPS to listOf(app("maps"), web("maps.google.com"), web("google.com")),
        PlaybookRules.Kind.PHOTO_AI to listOf(app("gemini"), app("chatgpt"), web("gemini.google.com"), web("chatgpt.com")),
        PlaybookRules.Kind.SHOP_LOOKUP to listOf(app("amazon"), app("flipkart"), web("google.com")),
        PlaybookRules.Kind.FORM to BROWSERS,
        PlaybookRules.Kind.SEARCH to listOf(app("chrome"), app("samsung internet"), app("firefox"), app("google")),
        PlaybookRules.Kind.GENERIC to emptyList()
    )

    /** Same words as AgentRules.HARD (kept private there; a contract test checks they stay in sync). */
    private val DENY_WORDS = setOf(
        "pay", "payment", "buy", "purchase", "transfer", "order", "uninstall", "erase", "reset",
        "upgrade", "subscribe", "subscription", "checkout", "donate", "kharido",
        "password", "otp", "passcode"
    )

    /** Same idea as Control.blocked(): bank / wallet / UPI / payment apps, Settings, permission screens, installer, Play Store, NOVA itself. */
    private val DENY_FRAGMENTS = listOf(
        "bank", "wallet", "upi", "payment", "paytm", "phonepe", "gpay", "paisa",
        "settings", "permission", "installer", "vending", "play store", "playstore", "nova"
    )

    fun routes(kind: PlaybookRules.Kind): List<Candidate> = ROUTES[kind] ?: emptyList()

    fun hasRoutes(kind: PlaybookRules.Kind): Boolean = routes(kind).isNotEmpty()

    /** True when the label is on the HARD DENY / protected list and must never be suggested or opened by a switch. */
    fun isDenied(label: String): Boolean {
        val s = label.lowercase().trim()
        if (s.isEmpty()) return true
        val tokens = s.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (tokens.any { it in DENY_WORDS }) return true
        return DENY_FRAGMENTS.any { s.contains(it) }
    }

    /** A host from the fixed tables (so a model-typed free text never becomes a website through this path). */
    fun isKnownSite(label: String): Boolean {
        val s = PlaybookRules.cleanApp(label)
        if (s.isEmpty() || !s.contains('.')) return false
        return ROUTES.values.any { l -> l.any { it.site && it.label == s } }
    }

    /** Repeated failed taps (not the first one) count as a gap. */
    fun tapGapReached(consecutiveFailedTaps: Int): Boolean = consecutiveFailedTaps >= TAP_FAIL_TRIGGER

    /**
     * Next route to try, or null when nothing safe is left (or MAX_SWITCHES was used).
     * `triedThisJob` = app labels / hosts already tried in this job (any spelling; they are sanitised the same way as the playbook).
     */
    fun next(
        kind: PlaybookRules.Kind,
        triedThisJob: Collection<String>,
        playbookEntries: List<PlaybookRules.Entry>,
        failureGap: Gap,
        switchesDone: Int = 0
    ): Candidate? {
        if (switchesDone >= MAX_SWITCHES) return null
        val tried = triedThisJob.map { PlaybookRules.cleanApp(it) }.filter { it.isNotEmpty() }.toSet()
        val known = playbookEntries.firstOrNull { it.kind == kind }?.routes?.associateBy { it.app } ?: emptyMap()
        fun s(c: Candidate): Double = known[c.label]?.let { PlaybookRules.score(it) } ?: 0.5
        return routes(kind).withIndex()
            .filter { it.value.label !in tried && !isDenied(it.value.label) }
            .sortedWith(compareByDescending<IndexedValue<Candidate>> { s(it.value) }.thenBy { it.index })
            .firstOrNull()?.value
    }

    private fun name(failed: String) = failed.ifBlank { "this route" }

    private fun how(c: Candidate) = if (c.site) "open_app \"${c.label}\" (opens the website in Chrome)" else "open_app \"${c.label}\""

    /** Line for the agent history (a hint, never a command). */
    fun hintLine(failed: String, next: Candidate, gap: Gap, switchNo: Int): String {
        val why = when (gap) {
            Gap.APP_MISSING -> "it could not be opened"
            Gap.STUCK -> "the screen stopped changing"
            Gap.TAP_FAILED -> "several taps failed"
        }
        return ("ROUTE SWITCH: ${name(failed)} did not work; try ${next.label} ($why, switch $switchNo/$MAX_SWITCHES). " +
            "Use ${how(next)}. Hint only: the owner's request and the safety rules stay the same.").take(MAX_LINE_CHARS)
    }

    /** Line for the owner (Hinglish / English). */
    fun noteLine(failed: String, next: Candidate, switchNo: Int, en: Boolean): String =
        (if (en) "Switching route: ${name(failed)} did not work, trying ${next.label} ($switchNo/$MAX_SWITCHES)"
        else "Raasta badal raha hu: ${name(failed)} kaam nahi aaya, ab ${next.label} try kar raha hu ($switchNo/$MAX_SWITCHES)").take(MAX_LINE_CHARS)

    /** No safe route is left: the agent must stop honestly with the real blocker, never fake success. */
    fun noRouteLine(failed: String): String =
        ("ROUTE SWITCH: ${name(failed)} did not work and no other safe route is left. " +
            "Do NOT pretend it worked: use fail (or ask) and tell the owner the exact blocker.").take(MAX_LINE_CHARS)

    fun noRouteNote(failed: String, en: Boolean): String =
        if (en) "${name(failed)} did not work and I have no other safe route left"
        else "${name(failed)} nahi chala aur koi doosra safe raasta bacha nahi"

    /** Only a number goes into the ledger progress line, never any text. */
    fun progressSuffix(switches: Int): String = if (switches > 0) "; route switches " + switches.coerceAtMost(MAX_SWITCHES) else ""
}
