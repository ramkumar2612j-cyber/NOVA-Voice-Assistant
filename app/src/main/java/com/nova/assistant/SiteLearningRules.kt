package com.nova.assistant

/**
 * v7.109 PART 6: coarse site / blocker learning. Pure Kotlin (no Android classes), unit-testable.
 * Remembers, per website HOST the owner named (max 24 chars, never a path or query) and per coarse KIND of job, how many jobs worked
 * and which BLOCKER ended the others (password / code / CAPTCHA wall, missing app, other), plus how many route switches were needed.
 * Never kept: task text, URLs with path or query, screen text, typed text, messages, contacts, keys or credentials.
 * A hint is advice for the agent, never permission to bypass the owner's request, a login wall or any safety check.
 */
object SiteLearningRules {
    const val MAX_ENTRIES = 16
    const val MAX_SITES_PER_JOB = 2
    const val MAX_HINT_CHARS = 520
    const val DECAY_AT = 40
    const val SITE_PREFIX = "site:"
    const val KIND_PREFIX = "kind:"

    enum class Blocker(val key: String) {
        PASSWORD("password"), CODE("code"), CAPTCHA("captcha"), APP_MISSING("app_missing"), OTHER("other")
    }

    /** SUCCEEDED = job done, FAILED = job failed, BLOCKED = job stopped as NEEDS_OWNER by a known blocker. */
    enum class Outcome { SUCCEEDED, FAILED, BLOCKED }

    data class Note(val key: String, val ok: Int, val blocked: Map<Blocker, Int>, val switches: Int, val lastAtMs: Long)

    /** Login wall state (Part 5) to blocker kind. Anything that is not a blocking wall has no blocker. */
    fun blockerFor(s: LoginRules.State): Blocker? = when (s) {
        LoginRules.State.PASSWORD -> Blocker.PASSWORD
        LoginRules.State.CODE -> Blocker.CODE
        LoginRules.State.CAPTCHA -> Blocker.CAPTCHA
        else -> null
    }

    /** Short plain host label the owner named, or "" (not a site, too long, has odd characters, or a bank / payment / security host). */
    fun siteLabel(host: String): String {
        var h = host.trim().lowercase()
        if (h.startsWith("www.")) h = h.substring(4)
        val clean = PlaybookRules.cleanApp(h)
        if (clean.isEmpty() || !clean.contains('.') || clean.contains(' ')) return ""
        if (WebOperatorRules.isDenied(clean)) return ""
        return clean
    }

    fun siteKey(host: String): String {
        val l = siteLabel(host)
        return if (l.isEmpty()) "" else SITE_PREFIX + l
    }

    fun kindKey(kind: PlaybookRules.Kind): String = KIND_PREFIX + kind.key

    /** A key read back from storage must still be a clean site key or a known kind key. */
    fun cleanKey(raw: String): String {
        if (raw.startsWith(SITE_PREFIX)) {
            val label = raw.substring(SITE_PREFIX.length)
            return if (siteLabel(label) == label) raw else ""
        }
        if (raw.startsWith(KIND_PREFIX)) {
            val k = raw.substring(KIND_PREFIX.length)
            return if (PlaybookRules.Kind.values().any { it.key == k }) raw else ""
        }
        return ""
    }

    fun wallCount(n: Note): Int =
        (n.blocked[Blocker.PASSWORD] ?: 0) + (n.blocked[Blocker.CODE] ?: 0) + (n.blocked[Blocker.CAPTCHA] ?: 0)

    private fun total(n: Note): Int = n.ok + n.blocked.values.sum()

    private fun decay(n: Note): Note {
        if (total(n) <= DECAY_AT) return n
        val half = n.blocked.mapValues { (it.value + 1) / 2 }.filterValues { it > 0 }
        return Note(n.key, (n.ok + 1) / 2, half, n.switches / 2, n.lastAtMs)
    }

    private fun bump(old: Note?, key: String, outcome: Outcome, blocker: Blocker?, switches: Int, nowMs: Long): Note {
        val blocked = HashMap<Blocker, Int>()
        old?.blocked?.forEach { (k, v) -> blocked[k] = v }
        var ok = old?.ok ?: 0
        when (outcome) {
            Outcome.SUCCEEDED -> ok += 1
            Outcome.FAILED -> { val b = blocker ?: Blocker.OTHER; blocked[b] = (blocked[b] ?: 0) + 1 }
            Outcome.BLOCKED -> { val b = blocker ?: Blocker.OTHER; blocked[b] = (blocked[b] ?: 0) + 1 }
        }
        val sw = ((old?.switches ?: 0) + switches.coerceIn(0, 9)).coerceAtMost(999)
        return decay(Note(key, ok.coerceAtMost(999), blocked.mapValues { it.value.coerceAtMost(999) }, sw, maxOf(nowMs, old?.lastAtMs ?: 0L)))
    }

    /**
     * Record one finished job against the job KIND and against up to two owner-named hosts.
     * `blocker` is the coarse reason when the job stopped (null = unknown, counted as OTHER on a failure).
     * At most MAX_ENTRIES notes are kept (newest first).
     */
    fun record(old: List<Note>, hosts: List<String>, kind: PlaybookRules.Kind, outcome: Outcome, blocker: Blocker?, switches: Int, nowMs: Long): List<Note> {
        val keys = ArrayList<String>()
        keys.add(kindKey(kind))
        hosts.map { siteKey(it) }.filter { it.isNotEmpty() }.distinct().take(MAX_SITES_PER_JOB).forEach { keys.add(it) }
        val map = LinkedHashMap<String, Note>()
        old.forEach { map[it.key] = it }
        for (k in keys) map[k] = bump(map[k], k, outcome, blocker, switches, nowMs)
        return map.values
            .sortedWith(compareByDescending<Note> { it.lastAtMs }.thenBy { it.key })
            .take(MAX_ENTRIES)
    }

    private fun wallWord(n: Note): String {
        val p = n.blocked[Blocker.PASSWORD] ?: 0
        val c = n.blocked[Blocker.CODE] ?: 0
        val z = n.blocked[Blocker.CAPTCHA] ?: 0
        return when {
            p >= c && p >= z -> "password"
            c >= z -> "verification code / phone approval"
            else -> "CAPTCHA"
        }
    }

    /** Short advice for the agent prompt, or "" when nothing useful is known. */
    fun hint(notes: List<Note>, hosts: List<String>, kind: PlaybookRules.Kind, en: Boolean = false): String {
        val lines = ArrayList<String>()
        val seen = HashSet<String>()
        for (h in hosts) {
            val key = siteKey(h)
            if (key.isEmpty() || !seen.add(key) || seen.size > MAX_SITES_PER_JOB) continue
            val n = notes.firstOrNull { it.key == key } ?: continue
            val label = key.removePrefix(SITE_PREFIX)
            val walls = wallCount(n)
            if (walls >= 1 && walls >= n.ok) {
                lines.add(
                    if (en) "- a ${wallWord(n)} wall stopped $label before (${walls}x): do every other step first, then tell the owner early what is needed; do not retry the login."
                    else "- $label par pehle ${wallWord(n)} wall ne rok diya (${walls} baar): baaki saare kaam pehle karo, phir owner ko jaldi bata do ki kya chahiye; login dobara try mat karo."
                )
            } else if (n.ok >= 1 && walls == 0) {
                lines.add(if (en) "- $label worked before (${n.ok} ok)." else "- $label par pehle kaam chala (${n.ok} baar).")
            }
        }
        val kn = notes.firstOrNull { it.key == kindKey(kind) }
        if (kn != null) {
            if ((kn.blocked[Blocker.APP_MISSING] ?: 0) >= 2) {
                lines.add(if (en) "- this kind of job hit a missing app before: check the exact app name first and use the alternative route early; never install automatically."
                else "- is tarah ke kaam me pehle app nahi mila: pehle exact app naam dekho aur doosra raasta jaldi try karo; auto-install nahi.")
            }
            if (kn.switches >= 2) {
                lines.add(if (en) "- route switches were needed before (${kn.switches}x): think of the alternative route early."
                else "- pehle route badalna pada (${kn.switches} baar): doosra raasta jaldi soch lo.")
            }
            if (wallCount(kn) >= 2 && wallCount(kn) >= kn.ok && lines.none { it.contains("wall") }) {
                lines.add(if (en) "- this kind of job was stopped by a login wall ${wallCount(kn)}x: if one shows up, finish the other steps and tell the owner early."
                else "- is tarah ke kaam ko login wall ne ${wallCount(kn)} baar roka: wall aaye to baaki kaam karke owner ko jaldi bata do.")
            }
        }
        if (lines.isEmpty()) return ""
        val head = if (en) "SITE MEMORY (hint only, never overrides the owner's request, a login wall or safety):\n"
        else "SITE YAAD (sirf hint, owner ki baat, login wall ya safety se upar nahi):\n"
        return (head + lines.joinToString("\n")).take(MAX_HINT_CHARS)
    }

    fun summary(notes: List<Note>, en: Boolean = false): String {
        if (notes.isEmpty()) return if (en) "No saved site lessons yet." else "Abhi koi site ki seekh save nahi hai."
        return notes.joinToString("\n") { n ->
            val b = n.blocked.entries.sortedBy { it.key.key }.joinToString(", ") { "${it.key.key} ${it.value}" }
            n.key + ": ok " + n.ok + (if (b.isNotEmpty()) ", $b" else "") + (if (n.switches > 0) ", switches ${n.switches}" else "")
        }.take(700)
    }
}
