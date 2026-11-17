package com.nova.assistant

/**
 * v7.218 DEVICE SYNC rules (Part 2). Pure Kotlin — no Android, no network. Phone (SyncRulesTest) aur
 * desktop (desktop/test/sync.test.js) EXACT same fixtures chalate hain: dono taraf same merge result.
 *
 * Conflict policy (jarvis brain + link dono ka base): LWW by (ver, ts, deviceId) — JarvisCloud.Rules.lwwWinner.
 * Tasks: union by id, har id ka winner version se. Facts: same + private fact sirf usi device ka merge hota hai.
 * Memory text -> fact id: sha-256(text) ke pehle 20 hex — same text = same id = natural dedupe.
 */
object SyncRules {
    const val MAX_TASKS = 100
    const val MAX_FACTS = 200
    const val MAX_TEXT = 400

    data class TaskItem(val id: String, val title: String, val target: String = "", val state: String = "open",
                        val claimedBy: String = "", val ver: Int = 1, val ts: Long = 0, val device: String = "")

    data class FactItem(val id: String, val text: String, val scope: String = "all",
                        val ver: Int = 1, val ts: Long = 0, val device: String = "")

    /** Do task lists ka merge (cloud pull + local). Same id -> LWW winner; naya id -> add; NAYE ts wale bache, ts se sort, cap. */
    fun mergeTasks(mine: List<TaskItem>, theirs: List<TaskItem>): List<TaskItem> {
        val byId = LinkedHashMap<String, TaskItem>()
        for (t in theirs) byId[t.id] = t
        for (t in mine) {
            val old = byId[t.id]
            byId[t.id] = if (old == null) t
            else JarvisCloud.Rules.lwwWinner(t, t.ver, t.ts, t.device, old, old.ver, old.ts, old.device)
        }
        return byId.values.sortedByDescending { it.ts }.take(MAX_TASKS).sortedBy { it.ts }
    }

    /**
     * Memory facts merge. "mine" = is device ke facts. Private rule: sirf private fact APNE device ka
     * merge hota hai (privacy control — doosre device ka private kabhi nahi aata, aane wala list hi saaf hota hai).
     */
    fun mergeFacts(mine: List<FactItem>, theirs: List<FactItem>): List<FactItem> {
        val byId = LinkedHashMap<String, FactItem>()
        for (f in theirs) if (f.scope != "private") byId[f.id] = f
        for (f in mine) {
            val old = byId[f.id]
            byId[f.id] = if (old == null) f
            else JarvisCloud.Rules.lwwWinner(f, f.ver, f.ts, f.device, old, old.ver, old.ts, old.device)
        }
        return byId.values.sortedByDescending { it.ts }.take(MAX_FACTS).sortedBy { it.ts }
    }

    /** Local memory strings -> cloud fact items (stable id, saaf text). */
    fun memoryToFacts(list: List<String>, now: Long, device: String): List<FactItem> =
        list.filter { it.isNotBlank() }.take(MAX_FACTS).map { t ->
            FactItem(idOf(t), t.replace(Regex("[\\r\\n\\u0000-\\u001f]"), " ").trim().take(MAX_TEXT), "all", 1, now, device)
        }

    /** Cloud facts + existing local memory -> naya local memory list (same text dobara nahi aata; cap purana rule). */
    fun factsToMemory(facts: List<FactItem>, existing: List<String>): List<String> {
        val out = ArrayList(existing)
        for (f in facts) {
            if (f.scope == "private") continue
            val t = f.text.trim()
            if (t.isNotEmpty() && out.none { it.trim() == t }) out.add(t)
        }
        return out.take(MAX_FACTS)
    }

    /** Stable fact id: sha-256(text) pehle 20 hex chars. */
    fun idOf(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray())
            .joinToString("") { "%02x".format(it) }.take(20)

    /** Cloud task (phone ke liye aaya) -> schedule spec ban sakta hai? Sirf reminder jaisa title. Whitespace ek jaisa. */
    fun taskToReminderTitle(t: TaskItem): String = t.title.replace(Regex("\\s+"), " ").trim().take(120)
}
