package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** v7.105 PART 2: private on-device playbook. Stores only a task KIND, a short app label and counters; never task text, screen text, keys or messages. */
class PlaybookStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nova_playbook_v1", Context.MODE_PRIVATE)

    @Synchronized fun record(kind: PlaybookRules.Kind, apps: List<String>, success: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val updated = PlaybookRules.record(read(), kind, apps, success, nowMs)
        write(updated)
    }

    @Synchronized fun hintFor(kind: PlaybookRules.Kind, en: Boolean = false): String = PlaybookRules.hint(read(), kind, en)

    /** v7.106 PART 3: read-only copy of the sanitised entries for the alternatives engine. */
    @Synchronized fun entries(): List<PlaybookRules.Entry> = read()

    @Synchronized fun summary(en: Boolean = false): String = PlaybookRules.summary(read(), en)

    @Synchronized fun clear() { prefs.edit().remove(KEY).apply() }

    private fun write(entries: List<PlaybookRules.Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            val routes = JSONArray()
            e.routes.forEach { r -> routes.put(JSONObject().put("app", r.app).put("ok", r.ok).put("fail", r.fail).put("last", r.lastAtMs)) }
            arr.put(JSONObject().put("kind", e.kind.key).put("routes", routes))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun read(): List<PlaybookRules.Entry> = try {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        val out = ArrayList<PlaybookRules.Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val kind = PlaybookRules.Kind.values().firstOrNull { it.key == o.optString("kind") } ?: continue
            val ra = o.optJSONArray("routes") ?: continue
            val routes = ArrayList<PlaybookRules.Route>()
            for (k in 0 until ra.length()) {
                val r = ra.optJSONObject(k) ?: continue
                val app = PlaybookRules.cleanApp(r.optString("app"))
                if (app.isEmpty()) continue
                routes.add(PlaybookRules.Route(app, r.optInt("ok", 0).coerceIn(0, 999), r.optInt("fail", 0).coerceIn(0, 999), r.optLong("last", 0L).coerceAtLeast(0L)))
            }
            if (routes.isNotEmpty()) out.add(PlaybookRules.Entry(kind, routes.take(PlaybookRules.MAX_ROUTES_PER_KIND)))
        }
        out
    } catch (_: Exception) { emptyList() }

    companion object { private const val KEY = "entries" }
}
