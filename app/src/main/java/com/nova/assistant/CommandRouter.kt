package com.nova.assistant

/**
 * Side-effect-free routing plan. Later stages must explicitly resolve contacts, validate capabilities,
 * clarify/confirm, execute once, and report the actual result. This foundation does not launch apps.
 */
object CommandRouter {
    data class RoutePlan(
        val intent: IntentParser.ParsedIntent,
        val needsContactResolution: Boolean,
        val needsAppResolution: Boolean,
        val needsCapabilityValidation: Boolean,
        val needsClarification: Boolean,
        val needsConfirmation: Boolean,
        val executionKey: String
    )

    fun plan(raw: String): RoutePlan? {
        val parsed = IntentParser.parse(raw) ?: return null
        val communication = parsed.action == IntentParser.Action.CALL || parsed.action == IntentParser.Action.MESSAGE
        val key = listOf(parsed.appId ?: "system", parsed.action.name, parsed.target.orEmpty(), parsed.message.orEmpty())
            .joinToString("|").lowercase()
        return RoutePlan(
            intent = parsed,
            needsContactResolution = communication,
            needsAppResolution = parsed.action == IntentParser.Action.OPEN || parsed.appId != null,
            needsCapabilityValidation = parsed.action != IntentParser.Action.UNKNOWN,
            needsClarification = parsed.kind == IntentParser.Kind.AMBIGUOUS || parsed.confidence == IntentParser.Confidence.LOW,
            needsConfirmation = communication,
            executionKey = key
        )
    }

    /** Deterministic duplicate guard primitive for callers that need an in-flight key. */
    fun claim(key: String, inFlight: MutableSet<String>): Boolean {
        if (key.isBlank() || key in inFlight) return false
        inFlight.add(key)
        return true
    }

    fun release(key: String, inFlight: MutableSet<String>) { inFlight.remove(key) }
}

/** Pure, unit-testable time-window duplicate guard (double tap on a popup, repeated voice transcript). */
object ActionDedupe {
    const val WINDOW_MS = 8000L

    /** True when [key] may run now; false when the same key already ran within [windowMs]. Old entries are dropped. */
    fun claim(key: String, now: Long, windowMs: Long = WINDOW_MS, seen: MutableMap<String, Long>): Boolean = synchronized(seen) {
        if (key.isBlank()) return false
        seen.entries.removeAll { now - it.value >= windowMs || it.value > now }
        if (seen.containsKey(key)) return false
        seen[key] = now
        true
    }
}
