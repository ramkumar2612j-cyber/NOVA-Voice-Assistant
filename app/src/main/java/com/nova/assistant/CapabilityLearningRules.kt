package com.nova.assistant

/**
 * v7.103: bounded capability-gap learning. Pure rules: stores categories, not raw screens, speech, secrets or full prompts.
 * This does not magically install tools; it teaches the agent which safe recovery path to prefer next time.
 */
object CapabilityLearningRules {
    const val MAX_RECORDS = 24
    const val MAX_GUIDANCE_CHARS = 900

    enum class Gap(val key: String) {
        ACCESSIBILITY("accessibility"), PROVIDER("provider"), NETWORK("network"), SCREEN("screen"),
        STUCK("stuck"), APP_MISSING("app_missing"), PERMISSION("permission"), TIMEOUT("timeout"), UNKNOWN("unknown"),
        LOGIN("login")   // v7.109 PART 6: a password / code / CAPTCHA wall stopped the job
    }

    data class Lesson(val gap: Gap, val count: Int, val lastAtMs: Long)

    /** Convert a failure into a coarse category. Never retain the original error or screen text. */
    fun classify(reason: String): Gap {
        val n = reason.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().take(400)
        return when {
            listOf("sign in ke liye", "sign in needs", "captcha").any { n.contains(it) } -> Gap.LOGIN   // v7.109 PART 6: the exact Part 5 stop messages
            listOf("accessibility", "screen padh", "screen read", "screen nahi padh", "accessibility on").any { n.contains(it) } -> Gap.ACCESSIBILITY
            listOf("api key", "provider", "quota", "limit", "401", "403", "key check", "ai provider").any { n.contains(it) } -> Gap.PROVIDER
            listOf("internet", "network", "offline", "connection", "dns", "timeout").any { n.contains(it) } -> if (n.contains("timeout") || n.contains("deadline")) Gap.TIMEOUT else Gap.NETWORK
            listOf("permission", "permission chahiye", "grant access").any { n.contains(it) } -> Gap.PERMISSION
            listOf("app not found", "app nahi mila", "install it", "app ka naam").any { n.contains(it) } -> Gap.APP_MISSING
            listOf("deadline", "time khatam", "too many steps", "timeout").any { n.contains(it) } -> Gap.TIMEOUT
            listOf("screen par kuch badal", "stuck", "atak gaya", "nothing changed", "screen unchanged").any { n.contains(it) } -> Gap.STUCK
            listOf("screen", "button", "visible", "read").any { n.contains(it) } -> Gap.SCREEN
            else -> Gap.UNKNOWN
        }
    }

    fun guidance(gap: Gap, en: Boolean = false): String = when (gap) {
        Gap.ACCESSIBILITY -> if (en) "ACCESSIBILITY GAP: do not pretend or loop. Explain the exact Android setting needed; continue after the owner enables it." else "ACCESSIBILITY GAP: jhootha success ya loop nahi. Android ki exact setting batao; owner enable kare to aage badho."
        Gap.PROVIDER -> if (en) "PROVIDER GAP: check configured providers in order, skip cooling-down/failed providers, use local capability if suitable, and name the exact missing key/quota only at the end." else "PROVIDER GAP: configured fallback providers ko order mein check karo, failed/cooldown provider skip karo, suitable local tareeka use karo; key/quota ka blocker aakhir mein saaf batao."
        Gap.NETWORK -> if (en) "NETWORK GAP: do useful offline/local steps first; retry network once only after a meaningful wait or connectivity change; never label offline knowledge as live research." else "NETWORK GAP: pehle local/offline kaam karo; network retry sirf ek baar meaningful wait/change ke baad; offline knowledge ko live research mat bolo."
        Gap.SCREEN -> if (en) "SCREEN GAP: inspect the current screen again, scroll both directions if useful, use visible labels only, then try one different safe route." else "SCREEN GAP: current screen dobara dekho, zaroorat par dono taraf scroll karo, sirf visible labels use karo, phir ek alag safe route try karo."
        Gap.STUCK -> if (en) "STUCK GAP: never repeat the same no-change action; use a different visible control, back/scroll, or reopen the app once, then verify." else "STUCK GAP: bina change wala action repeat mat karo; doosra visible control, back/scroll ya app ek baar reopen karo, phir verify karo."
        Gap.APP_MISSING -> if (en) "APP GAP: search installed apps / use the exact visible app name; do not install automatically. Ask the owner to install only if no safe route exists." else "APP GAP: installed apps/exact visible naam check karo; auto-install nahi. Safe route na ho tabhi owner ko install karne bolo."
        Gap.PERMISSION -> if (en) "PERMISSION GAP: continue all steps not needing that permission; explain the precise permission blocker and wait for owner action." else "PERMISSION GAP: bina us permission ke hone wale steps karo; exact permission blocker batao aur owner ke action ka intezar karo."
        Gap.TIMEOUT -> if (en) "TIME GAP: prioritize the highest-value remaining step, avoid repeated waits, verify what is done, and report unfinished items clearly." else "TIME GAP: sabse zaroori bacha step pehle karo, repeated wait mat karo, hua kaam verify karo aur adhure steps saaf batao."
        Gap.LOGIN -> if (en) "LOGIN GAP: a password / code / CAPTCHA wall needs the owner. Never type or guess it; finish every other step first, then tell the owner the exact thing needed, early." else "LOGIN GAP: password / code / CAPTCHA wall par owner chahiye. Kabhi type ya guess mat karo; baaki saare steps pehle karo, phir owner ko jaldi exact cheez batao."
        Gap.UNKNOWN -> if (en) "RECOVERY: do not give up at the first obstacle. Identify the missing prerequisite, try one materially different safe route, verify it, and report only the real blocker." else "RECOVERY: pehli rukawat par haar mat maano. Missing cheez pehchano, ek alag safe route try karo, verify karo aur sirf asli blocker batao."
    }

    /** Aggregate repeated failures without storing their raw text. */
    fun record(old: List<Lesson>, gap: Gap, nowMs: Long): List<Lesson> {
        val map = old.associateBy { it.gap }.toMutableMap()
        val previous = map[gap]
        map[gap] = Lesson(gap, ((previous?.count ?: 0) + 1).coerceAtMost(999), nowMs.coerceAtLeast(previous?.lastAtMs ?: 0L))
        return map.values.sortedWith(compareByDescending<Lesson> { it.lastAtMs }.thenBy { it.gap.key }).take(MAX_RECORDS)
    }

    fun promptGuidance(lessons: List<Lesson>, en: Boolean = false): String {
        if (lessons.isEmpty()) return ""
        val selected = lessons.sortedWith(compareByDescending<Lesson> { it.count }.thenByDescending { it.lastAtMs }).take(3)
        val prefix = if (en) "LESSONS FROM EARLIER FAILURES (guidance, not permission to bypass safety):\n" else "PURANE FAILURES SE SEEKHE HUE TAREEKE (sirf guidance, safety bypass nahi):\n"
        return (prefix + selected.joinToString("\n") { "- ${it.gap.key} (${it.count}x): ${guidance(it.gap, en)}" }).take(MAX_GUIDANCE_CHARS)
    }
}
