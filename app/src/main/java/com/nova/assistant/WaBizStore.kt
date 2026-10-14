package com.nova.assistant

import android.content.Context

/** Owner's own catalogue answers for business watch (rate, size, delivery ...). Stored privately on the phone, never uploaded. */
object WaBizStore {
    private const val PREFS = "nova_biz_catalogue"
    private const val KEY = "lines"
    private const val MAX_ENTRIES = 60

    fun load(ctx: Context) {
        val raw = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        val m = LinkedHashMap<String, String>()
        for (line in raw.split("\n")) {
            val i = line.indexOf('\u0001')
            if (i > 0) m[line.substring(0, i)] = line.substring(i + 1)
        }
        WaBiz.catalogue = m
    }

    /** Keyword and answer: one line, no OTP-looking text, bounded size. */
    fun put(ctx: Context, keyword: String, answer: String): Boolean {
        val k = keyword.lowercase().replace(Regex("[\\u0001\\n]"), " ").trim().take(30)
        val a = answer.replace(Regex("[\\u0001\\n]"), " ").trim().take(300)
        if (k.length < 2 || a.isBlank() || WaBiz.looksSensitive(a)) return false
        load(ctx)
        val m = LinkedHashMap(WaBiz.catalogue)
        if (m.size >= MAX_ENTRIES && k !in m) return false
        m[k] = a
        val out = m.entries.joinToString("\n") { it.key + "\u0001" + it.value }
        val ok = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, out).commit()
        if (ok) WaBiz.catalogue = m
        return ok
    }
}
