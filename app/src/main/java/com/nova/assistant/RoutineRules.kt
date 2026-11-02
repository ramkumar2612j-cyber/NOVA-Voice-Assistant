package com.nova.assistant

/** Part 10: routines use the same safe Brain.SAFE_KINDS allow-list and cannot bypass confirmations. */
object RoutineRules {
    const val MAX_STEPS = 8
    data class Routine(val name: String, val trigger: String, val steps: List<String>)

    fun parse(name: String, trigger: String, stepsRaw: String): Routine? {
        val steps = stepsRaw.split(Regex("[\\n;]+" )).map { it.trim() }.filter { it.isNotEmpty() }
        if (name.trim().isEmpty() || trigger.trim().isEmpty()) return null
        val s = Skill(name.trim(), listOf(trigger.trim()), steps, "user")
        if (Brain.validateSkill(s) != null || steps.size > MAX_STEPS) return null
        return Routine(s.name, s.triggers.first(), s.steps)
    }
}
