package com.nova.assistant

/**
 * v6.70 (Part 2): what a BACKUP model (free chain / offline model) must be told so it continues the same conversation and the
 * same task instead of answering a lone sentence. Pure rules (no Android): bounded recent turns, one safe line about the current
 * phone task, and a short "earlier" list for the small offline model. Nothing here contains keys; task text is redacted again.
 */
object FallbackContext {
    const val MAX_MSGS = 6
    const val MAX_MSG_CHARS = 300
    const val LOCAL_MSGS = 2
    const val LOCAL_CHARS = 120
    const val TASK_FRESH_MS = 30L * 60L * 1000L

    /** v6.71: the FULL current chat for the model. Budgets in characters (a model has a context limit, so "full" means "as much as fits, newest kept"). */
    const val FULL_FREE_CHARS = 12_000          // free providers (small context windows)
    const val FULL_GEMINI_CHARS = 120_000       // Gemini (very large context window)
    const val FULL_MSG_CHARS = 1_500

    class Turn(val role: String, val text: String)

    /** turns = what is sent; cut = true when older messages did not fit the budget (the model is told so). */
    class Window(val turns: List<Turn>, val cut: Boolean)

    /**
     * raw = the whole chat so far, oldest first, roles "me"/"user" and "ai"/"model"/"assistant". Every message is kept (each up to FULL_MSG_CHARS);
     * two in a row from the same side are joined, so no information is dropped to make the roles alternate. If the total is over [maxChars]
     * the OLDEST messages are left out and [Window.cut] is true. The window starts with the user and ENDS with the assistant, because the
     * caller adds the new question itself.
     */
    fun fullWindow(raw: List<Pair<String, String>>, maxChars: Int): Window {
        val joined = ArrayList<Turn>()
        for ((r, t) in raw) {
            val role = when (r) { "me", "user" -> "user"; "ai", "model", "assistant" -> "assistant"; else -> continue }
            val text = t.replace(Regex("[ \\t]+"), " ").trim().take(FULL_MSG_CHARS)
            if (text.isEmpty()) continue
            if (joined.isNotEmpty() && joined.last().role == role) {
                val last = joined.removeAt(joined.size - 1)
                joined.add(Turn(role, (last.text + "\n" + text).take(FULL_MSG_CHARS * 2)))
            } else joined.add(Turn(role, text))
        }
        while (joined.isNotEmpty() && joined.last().role == "user") joined.removeAt(joined.size - 1)
        var total = 0
        var start = joined.size
        while (start > 0) {
            val n = joined[start - 1].text.length
            if (total + n > maxChars && start < joined.size) break
            total += n
            start--
        }
        var part: List<Turn> = joined.subList(start, joined.size).toList()
        while (part.isNotEmpty() && part.first().role != "user") part = part.drop(1)
        return Window(part, part.size < joined.size)
    }

    /** A short system line when older messages had to be left out. */
    fun cutNote(en: Boolean): String =
        if (en) "Note: this chat is longer than fits here, so its oldest messages are left out."
        else "Note: ye chat itni lambi hai ki sabse purane messages yahan nahi aa paye."

    private fun clip(raw: String, max: Int): String = raw.replace(Regex("\\s+"), " ").trim().take(max)

    /**
     * raw = (role, text) as stored for Gemini ("user" / "model"). Result: roles "user" / "assistant", newest MAX_MSGS, strictly
     * alternating, starting with the user (some providers refuse anything else). Empty text is dropped.
     */
    fun turns(raw: List<Pair<String, String>>): List<Turn> {
        val out = ArrayList<Turn>()
        for ((r, t) in raw) {
            val role = when (r) { "user" -> "user"; "model", "assistant" -> "assistant"; else -> continue }
            val text = clip(t, MAX_MSG_CHARS)
            if (text.isEmpty()) continue
            if (out.isNotEmpty() && out.last().role == role) out.removeAt(out.size - 1)   // keep the newer of two in a row
            out.add(Turn(role, text))
        }
        var part: List<Turn> = if (out.size > MAX_MSGS) out.subList(out.size - MAX_MSGS, out.size).toList() else out
        while (part.isNotEmpty() && part.first().role != "user") part = part.drop(1)
        return part
    }

    private fun stateLabel(state: String, en: Boolean): String = when (state) {
        "CREATED", "PLANNING" -> "planning"
        "RUNNING", "RETRYING" -> if (en) "running" else "chal raha hai"
        "VERIFYING" -> if (en) "being verified" else "verify ho raha hai"
        "WAITING_FOR_PERMISSION" -> if (en) "waiting for a permission" else "permission ka intezar"
        "WAITING_FOR_CONFIRMATION" -> if (en) "waiting for the owner's yes" else "aapki confirmation ka intezar"
        "SUCCEEDED" -> if (en) "finished and verified" else "ho gaya aur verify hua"
        "REPORTED_COMPLETE" -> if (en) "finished but NOT verified" else "ho gaya par verify nahi hua"
        "NEEDS_OWNER" -> if (en) "needs the owner" else "aapki madad chahiye"
        "FAILED" -> if (en) "failed" else "fail hua"
        "CANCELLED" -> if (en) "stopped by the owner" else "aapne rok diya"
        "INTERRUPTED" -> if (en) "interrupted" else "beech me ruk gaya"
        else -> state.lowercase()
    }

    /** One line about the latest phone task, or "" when there is none or it is older than TASK_FRESH_MS. Redacted again, never a prompt. */
    fun taskLine(summary: String, state: String, message: String, ageMs: Long, en: Boolean): String {
        val s = AgentTaskRules.safeSummary(summary).take(160)
        if (s.isBlank() || ageMs < 0L || ageMs > TASK_FRESH_MS) return ""
        val m = AgentTaskRules.safeSummary(message).take(100)
        val tail = if (m.isBlank()) "" else " Note: $m"
        return (if (en) "Current phone task: $s (status: ${stateLabel(state, true)}).$tail Use it only if the user asks about it."
        else "Abhi ka phone task: $s (status: ${stateLabel(state, false)}).$tail Sirf tab use karo jab user is bare me pooche.").trim()
    }

    fun systemWith(base: String, taskLine: String): String = if (taskLine.isBlank()) base else base + "\n" + taskLine

    /** The offline model is small: only the last LOCAL_MSGS turns, each cut short, as "user: ..." / "nova: ...". */
    fun localEarlier(turns: List<Turn>): List<String> =
        turns.takeLast(LOCAL_MSGS).map { (if (it.role == "user") "user: " else "nova: ") + clip(it.text, LOCAL_CHARS) }
}
