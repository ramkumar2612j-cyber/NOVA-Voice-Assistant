package com.nova.assistant

/**
 * v6.95 ROZ KA SUDHAAR (daily self-improve, with approval). PURE Kotlin: no Android classes, no network.
 *
 * What it does: once a day, inside ONE 1-hour window, a NON-Gemini AI (Groq / Qwen / GLM / OpenRouter, the user's own free keys)
 * is asked for a few "nicknames": how the owner may SAY an installed app (Hinglish / Hindi / English) -> the real app name.
 * Every suggestion is only a PROPOSAL. Nothing changes until the owner presses HAAN. Every applied change can be undone (WAPAS).
 * It can only add teach-aliases (TeachStore). It never touches code, settings, keys, contacts or permissions.
 * Evidence level: written + unit tested; not compiled in the sandbox, not phone tested.
 */
object SelfImproveRules {
    /** The whole feature may work at most this long per day (the window length and the daily AI-time budget). */
    const val WINDOW_MS = 60L * 60L * 1000L
    const val DAILY_BUDGET_MS = 60L * 60L * 1000L
    const val DEFAULT_START_HOUR = 3
    const val MAX_PER_RUN = 5
    const val MAX_PENDING = 12
    const val MAX_ITEMS = 60
    const val MIN_GAP_MS = 20L * 60L * 1000L
    const val MAX_AUTO_RUNS_PER_DAY = 3
    const val MAX_MANUAL_RUNS_PER_DAY = 6
    const val MAX_APPS_IN_PROMPT = 60

    const val PENDING = "pending"
    const val APPLIED = "applied"
    const val REJECTED = "rejected"
    const val REVERTED = "reverted"

    class Proposal(val alias: String, val value: String, val why: String)

    /** hour (0-23) is inside the 1-hour window that starts at startHour. */
    fun inWindow(hour: Int, startHour: Int): Boolean = hour == startHour.coerceIn(0, 23)

    /** AI time left today. usedDay must equal today, otherwise the counter is from an older day and starts again. */
    fun remainingMs(usedMs: Long, usedDay: String, today: String): Long =
        if (usedDay != today) DAILY_BUDGET_MS else (DAILY_BUDGET_MS - usedMs).coerceIn(0L, DAILY_BUDGET_MS)

    /** Automatic run: switched on, inside the window, budget left, enough gap after the last run, not too many runs, not too many open proposals. */
    fun canAutoRun(on: Boolean, hour: Int, startHour: Int, remainingMs: Long, sinceLastRunMs: Long, runsToday: Int, pending: Int): Boolean =
        on && inWindow(hour, startHour) && remainingMs > 0L && sinceLastRunMs >= MIN_GAP_MS &&
            runsToday < MAX_AUTO_RUNS_PER_DAY && pending < MAX_PENDING

    /** Manual run (button): any time, but still inside the daily budget and run limit. */
    fun canManualRun(remainingMs: Long, runsToday: Int, pending: Int): Boolean =
        remainingMs > 0L && runsToday < MAX_MANUAL_RUNS_PER_DAY && pending < MAX_PENDING

    /** Only providers that need the owner's own key; never Gemini and never the anonymous one. */
    fun allowedProviders(all: List<FreeProvider>): List<FreeProvider> =
        all.filter { it.keyName != null && !it.id.contains("gemini", true) && !it.label.contains("gemini", true) }

    fun systemPrompt(): String =
        "You help a voice assistant called NOVA understand its owner better. The owner speaks Hinglish, Hindi and English mixed. " +
            "You get the names of apps installed on the phone and the nicknames that already exist. " +
            "Suggest at most $MAX_PER_RUN NEW nicknames: a short phrase the owner may really SAY for one installed app (a Hindi or Hinglish way to say it, written in English letters), " +
            "and the exact installed app name it stands for. Keep \"why\" under 6 words. Only suggest a nickname when you are quite sure; never invent apps that are not in the list; " +
            "no numbers, no links, no passwords, no commands. " +
            "Reply with JSON only, no other text: {\"items\":[{\"alias\":\"nickname\",\"value\":\"exact app name\",\"why\":\"short reason\"}]}. " +
            "If nothing is useful, reply {\"items\":[]}."

    fun userPrompt(appLabels: List<String>, existingAliases: List<String>): String {
        val apps = appLabels.map { it.trim() }.filter { it.isNotEmpty() && it.length <= 40 }.distinct().take(MAX_APPS_IN_PROMPT)
        val have = existingAliases.map { it.trim() }.filter { it.isNotEmpty() }.take(40)
        return "Installed apps: " + apps.joinToString("; ") + ". Existing nicknames: " + (if (have.isEmpty()) "none" else have.joinToString("; ")) + "."
    }

    private val SECRET = Regex("(?i)(password|passwd|pin\\b|otp\\b|token|api[ _-]?key|secret|AIza[0-9A-Za-z_-]{20,}|sk-[A-Za-z0-9]{20,})")

    /**
     * Reads the AI reply. Returns only proposals that are valid teach-aliases, whose value is really an installed app,
     * that are not already known, and not duplicated. Anything else is dropped silently. Never throws.
     */
    fun parse(reply: String, appLabels: List<String>, existingAliases: List<String>): List<Proposal> {
        val a = reply.indexOf('{')
        if (a < 0) return emptyList()
        val b = reply.lastIndexOf('}')
        // FreeProviders.clean cuts long replies (700 chars): if the JSON arrives cut, keep the complete items before the cut
        var root: Any? = if (b > a) MiniJson.parse(reply.substring(a, b + 1)) else null
        if (root == null) {
            val c = reply.lastIndexOf("},")
            if (c > a) root = MiniJson.parse(reply.substring(a, c + 1) + "]}")
        }
        if (root == null) return emptyList()
        val items = MiniJson.field(root, "items") as? List<*> ?: return emptyList()
        val labels = appLabels.map { Logic.norm(it) }
        val known = existingAliases.map { Logic.norm(it) }.toMutableSet()
        val out = ArrayList<Proposal>()
        for (item in items) {
            if (out.size >= MAX_PER_RUN) break
            val alias = (MiniJson.field(item, "alias") as? String)?.trim() ?: continue
            val value = (MiniJson.field(item, "value") as? String)?.trim() ?: continue
            val why = ((MiniJson.field(item, "why") as? String) ?: "").trim().take(100)
            if (!TeachRules.valid(alias, value)) continue
            if (SECRET.containsMatchIn(alias) || SECRET.containsMatchIn(value) || SECRET.containsMatchIn(why)) continue
            if (Logic.norm(value) !in labels) continue
            val n = Logic.norm(alias)
            if (n in known || n in labels) continue
            known.add(n)
            out.add(Proposal(alias, value, why))
        }
        return out
    }
}
