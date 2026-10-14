package com.nova.assistant

import android.content.Context

/**
 * v6.97: REAL counters of questions NOVA answered without a cloud AI (key-less browsers / on-device AI), per day.
 * Shown on the LIMITS screen so the owner sees how much API load was saved. Counted only when an answer was really returned.
 */
object SavedStats {
    private const val PREF = "nova_saved"

    fun add(ctx: Context, kind: String) {
        try {
            val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val day = java.time.LocalDate.now().toString()
            val ed = sp.edit()
            if (sp.getString("day", "") != day) { ed.putString("day", day).putInt("web_d", 0).putInt("local_d", 0) }
            val dk = if (kind == "web") "web_d" else "local_d"
            val tk = if (kind == "web") "web_t" else "local_t"
            val base = if (sp.getString("day", "") != day) 0 else sp.getInt(dk, 0)
            ed.putInt(dk, base + 1).putInt(tk, sp.getInt(tk, 0) + 1).apply()
        } catch (e: Exception) { ErrLog.add("SavedStats", e.toString()) }
    }

    fun json(ctx: Context): String = try {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val today = sp.getString("day", "") == java.time.LocalDate.now().toString()
        "{\"webToday\":${if (today) sp.getInt("web_d", 0) else 0},\"localToday\":${if (today) sp.getInt("local_d", 0) else 0},\"webTotal\":${sp.getInt("web_t", 0)},\"localTotal\":${sp.getInt("local_t", 0)}}"
    } catch (e: Exception) { "{}" }
}
