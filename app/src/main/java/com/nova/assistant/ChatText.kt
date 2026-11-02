package com.nova.assistant

/** Converts accidental backend JSON envelopes into normal user-facing text. Never exposes raw JSON. */
object ChatText {
    private val JSONISH = Regex("^\\s*[\\{\\[].*[\\}\\]]\\s*$", RegexOption.DOT_MATCHES_ALL)
    private val FIELD = Regex("\"(message|text|answer|reply|content|result)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", RegexOption.IGNORE_CASE)

    fun clean(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty() || !JSONISH.matches(s)) return raw
        val field = FIELD.find(s)?.groupValues?.get(2)
        if (!field.isNullOrBlank()) return unescape(field)
        val status = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)
        return when (status?.lowercase()) {
            "success", "ok" -> "Theek hai, kaam ho gaya."
            "error", "failed", "failure" -> "Kaam nahi ho paya."
            else -> "Jawab samajh nahi aaya."
        }
    }

    private fun unescape(s: String): String = s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").trim()
}
