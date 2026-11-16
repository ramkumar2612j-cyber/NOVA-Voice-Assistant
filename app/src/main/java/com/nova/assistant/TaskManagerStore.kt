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
}
