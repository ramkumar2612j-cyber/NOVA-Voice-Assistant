package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * v7.109 PART 6: private on-device site / blocker learning. Stores only a clean host label the owner named (<= 24 chars, no path or query),
 * a coarse job KIND, and counters + a time. Never task text, URLs with path/query, screen text, typed text, messages, keys or credentials.
 */
class SiteLearningStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nova_site_learning_v1", Context.MODE_PRIVATE)

    @Synchronized fun record(hosts: List<String>, kind: PlaybookRules.Kind, outcome: SiteLearningRules.Outcome, blocker: SiteLearningRules.Blocker?, switches: Int, nowMs: Long = System.currentTimeMillis()) {
        write(SiteLearningRules.record(read(), hosts, kind, outcome, blocker, switches, nowMs))
    }

    @Synchronized fun hintFor(hosts: List<String>, kind: PlaybookRules.Kind, en: Boolean = false): String = SiteLearningRules.hint(read(), hosts, kind, en)

    @Synchronized fun summary(en: Boolean = false): String = SiteLearningRules.summary(read(), en)

    @Synchronized fun clear() { prefs.edit().remove(KEY).apply() }

    private fun write(notes: List<SiteLearningRules.Note>) {
        val arr = JSONArray()
        notes.forEach { n ->
            val b = JSONObject()
            n.blocked.forEach { (k, v) -> b.put(k.key, v) }
            arr.put(JSONObject().put("key", n.key).put("ok", n.ok).put("b", b).put("sw", n.switches).put("last", n.lastAtMs))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun read(): List<SiteLearningRules.Note> { return try {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        val out = ArrayList<SiteLearningRules.Note>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = SiteLearningRules.cleanKey(o.optString("key"))
            if (key.isEmpty()) continue
            val bo = o.optJSONObject("b")
            val blocked = HashMap<SiteLearningRules.Blocker, Int>()
            if (bo != null) {
                for (b in SiteLearningRules.Blocker.values()) {
                    val v = bo.optInt(b.key, 0).coerceIn(0, 999)
                    if (v > 0) blocked[b] = v
                }
            }
            out.add(SiteLearningRules.Note(key, o.optInt("ok", 0).coerceIn(0, 999), blocked, o.optInt("sw", 0).coerceIn(0, 999), o.optLong("last", 0L).coerceAtLeast(0L)))
        }
        out.take(SiteLearningRules.MAX_ENTRIES)
    } catch (_: Exception) { emptyList() } }

    companion object { private const val KEY = "notes" }
}
