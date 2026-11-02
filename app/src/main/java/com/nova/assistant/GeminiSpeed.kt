package com.nova.assistant

import org.json.JSONObject

/**
 * v7.3 faster "thinking": Gemini 2.5 Flash accepts thinkingBudget = 0 (answer at once, no hidden reasoning pass).
 * Other models answer HTTP 400 to that field, so it is added ONLY when the model name has "2.5" and "flash" (and not "pro"),
 * and the caller retries once without it if a 400 still comes back.
 */
object GeminiSpeed {
    fun supports(model: String): Boolean {
        val m = model.lowercase()
        return m.contains("2.5") && m.contains("flash") && !m.contains("pro")
    }

    /** v7.75: Gemini 3.x Flash (not Lite, not Pro) thinks at "medium" by default = slow. thinkingLevel "minimal" answers fast. Lite already defaults to minimal. */
    fun supportsLevel(model: String): Boolean {
        val m = model.lowercase()
        return m.contains("gemini-3") && m.contains("flash") && !m.contains("lite") && !m.contains("pro")
    }

    /** Returns [json] with generationConfig.thinkingConfig.thinkingBudget = 0 for a supporting model; anything else (or any parse problem) returns [json] unchanged. */
    fun withFastThinking(model: String, json: String): String {
        val budget = supports(model)
        if (!budget && !supportsLevel(model)) return json
        return try {
            val b = JSONObject(json)
            val gc = b.optJSONObject("generationConfig") ?: JSONObject()
            if (gc.has("thinkingConfig")) return json
            gc.put("thinkingConfig", if (budget) JSONObject().put("thinkingBudget", 0) else JSONObject().put("thinkingLevel", "minimal"))
            b.put("generationConfig", gc)
            b.toString()
        } catch (e: Exception) { json }
    }
}
