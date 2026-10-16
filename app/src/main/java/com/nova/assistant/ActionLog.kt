package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * v6.53: history of what NOVA did by itself (auto replies and the reasons it did not reply), plus the auto-reply settings.
 * Stored only on this phone (private SharedPreferences), newest 300 entries, owner can clear it. Code / password messages are never stored.
 */
object ActionLog {
    private const val PREFS = "nova_action_log"
    private const val KEY_LOG = "log"
    private const val KEY_SEEN = "seen_ts"
    private const val KEY_AR_ON = "auto_reply_on"
    private const val KEY_AR_TEXT = "auto_reply_text"
    private const val MAX = 300

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoReplyOn(ctx: Context): Boolean = p(ctx).getBoolean(KEY_AR_ON, true)

    fun autoReplyText(ctx: Context): String = AutoReplyRules.cleanText(p(ctx).getString(KEY_AR_TEXT, "") ?: "")

    fun setAutoReply(ctx: Context, on: Boolean, text: String) {
        p(ctx).edit().putBoolean(KEY_AR_ON, on).putString(KEY_AR_TEXT, AutoReplyRules.cleanText(text)).apply()
    }

    /** status: "sent", "failed" or "skipped". src: "AI" or "fixed" for a sent reply ("" = not a reply). */
    @Synchronized fun add(ctx: Context, app: String, sender: String, incoming: String, status: String, detail: String, pkg: String = "", src: String = "") {
        try {
            val sp = p(ctx)
            val old = try { JSONArray(sp.getString(KEY_LOG, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
            old.put(JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("app", app)
                .put("who", sender.take(40))
                .put("in", incoming.take(80))
                .put("st", status)
                .put("pk", pkg)
                .put("src", src.take(8))
                .put("d", detail.take(MAX)))
            val out = JSONArray()
            for (i in maxOf(0, old.length() - MAX) until old.length()) out.put(old.get(i))
            sp.edit().putString(KEY_LOG, out.toString()).apply()
        } catch (e: Exception) {
            // history is a convenience: never let it break message handling
        }
    }

    /** JSON array, newest first. */
    @Synchronized fun listJson(ctx: Context): String = try {
        val arr = JSONArray(p(ctx).getString(KEY_LOG, "[]") ?: "[]")
        val out = JSONArray()
        for (i in arr.length() - 1 downTo 0) out.put(arr.get(i))
        out.toString()
    } catch (e: Exception) { "[]" }

    /** Entries newer than the last time the owner opened the history. */
    @Synchronized fun unseen(ctx: Context): Int = try {
        val seen = p(ctx).getLong(KEY_SEEN, 0L)
        val arr = JSONArray(p(ctx).getString(KEY_LOG, "[]") ?: "[]")
        var n = 0
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optLong("ts", 0L) > seen) n++
        n
    } catch (e: Exception) { 0 }

    /** The entry with this timestamp (a sent reply), or null. */
    @Synchronized fun find(ctx: Context, ts: Long): JSONObject? = try {
        val arr = JSONArray(p(ctx).getString(KEY_LOG, "[]") ?: "[]")
        var r: JSONObject? = null
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optLong("ts", -1L) == ts) r = arr.getJSONObject(i)
        r
    } catch (e: Exception) { null }

    /** Changes the status of one entry (sent -> deleted / delete_failed) and keeps the reason in "x". */
    @Synchronized fun setStatus(ctx: Context, ts: Long, status: String, note: String) {
        try {
            val arr = JSONArray(p(ctx).getString(KEY_LOG, "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optLong("ts", -1L) == ts) { o.put("st", status); o.put("x", note.take(MAX)) }
            }
            p(ctx).edit().putString(KEY_LOG, arr.toString()).apply()
        } catch (e: Exception) {
            // history is a convenience
        }
    }

    /** The newest sent reply that is still there (for the voice command "reply delete karo"). */
    @Synchronized fun lastSentTs(ctx: Context): Long = try {
        val arr = JSONArray(p(ctx).getString(KEY_LOG, "[]") ?: "[]")
        var r = -1L
        for (i in arr.length() - 1 downTo 0) { val o = arr.getJSONObject(i); if (o.optString("st") == "sent") { r = o.optLong("ts", -1L); break } }
        r
    } catch (e: Exception) { -1L }

    fun markSeen(ctx: Context) { p(ctx).edit().putLong(KEY_SEEN, System.currentTimeMillis()).apply() }

    @Synchronized fun clear(ctx: Context) {
        p(ctx).edit().remove(KEY_LOG).putLong(KEY_SEEN, System.currentTimeMillis()).apply()
    }
}
