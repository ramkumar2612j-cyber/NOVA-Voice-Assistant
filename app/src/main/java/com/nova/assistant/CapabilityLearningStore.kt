package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Private on-device learning store: coarse failure categories only; never saves raw screen text, audio, prompts or keys. */
class CapabilityLearningStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nova_capability_learning_v1", Context.MODE_PRIVATE)

    @Synchronized fun recordFailure(reason: String, nowMs: Long = System.currentTimeMillis()) {
        val gap = CapabilityLearningRules.classify(reason)
        val current = read().toMutableList()
        val updated = CapabilityLearningRules.record(current, gap, nowMs)
        // Store a coarse category only; task and reason are deliberately not persisted.
        val arr = JSONArray()
        updated.forEach { arr.put(JSONObject().put("gap", it.gap.key).put("count", it.count).put("last", it.lastAtMs)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun guidanceFor(en: Boolean = false): String {
        // No task text is used as a key: the bounded general lessons are safe to reuse across tasks.
        return CapabilityLearningRules.promptGuidance(read(), en)
    }

    @Synchronized fun summary(en: Boolean = false): String {
        val lessons = read()
        if (lessons.isEmpty()) return if (en) "No saved capability-gap lessons yet." else "Abhi capability-gap ki koi seekh save nahi hai."
        return CapabilityLearningRules.promptGuidance(lessons, en).take(700)
    }

    /** v7.110 PART 7: the CLEAR button in Settings (LearnedMemory.clearAll) empties these lessons too. */
    @Synchronized fun clear() { prefs.edit().remove(KEY).apply() }

    private fun read(): List<CapabilityLearningRules.Lesson> = try {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        val out = ArrayList<CapabilityLearningRules.Lesson>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val gap = CapabilityLearningRules.Gap.values().firstOrNull { it.key == o.optString("gap") } ?: continue
            out.add(CapabilityLearningRules.Lesson(gap, o.optInt("count", 1).coerceIn(1, 999), o.optLong("last", 0L).coerceAtLeast(0L)))
        }
        out.take(CapabilityLearningRules.MAX_RECORDS)
    } catch (_: Exception) { emptyList() }

    companion object { private const val KEY = "lessons" }
}
