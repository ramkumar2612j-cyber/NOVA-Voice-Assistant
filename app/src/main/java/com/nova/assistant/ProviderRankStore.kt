package com.nova.assistant

import android.content.Context

/** v6.37: keeps what NOVA learned about each AI provider on this phone (numbers only, no text, no keys). */
object ProviderRankStore {
    private const val PREF = "nova_rank"
    private const val KEY = "stats"
    private val lock = Any()

    fun load(ctx: Context): Map<String, ProviderRanker.Stat> = try {
        ProviderRanker.decode(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: "")
    } catch (e: Exception) { emptyMap() }

    fun record(ctx: Context, id: String, code: Int, latencyMs: Long) {
        val ok = ProviderRanker.outcomeOf(code) ?: return
        try {
            synchronized(lock) {
                val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                val m = LinkedHashMap(ProviderRanker.decode(sp.getString(KEY, "") ?: ""))
                m[id] = ProviderRanker.update(m[id], ok, latencyMs)
                sp.edit().putString(KEY, ProviderRanker.encode(m)).apply()
            }
        } catch (e: Exception) { ErrLog.add("ProviderRankStore", e.toString()) }
    }

    fun reset(ctx: Context) {
        try { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply() } catch (e: Exception) { ErrLog.add("ProviderRankStore", e.toString()) }
    }
}
