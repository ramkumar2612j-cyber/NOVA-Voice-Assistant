package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * v7.143 STEP 3: private on-device storage for [LearnedRouteRules]. See that file for exactly what is (and is never) stored.
 * Shown and cleared together with the other learned notes through LearnedMemory.
 */
class LearnedRouteStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nova_learned_routes_v1", Context.MODE_PRIVATE)

    @Synchronized fun find(task: String, nowMs: Long = System.currentTimeMillis()): LearnedRouteRules.Route? {
        val key = LearnedRouteRules.fingerprint(task)
        if (key.isEmpty()) return null
        return read().firstOrNull { it.key == key && LearnedRouteRules.usable(it, nowMs) }
    }

    @Synchronized fun learn(task: String, steps: List<LearnedRouteRules.Step>, nowMs: Long = System.currentTimeMillis()) {
        write(LearnedRouteRules.record(read(), LearnedRouteRules.fingerprint(task), steps, nowMs))
    }

    @Synchronized fun ok(task: String, nowMs: Long = System.currentTimeMillis()) {
        write(LearnedRouteRules.bumpOk(read(), LearnedRouteRules.fingerprint(task), nowMs))
    }

    @Synchronized fun fail(task: String, nowMs: Long = System.currentTimeMillis()) {
        write(LearnedRouteRules.markFail(read(), LearnedRouteRules.fingerprint(task), nowMs))
    }

    @Synchronized fun count(): Int = read().size

    @Synchronized fun summary(en: Boolean = false): String = LearnedRouteRules.summary(read(), en)

    @Synchronized fun clear() { prefs.edit().remove(KEY).apply() }

    private fun write(routes: List<LearnedRouteRules.Route>) {
        val arr = JSONArray()
        routes.forEach { r ->
            val s = JSONArray()
            r.steps.forEach { s.put(JSONObject().put("a", it.act).put("v", it.v)) }
            arr.put(JSONObject().put("k", r.key).put("s", s).put("ok", r.ok).put("f", r.fail).put("t", r.lastAtMs))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun read(): List<LearnedRouteRules.Route> { return try {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        val out = ArrayList<LearnedRouteRules.Route>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.optString("k")
            if (key.length != 16) continue
            val sa = o.optJSONArray("s") ?: continue
            val steps = ArrayList<LearnedRouteRules.Step>()
            for (j in 0 until sa.length()) {
                val so = sa.optJSONObject(j) ?: continue
                val act = so.optString("a")
                if (act !in setOf("open_app", "tap", "scroll", "back")) continue
                steps.add(LearnedRouteRules.Step(act, so.optString("v").take(30)))
            }
            if (steps.isEmpty()) continue
            out.add(LearnedRouteRules.Route(key, steps.take(LearnedRouteRules.MAX_STEPS), o.optInt("ok", 0).coerceIn(0, 999), o.optInt("f", 0).coerceIn(0, 99), o.optLong("t", 0L).coerceAtLeast(0L)))
        }
        out.take(LearnedRouteRules.MAX_ROUTES)
    } catch (_: Exception) { emptyList() } }

    companion object { private const val KEY = "routes" }
}
