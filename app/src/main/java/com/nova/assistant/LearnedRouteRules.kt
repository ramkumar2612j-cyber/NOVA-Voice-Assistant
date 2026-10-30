package com.nova.assistant

import java.security.MessageDigest

/**
 * v7.143 STEP 3 OF 10: learned routes. When an agent job ends VERIFIED (the independent screen verifier said so) and every step was a plain,
 * harmless one (open an app, tap a generic button, scroll, back), the steps are remembered under a one-way fingerprint of the spoken job.
 * The next time the SAME job is spoken, NovaService replays those steps without an AI plan and checks the result again. Pure Kotlin
 * (no Android), unit-testable. Nothing here executes anything.
 *
 * What is stored: a hash of the job (not the words), app names, and tap labels that are either generic buttons ("reels", "search",
 * "subscriptions") or words the owner himself said in that job. NEVER typed text (a job with a typing step is not learned), numbers,
 * messages, passwords or anything money / account / send / delete related. A job that needed a spoken yes, a retry, a stuck-hint,
 * a repair round or an owner answer is not learned either: only clean runs are.
 */
object LearnedRouteRules {
    const val MAX_STEPS = 8
    const val MAX_ROUTES = 40
    const val MAX_FAILS = 2
    const val MAX_AGE_MS = 60L * 24 * 60 * 60 * 1000

    class Step(val act: String, val v: String)

    class Route(val key: String, val steps: List<Step>, val ok: Int, val fail: Int, val lastAtMs: Long)

    private val GENERIC = setOf(
        "home", "search", "explore", "reels", "reel", "shorts", "subscriptions", "library", "you", "profile", "notifications", "inbox",
        "messages", "chats", "chat", "calls", "status", "updates", "communities", "community", "for", "following", "discover", "videos",
        "music", "podcasts", "liked", "songs", "playlists", "your", "feed", "stories", "activity", "trending", "movies", "tv", "shows",
        "downloads", "history", "saved", "favorites", "recent", "new", "tab", "tabs", "menu", "more", "all", "watch", "live"
    )

    /** Words that are never learned as a tap target, even when the owner said them. Replay ALSO runs AgentRules.check on every tap. */
    private val DENY = setOf(
        "send", "bhejo", "bhej", "pay", "payment", "payments", "buy", "order", "delete", "remove", "post", "share", "install", "uninstall",
        "password", "otp", "pin", "bank", "upi", "transfer", "logout", "login", "signin", "signout", "sign", "log", "settings", "setting",
        "account", "money", "cart", "checkout", "confirm", "subscribe", "follow", "unfollow", "block", "report", "call", "dial", "purchase",
        "book", "submit", "accept", "allow", "grant", "reset", "erase", "clear", "create", "upload", "ok", "yes", "agree", "continue", "next"
    )

    private val WAKE_FILL = Regex("^(?:(?:hey |ok )?nova |please |zara |jara |abhi |now )+")

    fun normTask(task: String): String = WAKE_FILL.replace(DirectRoute.norm(task), "").trim()

    /** One-way key of the spoken job. Empty = too short to learn. */
    fun fingerprint(task: String): String {
        val n = normTask(task)
        if (n.length < 8) return ""
        val d = MessageDigest.getInstance("SHA-256").digest(n.toByteArray(Charsets.UTF_8))
        return d.take(8).joinToString("") { "%02x".format(it) }
    }

    /** The clean label to store for a tap, or null when it must not be learned. */
    fun safeLabel(label: String, task: String): String? {
        val n = DirectRoute.norm(label)
        if (n.length < 2 || n.length > 28) return null
        if (n.any { it.isDigit() }) return null
        val words = n.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 4) return null
        if (words.any { it in DENY }) return null
        val taskWords = normTask(task).split(" ").filter { it.isNotEmpty() }.toSet()
        val generic = words.all { it in GENERIC }
        val ownerSaid = words.all { it in taskWords }
        return if (generic || ownerSaid) n else null
    }

    /**
     * One executed agent action -> a step to remember. null = this job must NOT be learned (typing, long press, home, picture...).
     * [skip] true = a harmless pause that is simply not remembered.
     */
    fun stepFor(act: String, app: String, label: String, dir: String, task: String): Step? = when (act) {
        "open_app" -> DirectRoute.norm(app).takeIf { it.isNotEmpty() && it.length <= 30 }?.let { Step("open_app", it) }
        "tap" -> safeLabel(label, task)?.let { Step("tap", it) }
        "scroll" -> dir.lowercase().takeIf { it in setOf("up", "down", "left", "right") }?.let { Step("scroll", it) }
        "back" -> Step("back", "")
        else -> null
    }

    fun isSkippable(act: String): Boolean = act == "wait"

    /** A clean run: 1..MAX_STEPS steps and at least one open_app or tap. */
    fun learnable(steps: List<Step>): Boolean =
        steps.size in 1..MAX_STEPS && steps.any { it.act == "open_app" || it.act == "tap" }

    fun usable(r: Route, nowMs: Long): Boolean =
        r.steps.isNotEmpty() && r.steps.size <= MAX_STEPS && r.fail < MAX_FAILS && nowMs - r.lastAtMs <= MAX_AGE_MS

    /** Adds a new route or replaces the steps of an existing one and counts one more success. Oldest routes drop first. */
    fun record(routes: List<Route>, key: String, steps: List<Step>, nowMs: Long): List<Route> {
        if (key.isEmpty() || !learnable(steps)) return routes
        val old = routes.firstOrNull { it.key == key }
        val fresh = Route(key, steps, (old?.ok ?: 0) + 1, 0, nowMs)
        return (routes.filter { it.key != key } + fresh).sortedByDescending { it.lastAtMs }.take(MAX_ROUTES)
    }

    /** A replayed route worked again (verified). */
    fun bumpOk(routes: List<Route>, key: String, nowMs: Long): List<Route> =
        routes.map { if (it.key == key) Route(it.key, it.steps, it.ok + 1, 0, nowMs) else it }

    /** A replay failed. After MAX_FAILS the route is forgotten. */
    fun markFail(routes: List<Route>, key: String, nowMs: Long): List<Route> =
        routes.mapNotNull {
            if (it.key != key) it
            else if (it.fail + 1 >= MAX_FAILS) null
            else Route(it.key, it.steps, it.ok, it.fail + 1, nowMs)
        }

    /** Position of the ONE clickable item whose cleaned label equals [want]. null = not there or ambiguous (never guess). */
    fun findIndex(items: List<Pair<Int, String>>, want: String): Int? {
        val hit = items.filter { DirectRoute.norm(it.second) == want }
        return if (hit.size == 1) hit[0].first else null
    }

    fun summary(routes: List<Route>, en: Boolean): String {
        if (routes.isEmpty()) return ""
        val steps = routes.sumOf { it.steps.size }
        return if (en) "${routes.size} learned routes ($steps steps in total), replayed without an AI plan only for the same spoken job"
        else "${routes.size} seekhe hue raaste (kul $steps step), sirf wahi kaam dobara bolne par bina AI plan ke chalte hain"
    }
}
