package com.nova.assistant

/**
 * v7.105 PART 2: playbook of routes. Pure rules (no Android classes), unit-testable.
 * Remembers, per coarse KIND of task, which APP route worked or failed. It never keeps task text, screen text, messages,
 * URLs with paths/queries, contacts, keys or credentials: only a sanitised short app label plus counters and a time.
 * A hint is advice for the agent, never permission to bypass the owner's request or any safety check.
 */
object PlaybookRules {
    const val MAX_ROUTES_PER_KIND = 5
    const val MAX_APPS_PER_TASK = 3
    const val MAX_HINT_CHARS = 520
    const val DECAY_AT = 40

    enum class Kind(val key: String) {
        VIDEO("video"), MUSIC("music"), MAIL("mail"), MESSAGE("message"), MAPS("maps"),
        PHOTO_AI("photo_ai"), SHOP_LOOKUP("shop_lookup"), FORM("form"), SEARCH("search"), GENERIC("generic")
    }

    data class Route(val app: String, val ok: Int, val fail: Int, val lastAtMs: Long)
    data class Entry(val kind: Kind, val routes: List<Route>)

    private val WORDS: List<Pair<Kind, List<String>>> = listOf(
        Kind.VIDEO to listOf("youtube", "video", "videos", "reel", "reels", "shorts", "netflix", "movie", "film"),
        Kind.MUSIC to listOf("gaana", "gana", "song", "songs", "music", "spotify"),
        Kind.MAIL to listOf("gmail", "mail", "email", "inbox"),
        Kind.MESSAGE to listOf("whatsapp", "message", "msg", "sms", "telegram", "text"),
        Kind.MAPS to listOf("map", "maps", "route", "rasta", "direction", "directions", "navigate"),
        Kind.PHOTO_AI to listOf("photo", "image", "picture", "logo", "banner", "poster"),
        Kind.SHOP_LOOKUP to listOf("price", "rate", "compare", "sasta", "amazon", "flipkart"),
        Kind.FORM to listOf("form", "fill", "apply", "register", "signup", "login"),
        Kind.SEARCH to listOf("search", "google", "dhundo", "khojo", "find", "news", "weather")
    )

    /** Coarse kind of a spoken job. The text itself is never stored. */
    fun classify(task: String): Kind {
        val n = task.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().take(300)
        if (n.isEmpty()) return Kind.GENERIC
        val tokens = n.split(" ").toSet()
        for (p in WORDS) {
            if (p.second.any { tokens.contains(it) }) return p.first
        }
        return Kind.GENERIC
    }

    /** Only a short, plain app label is allowed: no URL, path, query, e-mail, long number or sentence. */
    fun cleanApp(raw: String): String {
        val s = raw.lowercase().trim()
        if (s.isEmpty() || s.length > 24) return ""
        if (!s.all { it in 'a'..'z' || it in '0'..'9' || it == ' ' || it == '.' || it == '-' }) return ""
        if (s.count { it.isDigit() } > 4) return ""
        return s
    }

    fun distinctApps(raw: List<String>): List<String> =
        raw.map { cleanApp(it) }.filter { it.isNotEmpty() }.distinct().take(MAX_APPS_PER_TASK)

    /** Laplace-smoothed success rate: a route with no history scores 0.5. */
    fun score(r: Route): Double = (r.ok + 1.0) / (r.ok + r.fail + 2.0)

    private fun decay(r: Route): Route =
        if (r.ok + r.fail > DECAY_AT) Route(r.app, (r.ok + 1) / 2, (r.fail + 1) / 2, r.lastAtMs) else r

    /**
     * Success credits every app opened during the task (up to 3). Failure charges only the LAST app opened, where the task died.
     * A route that never worked and failed 5 times is pruned. At most MAX_ROUTES_PER_KIND routes are kept per kind.
     */
    fun record(old: List<Entry>, kind: Kind, apps: List<String>, success: Boolean, nowMs: Long): List<Entry> {
        val clean = distinctApps(apps)
        if (clean.isEmpty()) return old
        val touched = if (success) clean else listOf(clean.last())
        val current = old.firstOrNull { it.kind == kind }?.routes ?: emptyList()
        val map = LinkedHashMap<String, Route>()
        current.forEach { map[it.app] = it }
        for (app in touched) {
            val p = map[app]
            val ok = (p?.ok ?: 0) + (if (success) 1 else 0)
            val fail = (p?.fail ?: 0) + (if (success) 0 else 1)
            map[app] = decay(Route(app, ok.coerceAtMost(999), fail.coerceAtMost(999), maxOf(nowMs, p?.lastAtMs ?: 0L)))
        }
        val kept = map.values
            .filter { !(it.ok == 0 && it.fail >= 5) }
            .sortedWith(compareByDescending<Route> { score(it) }.thenByDescending { it.lastAtMs }.thenBy { it.app })
            .take(MAX_ROUTES_PER_KIND)
        val others = old.filter { it.kind != kind }
        val merged = if (kept.isEmpty()) others else others + Entry(kind, kept)
        return merged.sortedBy { it.kind.key }
    }

    /** Short advice for the agent prompt, or "" when nothing useful is known. */
    fun hint(entries: List<Entry>, kind: Kind, en: Boolean = false): String {
        val routes = entries.firstOrNull { it.kind == kind }?.routes ?: return ""
        val good = routes.filter { it.ok >= 1 && score(it) >= 0.5 }.take(2)
        val bad = routes.filter { it.fail >= 2 && score(it) < 0.4 }.take(2)
        if (good.isEmpty() && bad.isEmpty()) return ""
        val sb = StringBuilder(
            if (en) "ROUTE MEMORY (hint only, never overrides the owner's request or safety):\n"
            else "PEHLE KAAM KARNE WALE RASTE (sirf hint, owner ki baat ya safety se upar nahi):\n"
        )
        good.forEach {
            sb.append(if (en) "- worked before: ${it.app} (${it.ok} ok, ${it.fail} failed). Try it first.\n"
            else "- pehle chala: ${it.app} (${it.ok} baar kaam, ${it.fail} baar fail). Pehle isse try karo.\n")
        }
        bad.forEach {
            sb.append(if (en) "- often failed: ${it.app} (${it.fail} failed). Try another route first.\n"
            else "- aksar fail hua: ${it.app} (${it.fail} baar fail). Pehle doosra raasta try karo.\n")
        }
        sb.append(if (en) "If a remembered route fails now, use a different safe route." else "Agar ye raasta abhi fail ho to koi doosra safe raasta try karo.")
        return sb.toString().take(MAX_HINT_CHARS)
    }

    fun summary(entries: List<Entry>, en: Boolean = false): String {
        if (entries.isEmpty()) return if (en) "No saved routes yet." else "Abhi koi raasta save nahi hai."
        return entries.joinToString("\n") { e ->
            e.kind.key + ": " + e.routes.joinToString(", ") { "${it.app} (${it.ok}/${it.fail})" }
        }.take(700)
    }
}
