package com.nova.assistant

/** Pure voice-command rules for the persistent cloud-job queue. Screen/UI agent tasks are never queueable. */
object OfflineQueueRules {
    enum class Kind { WEBSITE, LEARN, IMAGE, LEAD_DRAFTS }
    const val MAX_PENDING = 20
    const val MAX_ATTEMPTS = 3
    const val MAX_PAYLOAD_CHARS = 1200

    fun command(raw: String): Logic.Cmd? {
        val n = Logic.norm(raw).removePrefix("nova ").trim()
        if (n in setOf("queue dikhao", "queue list", "show queue", "meri queue", "queue status")) return Logic.Cmd("queue_list")
        if (n in setOf("queue saaf karo", "queue clear", "clear queue", "queue hatao")) return Logic.Cmd("queue_clear")
        return null
    }

    fun mayQueue(kind: Kind, containsScreenInstructions: Boolean = false): Boolean =
        !containsScreenInstructions && kind in setOf(Kind.WEBSITE, Kind.LEARN, Kind.IMAGE, Kind.LEAD_DRAFTS)

    fun cleanPayload(raw: String): String = raw.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
        .trim().take(MAX_PAYLOAD_CHARS)

    /** Results from an AI model are reports, never proof of a UI action. */
    fun statusForModelCompletion(): AgentTaskRules.State = AgentTaskRules.State.REPORTED_COMPLETE
}
