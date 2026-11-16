package com.nova.assistant

import android.content.Context

/** v7.191 L3: remembers the last job the Kaam-Manager started so the launcher home can show "Aakhri kaam". */
internal object TaskManagerStore {
    private const val PREF = "launcher"
    fun note(ctx: Context, name: String) {
        try { ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("last_task", name).putLong("last_task_at", System.currentTimeMillis()).apply() } catch (e: Throwable) { }
    }
    fun last(ctx: Context): String = try {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val t = p.getString("last_task", "") ?: ""
        if (t.isBlank() || System.currentTimeMillis() - p.getLong("last_task_at", 0L) > 24L * 3600_000L) "" else t
    } catch (e: Throwable) { "" }

    /** v7.199 CHAIN: steps left after a long (background) step. Expire after 24 hours. */
    fun savePending(ctx: Context, steps: List<TaskManagerRules.Plan>) {
        try {
            val e = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            if (steps.isEmpty()) e.remove("pending_steps").remove("pending_at") else e.putString("pending_steps", TaskManagerRules.encodeSteps(steps)).putLong("pending_at", System.currentTimeMillis())
            e.apply()
        } catch (e: Throwable) { }
    }
    fun pending(ctx: Context): List<TaskManagerRules.Plan> = try {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong("pending_at", 0L) > 24L * 3600_000L) emptyList()
        else TaskManagerRules.decodeSteps(p.getString("pending_steps", ""))
    } catch (e: Throwable) { emptyList() }
}
