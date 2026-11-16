package com.nova.assistant

import android.content.Context

/** v7.200: what JARVIS built or changed today (filled by each build step). Keeps the last 24 hours only. */
internal object DailyChangeLog {
    private const val PREF = "daily_check"
    fun add(ctx: Context, line: String) {
        try {
            val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val kept = today(ctx).toMutableList()
            kept.add(0, line.trim().take(160))
            val raw = kept.take(20).joinToString("\n")
            p.edit().putString("lines", raw).putLong("at", now).apply()
        } catch (e: Throwable) { }
    }
    fun today(ctx: Context): List<String> = try {
        val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong("at", 0L) > 24L * 3600_000L) emptyList()
        else (p.getString("lines", "") ?: "").split("\n").filter { it.isNotBlank() }
    } catch (e: Throwable) { emptyList() }
}
