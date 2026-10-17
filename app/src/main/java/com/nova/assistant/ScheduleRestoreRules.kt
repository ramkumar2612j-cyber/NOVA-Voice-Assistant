package com.nova.assistant

/**
 * v7.12 AUTOPSY R4 (pure Kotlin, no Android classes): what to do with a saved scheduled job when alarms are re-armed
 * (after a reboot, an app update, a clock / zone change, or when the app is opened), and which system broadcast does what in BootReceiver.
 */
object ScheduleRestoreRules {
    enum class Plan { KEEP, MOVE_NEXT, FIRE_NOW, DROP_MISSED }

    /** Past-due by up to 10 minutes still fires right away (the phone was only just back on, or Android delayed the alarm); older one-shot jobs are reported as missed. */
    const val GRACE_MS = 600_000L

    fun planFor(nextAtMs: Long, repeating: Boolean, nowMs: Long): Plan = when {
        nextAtMs > nowMs -> Plan.KEEP
        repeating -> Plan.MOVE_NEXT
        nowMs - nextAtMs <= GRACE_MS -> Plan.FIRE_NOW
        else -> Plan.DROP_MISSED
    }

    class BootPlan(val resetActive: Boolean, val rearm: Boolean, val askToStart: Boolean)

    /** null = not an action this receiver handles. Only a real boot switches NOVA to OFF and asks for the start tap. */
    fun bootPlan(action: String?): BootPlan? = when (action) {
        "android.intent.action.BOOT_COMPLETED", "android.intent.action.QUICKBOOT_POWERON" -> BootPlan(true, true, true)
        "android.intent.action.MY_PACKAGE_REPLACED", "android.intent.action.TIME_SET", "android.intent.action.TIMEZONE_CHANGED",
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> BootPlan(false, true, false)
        else -> null
    }
}
